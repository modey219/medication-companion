/**
 * Optional Play Integrity verification: confirms that a request really comes
 * from the released app running on a genuine Android device.
 *
 * It is disabled by default (`PLAY_INTEGRITY_MODE=off`) so the backend can run
 * and be tested without Google credentials. Set it to `enforce` once the Google
 * Cloud project, the service account and the app-side integrity token are ready.
 *
 * The token is decoded with the Play Integrity API (`decodeIntegrityToken`) and
 * checked against the expected package name and, when provided, a request hash
 * bound to the install id, which blunts replay across installs.
 */

import type { IntegrityConfig } from "../config";

export interface IntegrityVerdict {
  ok: boolean;
  reason?: string;
}

export interface ServiceAccount {
  client_email: string;
  private_key: string;
  token_uri?: string;
}

interface IntegrityExpectation {
  packageName: string;
  expectedRequestHash?: string;
}

const PLAY_INTEGRITY_SCOPE = "https://www.googleapis.com/auth/playintegrity";
const DEFAULT_TOKEN_URI = "https://oauth2.googleapis.com/token";
const DEFAULT_ALLOWED_APP_VERDICT = "PLAY_RECOGNIZED";
const DEFAULT_REQUIRED_DEVICE_VERDICT = "MEETS_DEVICE_INTEGRITY";

const accessTokenCache = new Map<string, { token: string; expiresAt: number }>();

/** Test hook: clear the cached OAuth access tokens. */
export function resetIntegrityCaches(): void {
  accessTokenCache.clear();
}

export function isIntegrityEnforced(mode: string | undefined): boolean {
  return (mode ?? "off").toLowerCase() === "enforce";
}

export function parseServiceAccount(json: string): ServiceAccount {
  const parsed = JSON.parse(json) as Partial<ServiceAccount>;
  if (typeof parsed.client_email !== "string" || parsed.client_email.length === 0) {
    throw new Error("service account is missing client_email");
  }
  if (typeof parsed.private_key !== "string" || parsed.private_key.length === 0) {
    throw new Error("service account is missing private_key");
  }
  return {
    client_email: parsed.client_email,
    private_key: parsed.private_key,
    ...(typeof parsed.token_uri === "string" ? { token_uri: parsed.token_uri } : {}),
  };
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

/** SHA-256 (hex) of the install id, used as the Play Integrity request hash. */
export async function requestHashFor(installId: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(installId));
  return Array.from(new Uint8Array(digest))
    .map((byte) => byte.toString(16).padStart(2, "0"))
    .join("");
}

/** Pure evaluation of a decoded Play Integrity payload. */
export function evaluateIntegrityPayload(payload: unknown, expectation: IntegrityExpectation): IntegrityVerdict {
  if (!isRecord(payload)) {
    return { ok: false, reason: "empty_payload" };
  }
  const requestDetails = isRecord(payload.requestDetails) ? payload.requestDetails : {};
  if (requestDetails.requestPackageName !== expectation.packageName) {
    return { ok: false, reason: "package_mismatch" };
  }
  if (expectation.expectedRequestHash !== undefined && requestDetails.requestHash !== expectation.expectedRequestHash) {
    return { ok: false, reason: "request_hash_mismatch" };
  }
  const appIntegrity = isRecord(payload.appIntegrity) ? payload.appIntegrity : {};
  if (appIntegrity.appRecognitionVerdict !== DEFAULT_ALLOWED_APP_VERDICT) {
    return { ok: false, reason: "app_not_recognized" };
  }
  const deviceIntegrity = isRecord(payload.deviceIntegrity) ? payload.deviceIntegrity : {};
  const verdicts = Array.isArray(deviceIntegrity.deviceRecognitionVerdict)
    ? deviceIntegrity.deviceRecognitionVerdict
    : [];
  if (!verdicts.includes(DEFAULT_REQUIRED_DEVICE_VERDICT)) {
    return { ok: false, reason: "device_integrity" };
  }
  return { ok: true };
}

function base64UrlFromBytes(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) {
    binary += String.fromCharCode(byte);
  }
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

