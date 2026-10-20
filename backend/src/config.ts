import type { Env } from "./env";

/**
 * Limits chosen by the team for abuse and cost protection (see DECISIONS D-018).
 * 10 requests/minute and 50/day per install, with a global daily ceiling of
 * 2000 requests across every install.
 */
export const DEFAULT_LIMITS = {
  perMinute: 10,
  perDay: 50,
  globalPerDay: 2000,
} as const;

export const DEFAULT_MAX_REQUEST_BYTES = 64 * 1024;
export const DEFAULT_MAX_MESSAGE_CHARS = 2000;
export const DEFAULT_MAX_MESSAGES = 20;
export const DEFAULT_MAX_MEDICATION_CONTEXT_CHARS = 4000;
export const DEFAULT_MAX_CONTEXT_CHARS = 12_000;
export const DEFAULT_TIMEOUT_MS = 45_000;
export const DEFAULT_MAX_OUTPUT_TOKENS = 512;
export const DEFAULT_MAX_REVIEW_TOKENS = 400;

/**
 * Placeholder used when no emergency number is configured.
 * TODO(clinical-review, T9): replace with the real local emergency number.
 */
export const PLACEHOLDER_EMERGENCY_NUMBER = "112";

export type IntegrityMode = "off" | "enforce";

export interface IntegrityConfig {
  mode: IntegrityMode;
  projectNumber: string;
  packageName: string;
  serviceAccountJson: string;
}

export interface Config {
  provider: string;
  model: string;
  apiKey: string;
  baseUrl: string;
  maxOutputTokens: number;
  timeoutMs: number;
  maxRequestBytes: number;
  maxMessageChars: number;
  maxMessages: number;
  maxMedicationContextChars: number;
  maxContextChars: number;
  maxReviewTokens: number;
  reasoningEffort: string | undefined;
  limits: {
    perMinute: number;
    perDay: number;
    globalPerDay: number;
  };
  emergencyNumber: string;
  integrity: IntegrityConfig;
}

function readString(value: string | undefined, fallback: string): string {
  if (typeof value !== "string") {
    return fallback;
  }
  const trimmed = value.trim();
  return trimmed.length > 0 ? trimmed : fallback;
}

function readInt(value: string | undefined, fallback: number, minimum: number, maximum: number): number {
  const parsed = Number.parseInt(readString(value, String(fallback)), 10);
  if (!Number.isFinite(parsed)) {
    return fallback;
  }
  return Math.min(Math.max(parsed, minimum), maximum);
}

function readOptional(value: string | undefined): string | undefined {
  const trimmed = readString(value, "");
  return trimmed.length > 0 ? trimmed.toLowerCase() : undefined;
}

function readIntegrityMode(value: string | undefined): IntegrityMode {
  return readString(value, "off").toLowerCase() === "enforce" ? "enforce" : "off";
}

/** Parse the Worker environment into a typed configuration with safe defaults. */
export function loadConfig(env: Env): Config {
  return {
    provider: readString(env.LLM_PROVIDER, "mock").toLowerCase(),
    model: readString(env.LLM_MODEL, ""),
    apiKey: readString(env.LLM_API_KEY, ""),
    baseUrl: readString(env.LLM_BASE_URL, "https://api.openai.com/v1"),
    maxOutputTokens: readInt(env.LLM_MAX_OUTPUT_TOKENS, DEFAULT_MAX_OUTPUT_TOKENS, 16, 4096),
    timeoutMs: readInt(env.REQUEST_TIMEOUT_MS, DEFAULT_TIMEOUT_MS, 1000, 120_000),
    maxRequestBytes: readInt(env.MAX_REQUEST_BYTES, DEFAULT_MAX_REQUEST_BYTES, 1024, 1024 * 1024),
    maxMessageChars: readInt(env.MAX_MESSAGE_CHARS, DEFAULT_MAX_MESSAGE_CHARS, 1, 20_000),
    maxMessages: readInt(env.MAX_MESSAGES, DEFAULT_MAX_MESSAGES, 1, 100),
    maxMedicationContextChars: readInt(
      env.MAX_MEDICATION_CONTEXT_CHARS,
      DEFAULT_MAX_MEDICATION_CONTEXT_CHARS,
      0,
      40_000,
    ),
    maxContextChars: readInt(env.MAX_CONTEXT_CHARS, DEFAULT_MAX_CONTEXT_CHARS, 0, 40_000),
    maxReviewTokens: readInt(env.LLM_MAX_REVIEW_TOKENS, DEFAULT_MAX_REVIEW_TOKENS, 64, 2048),
    reasoningEffort: readOptional(env.LLM_REASONING_EFFORT),
    limits: {
      perMinute: readInt(env.RATE_LIMIT_PER_MINUTE, DEFAULT_LIMITS.perMinute, 1, 10_000),
      perDay: readInt(env.RATE_LIMIT_PER_DAY, DEFAULT_LIMITS.perDay, 1, 100_000),
      globalPerDay: readInt(env.RATE_LIMIT_GLOBAL_PER_DAY, DEFAULT_LIMITS.globalPerDay, 1, 10_000_000),
    },
    emergencyNumber: readString(env.EMERGENCY_NUMBER, PLACEHOLDER_EMERGENCY_NUMBER),
    integrity: {
      mode: readIntegrityMode(env.PLAY_INTEGRITY_MODE),
      projectNumber: readString(env.PLAY_INTEGRITY_PROJECT_NUMBER, ""),
      packageName: readString(env.PLAY_INTEGRITY_PACKAGE_NAME, ""),
      serviceAccountJson: readString(env.PLAY_INTEGRITY_SERVICE_ACCOUNT_JSON, ""),
    },
  };
}
