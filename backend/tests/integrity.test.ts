import { afterEach, beforeEach, describe, expect, it } from "vitest";
import type { IntegrityConfig } from "../src/config";
import {
  evaluateIntegrityPayload,
  isIntegrityEnforced,
  parseServiceAccount,
  requestHashFor,
  resetIntegrityCaches,
  verifyIntegrityToken,
} from "../src/security/integrity";
import { INSTALL_ID } from "./helpers";

async function makeServiceAccountJson(): Promise<string> {
  const keyPair = (await crypto.subtle.generateKey(
    {
      name: "RSASSA-PKCS1-v1_5",
      modulusLength: 2048,
      publicExponent: new Uint8Array([1, 0, 1]),
      hash: "SHA-256",
    },
    true,
    ["sign", "verify"],
  )) as { privateKey: CryptoKey; publicKey: CryptoKey };
  const pkcs8 = (await crypto.subtle.exportKey("pkcs8", keyPair.privateKey)) as ArrayBuffer;
  const base64 = Buffer.from(new Uint8Array(pkcs8)).toString("base64");
  const lines = base64.match(/.{1,64}/g) ?? [base64];
  const pem = `-----BEGIN PRIVATE KEY-----\n${lines.join("\n")}\n-----END PRIVATE KEY-----\n`;
  return JSON.stringify({
    client_email: "assistant@example.iam.gserviceaccount.com",
    private_key: pem,
    token_uri: "https://oauth2.example.com/token",
  });
}

function goodPayload(requestHash: string): Record<string, unknown> {
  return {
    requestDetails: { requestPackageName: "com.medcompanion.app", requestHash },
    appIntegrity: { appRecognitionVerdict: "PLAY_RECOGNIZED" },
    deviceIntegrity: { deviceRecognitionVerdict: ["MEETS_DEVICE_INTEGRITY"] },
  };
}

function enforcingConfig(serviceAccountJson: string): IntegrityConfig {
  return {
    mode: "enforce",
    projectNumber: "1234567890",
    packageName: "com.medcompanion.app",
    serviceAccountJson,
  };
}

beforeEach(() => {
  resetIntegrityCaches();
});

afterEach(() => {
  resetIntegrityCaches();
});

describe("isIntegrityEnforced", () => {
  it("is off by default", () => {
    expect(isIntegrityEnforced(undefined)).toBe(false);
    expect(isIntegrityEnforced("off")).toBe(false);
  });

  it("is on only for enforce", () => {
    expect(isIntegrityEnforced("enforce")).toBe(true);
    expect(isIntegrityEnforced("ENFORCE")).toBe(true);
  });
});

describe("evaluateIntegrityPayload", () => {
  it("accepts a fully verified payload", () => {
    expect(
      evaluateIntegrityPayload(goodPayload("hash"), {
        packageName: "com.medcompanion.app",
        expectedRequestHash: "hash",
      }),
    ).toEqual({ ok: true });
  });

  it("rejects a payload for another package", () => {
    expect(
      evaluateIntegrityPayload(goodPayload("hash"), { packageName: "com.other.app" }),
    ).toMatchObject({ ok: false, reason: "package_mismatch" });
  });

  it("rejects a reused token bound to another install", () => {
    expect(
      evaluateIntegrityPayload(goodPayload("other-hash"), {
        packageName: "com.medcompanion.app",
        expectedRequestHash: "hash",
      }),
    ).toMatchObject({ ok: false, reason: "request_hash_mismatch" });
  });

  it("rejects an unrecognised app", () => {
    const payload = goodPayload("hash");
    (payload.appIntegrity as Record<string, unknown>).appRecognitionVerdict = "UNRECOGNIZED_VERSION";
    expect(evaluateIntegrityPayload(payload, { packageName: "com.medcompanion.app" })).toMatchObject({
      ok: false,
      reason: "app_not_recognized",
    });
  });

  it("rejects a device that does not meet integrity", () => {
    const payload = goodPayload("hash");
    (payload.deviceIntegrity as Record<string, unknown>).deviceRecognitionVerdict = ["MEETS_BASIC_INTEGRITY"];
    expect(evaluateIntegrityPayload(payload, { packageName: "com.medcompanion.app" })).toMatchObject({
      ok: false,
      reason: "device_integrity",
    });
  });

  it("rejects an empty payload", () => {
    expect(evaluateIntegrityPayload(null, { packageName: "com.medcompanion.app" })).toMatchObject({
      ok: false,
      reason: "empty_payload",
    });
  });
});

