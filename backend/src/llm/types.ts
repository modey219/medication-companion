import type { ChatMessage } from "../validation";

/** Which half of the review-then-answer flow a completion belongs to (T14). */
export type LlmPurpose = "review" | "answer";

/** A completion request handed to a provider adapter. */
export interface LlmCompleteInput {
  purpose: LlmPurpose;
  system: string;
  messages: ChatMessage[];
  model: string;
  maxOutputTokens: number;
  timeoutMs: number;
  /**
   * Optional provider hint (for example "minimal" or "low") that asks a
   * reasoning model to spend less time thinking. Unset means "send nothing".
   */
  reasoningEffort?: string;
}

/** Provider-agnostic LLM client. */
export interface LlmClient {
  complete(input: LlmCompleteInput): Promise<string>;
}

/** Error raised by a provider adapter. Codes are stable; messages are patient-safe. */
export class LlmError extends Error {
  readonly code: string;
  readonly status: number | undefined;
  /**
   * Diagnostic detail from the provider, for the server log only (T15 fix).
   * Already capped and redacted by the adapter; never returned to the client.
   */
  readonly detail: string | undefined;

  constructor(code: string, message: string, status?: number, detail?: string) {
    super(message);
    this.name = "LlmError";
    this.code = code;
    this.status = status;
    this.detail = detail;
  }
}
