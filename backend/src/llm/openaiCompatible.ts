import { LlmError, type LlmClient, type LlmCompleteInput } from "./types";

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

/** Read `choices[0].message.content` from an OpenAI-compatible response body. */
function extractReply(payload: unknown): string | undefined {
  if (!isRecord(payload)) {
    return undefined;
  }
  const choices = payload.choices;
  if (!Array.isArray(choices) || choices.length === 0) {
    return undefined;
  }
  const first = choices[0];
  if (!isRecord(first)) {
    return undefined;
  }
  const message = first.message;
  if (!isRecord(message)) {
    return undefined;
  }
  const content = message.content;
  if (typeof content === "string" && content.trim().length > 0) {
    return content.trim();
  }
  return undefined;
}

export interface OpenAiCompatibleOptions {
  apiKey: string;
  baseUrl: string;
  fetchImpl?: typeof fetch;
}

/**
 * Adapter for any OpenAI-compatible `POST /chat/completions` endpoint
 * (OpenAI, Azure OpenAI gateways, and most third-party providers). The base URL,
 * model and key all come from the environment, so the Worker stays
 * provider-agnostic.
 */
export function createOpenAiCompatibleClient(options: OpenAiCompatibleOptions): LlmClient {
  const doFetch = options.fetchImpl ?? fetch;
  const baseUrl = options.baseUrl.replace(/\/+$/, "");

  return {
    async complete(input: LlmCompleteInput): Promise<string> {
      const controller = new AbortController();
      const timer = setTimeout(() => controller.abort(), input.timeoutMs);
      try {
        const response = await doFetch(`${baseUrl}/chat/completions`, {
          method: "POST",
          headers: {
            "content-type": "application/json",
            authorization: `Bearer ${options.apiKey}`,
          },
          body: JSON.stringify({
            model: input.model,
            messages: [{ role: "system", content: input.system }, ...input.messages],
            max_tokens: input.maxOutputTokens,
          }),
          signal: controller.signal,
        });

        if (!response.ok) {
          throw new LlmError(
            "llm_http_error",
            "The assistant is unavailable right now. Please try again.",
            response.status,
          );
        }

        const payload = (await response.json()) as unknown;
        const reply = extractReply(payload);
        if (!reply) {
          throw new LlmError("llm_empty_response", "The assistant returned an empty reply.");
        }
        return reply;
      } catch (error) {
        if (error instanceof LlmError) {
          throw error;
        }
        if (error instanceof Error && error.name === "AbortError") {
          throw new LlmError("llm_timeout", "The assistant took too long to reply.");
        }
        throw new LlmError("llm_unreachable", "The assistant is unavailable right now. Please try again.");
      } finally {
        clearTimeout(timer);
      }
    },
  };
}