describe("parseServiceAccount", () => {
  it("rejects JSON without the required fields", () => {
    expect(() => parseServiceAccount("{}")).toThrow();
    expect(() => parseServiceAccount("not json")).toThrow();
  });
});

describe("verifyIntegrityToken", () => {
  it("allows every request when the mode is off", async () => {
    const verdict = await verifyIntegrityToken({
      config: { mode: "off", projectNumber: "", packageName: "", serviceAccountJson: "" },
      installId: INSTALL_ID,
      token: "",
    });
    expect(verdict.ok).toBe(true);
  });

  it("requires a token when enforcing", async () => {
    const verdict = await verifyIntegrityToken({
      config: enforcingConfig(await makeServiceAccountJson()),
      installId: INSTALL_ID,
      token: "",
    });
    expect(verdict).toMatchObject({ ok: false, reason: "missing_token" });
  });

  it("fails closed when the service account is not configured", async () => {
    const verdict = await verifyIntegrityToken({
      config: { mode: "enforce", projectNumber: "", packageName: "com.medcompanion.app", serviceAccountJson: "" },
      installId: INSTALL_ID,
      token: "token-abc",
    });
    expect(verdict).toMatchObject({ ok: false, reason: "integrity_not_configured" });
  });

  it("fails closed when the service account JSON is invalid", async () => {
    const verdict = await verifyIntegrityToken({
      config: enforcingConfig("{}"),
      installId: INSTALL_ID,
      token: "token-abc",
    });
    expect(verdict).toMatchObject({ ok: false, reason: "invalid_service_account" });
  });

  it("verifies a token end to end against the Google APIs", async () => {
    const serviceAccountJson = await makeServiceAccountJson();
    const expectedHash = await requestHashFor(INSTALL_ID);
    const urls: string[] = [];
    const fetchImpl = (async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = typeof input === "string" ? input : input instanceof URL ? input.toString() : input.url;
      urls.push(url);
      if (url.startsWith("https://oauth2.example.com")) {
        return new Response(JSON.stringify({ access_token: "ya29.test", expires_in: 3600 }), { status: 200 });
      }
      if (url.includes(":decodeIntegrityToken")) {
        const body = JSON.parse(String(init?.body ?? "{}")) as { integrityToken?: string };
        expect(body.integrityToken).toBe("token-abc");
        return new Response(JSON.stringify({ tokenPayloadExternal: goodPayload(expectedHash) }), { status: 200 });
      }
      return new Response("not found", { status: 404 });
    }) as unknown as typeof fetch;

    const verdict = await verifyIntegrityToken({
      config: enforcingConfig(serviceAccountJson),
      installId: INSTALL_ID,
      token: "token-abc",
      fetchImpl,
      nowMs: Date.UTC(2026, 0, 15, 12, 0, 0),
    });
    expect(verdict).toEqual({ ok: true });
    expect(urls).toEqual([
      "https://oauth2.example.com/token",
      "https://playintegrity.googleapis.com/v1/com.medcompanion.app:decodeIntegrityToken",
    ]);
  });

  it("fails closed when the decode call fails", async () => {
    const fetchImpl = (async (input: RequestInfo | URL) => {
      const url = typeof input === "string" ? input : input instanceof URL ? input.toString() : input.url;
      if (url.startsWith("https://oauth2.example.com")) {
        return new Response(JSON.stringify({ access_token: "ya29.test", expires_in: 3600 }), { status: 200 });
      }
      return new Response("boom", { status: 500 });
    }) as unknown as typeof fetch;
    const verdict = await verifyIntegrityToken({
      config: enforcingConfig(await makeServiceAccountJson()),
      installId: INSTALL_ID,
      token: "token-abc",
      fetchImpl,
    });
    expect(verdict).toMatchObject({ ok: false, reason: "decode_failed" });
  });

  it("fails closed when the network is unavailable", async () => {
    const fetchImpl = (async () => {
      throw new Error("network down");
    }) as unknown as typeof fetch;
    const verdict = await verifyIntegrityToken({
      config: enforcingConfig(await makeServiceAccountJson()),
      installId: INSTALL_ID,
      token: "token-abc",
      fetchImpl,
    });
    expect(verdict).toMatchObject({ ok: false, reason: "integrity_unavailable" });
  });
});