async function importPrivateKey(pem: string): Promise<CryptoKey> {
  const body = pem
    .replace("-----BEGIN PRIVATE KEY-----", "")
    .replace(/-----END PRIVATE KEY-----/, "")
    .replace(/\s+/g, "");
  const raw = Uint8Array.from(atob(body), (char) => char.charCodeAt(0));
  return crypto.subtle.importKey("pkcs8", raw, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
}

async function createSignedJwt(account: ServiceAccount, nowMs: number): Promise<string> {
  const tokenUri = account.token_uri ?? DEFAULT_TOKEN_URI;
  const issuedAt = Math.floor(nowMs / 1000);
  const header = base64UrlFromBytes(
    new TextEncoder().encode(JSON.stringify({ alg: "RS256", typ: "JWT" })),
  );
  const claims = base64UrlFromBytes(
    new TextEncoder().encode(
      JSON.stringify({
        iss: account.client_email,
        scope: PLAY_INTEGRITY_SCOPE,
        aud: tokenUri,
        iat: issuedAt,
        exp: issuedAt + 3600,
      }),
    ),
  );
  const key = await importPrivateKey(account.private_key);
  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(`${header}.${claims}`),
  );
  return `${header}.${claims}.${base64UrlFromBytes(new Uint8Array(signature))}`;
}

export async function getAccessToken(
  account: ServiceAccount,
  fetchImpl: typeof fetch,
  nowMs: number,
): Promise<string> {
  const cached = accessTokenCache.get(account.client_email);
  if (cached && cached.expiresAt > nowMs + 30_000) {
    return cached.token;
  }
  const tokenUri = account.token_uri ?? DEFAULT_TOKEN_URI;
  const assertion = await createSignedJwt(account, nowMs);
  const response = await fetchImpl(tokenUri, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }).toString(),
  });
  if (!response.ok) {
    throw new Error("play integrity: could not obtain an access token");
  }
  const payload = (await response.json()) as { access_token?: string; expires_in?: number };
  if (typeof payload.access_token !== "string" || payload.access_token.length === 0) {
    throw new Error("play integrity: empty access token");
  }
  accessTokenCache.set(account.client_email, {
    token: payload.access_token,
    expiresAt: nowMs + (payload.expires_in ?? 3600) * 1000,
  });
  return payload.access_token;
}

export interface VerifyIntegrityOptions {
  config: IntegrityConfig;
  installId: string;
  token: string;
  fetchImpl?: typeof fetch;
  nowMs?: number;
}

/** Verify a Play Integrity token. When the mode is `off` this always allows. */
export async function verifyIntegrityToken(options: VerifyIntegrityOptions): Promise<IntegrityVerdict> {
  const { config } = options;
  if (!isIntegrityEnforced(config.mode)) {
    return { ok: true };
  }
  if (options.token.trim().length === 0) {
    return { ok: false, reason: "missing_token" };
  }
  if (config.packageName.length === 0 || config.serviceAccountJson.length === 0) {
    return { ok: false, reason: "integrity_not_configured" };
  }

  const fetchImpl = options.fetchImpl ?? fetch;
  const nowMs = options.nowMs ?? Date.now();

  let account: ServiceAccount;
  try {
    account = parseServiceAccount(config.serviceAccountJson);
  } catch {
    return { ok: false, reason: "invalid_service_account" };
  }

  try {
    const accessToken = await getAccessToken(account, fetchImpl, nowMs);
    const response = await fetchImpl(
      `https://playintegrity.googleapis.com/v1/${config.packageName}:decodeIntegrityToken`,
      {
        method: "POST",
        headers: {
          "content-type": "application/json",
          authorization: `Bearer ${accessToken}`,
        },
        body: JSON.stringify({ integrityToken: options.token }),
      },
    );
    if (!response.ok) {
      return { ok: false, reason: "decode_failed" };
    }
    const payload = (await response.json()) as { tokenPayloadExternal?: unknown };
    return evaluateIntegrityPayload(payload.tokenPayloadExternal, {
      packageName: config.packageName,
      expectedRequestHash: await requestHashFor(options.installId),
    });
  } catch {
    return { ok: false, reason: "integrity_unavailable" };
  }
}
