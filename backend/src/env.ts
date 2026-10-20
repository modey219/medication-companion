/**
 * Cloudflare Worker bindings and environment variables for the assistant proxy.
 *
 * Everything here is optional so the Worker and the tests can run with an empty
 * environment; `loadConfig` in config.ts applies the safe defaults. Only
 * `LLM_API_KEY`, and, when Play Integrity is enforced, the service account
 * JSON, are secrets and must never be committed.
 */
export interface Env {
  /** "openai-compatible" | "mock". */
  LLM_PROVIDER?: string;
  LLM_MODEL?: string;
  LLM_API_KEY?: string;
  LLM_BASE_URL?: string;
  LLM_MAX_OUTPUT_TOKENS?: string;
  LLM_MAX_REVIEW_TOKENS?: string;
  /** Optional "minimal" | "low" hint for reasoning models; unset sends nothing. */
  LLM_REASONING_EFFORT?: string;

  REQUEST_TIMEOUT_MS?: string;
  MAX_REQUEST_BYTES?: string;
  MAX_MESSAGE_CHARS?: string;
  MAX_MESSAGES?: string;
  MAX_MEDICATION_CONTEXT_CHARS?: string;
  MAX_CONTEXT_CHARS?: string;

  RATE_LIMIT_PER_MINUTE?: string;
  RATE_LIMIT_PER_DAY?: string;
  RATE_LIMIT_GLOBAL_PER_DAY?: string;

  EMERGENCY_NUMBER?: string;

  PLAY_INTEGRITY_MODE?: string;
  PLAY_INTEGRITY_PROJECT_NUMBER?: string;
  PLAY_INTEGRITY_PACKAGE_NAME?: string;
  PLAY_INTEGRITY_SERVICE_ACCOUNT_JSON?: string;

  /**
   * Optional KV namespace used for the rate-limit counters. When it is not
   * bound the Worker falls back to an in-memory counter, which is correct for
   * local development and tests but is not shared across isolates.
   */
  RATE_LIMIT_KV?: KVNamespace;
}
