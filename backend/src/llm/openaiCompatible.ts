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

/** Longest provider detail kept for the log; never shown to the patient. */
const MAX_DETAIL_CHARS = 300;

/**
 * Drop anything that looks like a key so a diagnostic line cannot leak a secret.
 * OpenAI-style 401 bodies echo a masked key, and a proxy may echo the header.
 */
function redactSecrets(value: string): string {
  return value.replace(/sk-[A-Za-z0-9_*.-]{6,}/g, "sk-***").replace(/Bearer\s+[^\s",]+/gi, "Bearer ***");
}

/**
 * The provider's own explanation of a failed call, for the server log only
 * (T15 diagnostic fix). JSON bodies contribute only their `error.*` diagnostic
 * fields, so a body that echoes the request cannot carry patient text into the
 * log; anything else is reported by content type alone. When a body has none of
 * those fields the line says what shape it was (`empty-body`, the content type,
 * or the JSON key names) so a provider can be diagnosed without logging values.
 * The result is capped, single-line and secret-free, and is never returned to
 * the patient.
 */
async function readErrorDetail(response: Response): Promise<string | undefined> {
  try {
    const raw = await response.text();
    if (raw.trim().length === 0) {
      return "empty-body";
    }
    let parsed: unknown;
    try {
      parsed = JSON.parse(raw);
    } catch {
      const contentType = response.headers.get("content-type")?.split(";")[0]?.trim() ?? "unknown";
      return `non-json ${contentType}`.slice(0, MAX_DETAIL_CHARS);
    }
    if (typeof parsed !== "object" || parsed === null) {
      return `json-${parsed === null ? "null" : typeof parsed}`;
    }
    const root = (Array.isArray(parsed) ? parsed[0] : parsed) as Record<string, unknown> | undefined;
    if (typeof root !== "object" || root === null) {
      return "json-array-empty";
    }
    if (typeof root.error === "string" && root.error.trim().length > 0) {
      return redactSecrets(root.error.replace(/\s+/g, " ").trim()).slice(0, MAX_DETAIL_CHARS);
    }
    const error = typeof root.error === "object" && root.error !== null ? (root.error as Record<string, unknown>) : root;
    const parts = ["type", "code", "param", "message"]
      .map((key) => error[key])
      .filter((value): value is string => typeof value === "string" && value.trim().length > 0)
      .map((value) => value.replace(/\s+/g, " ").trim());
    if (parts.length === 0) {
      const keys = Object.keys(error).slice(0, 6).join(",");
      return `json-keys: ${keys.length > 0 ? keys : "none"}`.slice(0, MAX_DETAIL_CHARS);
    }
    return redactSecrets(parts.join(" | ")).slice(0, MAX_DETAIL_CHARS);
  } catch {
    return "unreadable-body";
  }
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
            await readErrorDetail(response),
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
