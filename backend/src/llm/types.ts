import type { ChatMessage } from "../validation";

/** A completion request handed to a provider adapter. */
export interface LlmCompleteInput {
  system: string;
  messages: ChatMessage[];
  model: string;
  maxOutputTokens: number;
  timeoutMs: number;
}

/** Provider-agnostic LLM client. */
export interface LlmClient {
  complete(input: LlmCompleteInput): Promise<string>;
}

/** Error raised by a provider adapter. Codes are stable; messages are patient-safe. */
export class LlmError extends Error {
  readonly code: string;
  readonly status: number | undefined;

  constructor(code: string, message: string, status?: number) {
    super(message);
    this.name = "LlmError";
    this.code = code;
    this.status = status;
  }
}
