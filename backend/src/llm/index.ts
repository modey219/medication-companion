import type { Config } from "../config";
import { createMockClient } from "./mock";
import { createOpenAiCompatibleClient } from "./openaiCompatible";
import type { LlmClient } from "./types";

export interface CreateLlmClientOptions {
  fetchImpl?: typeof fetch;
}

/**
 * Build the provider adapter from configuration. Unknown providers fail closed
 * with a mock client only when explicitly configured, so a typo cannot silently
 * send requests somewhere unexpected.
 */
export function createLlmClient(config: Config, options: CreateLlmClientOptions = {}): LlmClient {
  switch (config.provider) {
    case "mock":
      return createMockClient();
    case "openai-compatible":
    case "openai":
      if (config.apiKey.length === 0) {
        throw new Error("LLM_API_KEY is not configured");
      }
      return createOpenAiCompatibleClient({
        apiKey: config.apiKey,
        baseUrl: config.baseUrl,
        ...(options.fetchImpl ? { fetchImpl: options.fetchImpl } : {}),
      });
    default:
      throw new Error(`Unsupported LLM_PROVIDER: ${config.provider}`);
  }
}
