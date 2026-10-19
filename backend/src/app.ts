import { loadConfig, type Config } from "./config";
import type { Env } from "./env";
import { errorResponse, HttpError, jsonResponse } from "./http";
import {
  checkRateLimits,
  createKvCounterStore,
  createMemoryCounterStore,
  type CounterStore,
} from "./limits/rateLimit";
import { createLlmClient } from "./llm";
import { LlmError, type LlmClient } from "./llm/types";
import { createConsoleLogger, pseudonymize, type Logger } from "./logging";
import { detectEmergency, emergencyReply } from "./safety/emergency";
import { buildSystemPrompt } from "./safety/systemPrompt";
import { verifyIntegrityToken } from "./security/integrity";
import { parseChatRequest, readJsonBody, requireInstallId } from "./validation";

export interface AppDeps {
  llm: (config: Config) => LlmClient;
  store: (env: Env) => CounterStore;
  logger: Logger;
  now: () => number;
  fetchImpl: typeof fetch;
}

const fallbackMemoryStore = createMemoryCounterStore();

function defaultDeps(): AppDeps {
  return {
    llm: (config: Config) => createLlmClient(config),
    store: (env: Env) => (env.RATE_LIMIT_KV ? createKvCounterStore(env.RATE_LIMIT_KV) : fallbackMemoryStore),
    logger: createConsoleLogger(),
    now: () => Date.now(),
    fetchImpl: fetch,
  };
}

async function handleChat(request: Request, env: Env, deps: AppDeps): Promise<Response> {
  const startedAt = deps.now();
  const config = loadConfig(env);

  const installId = requireInstallId(request);
  const installTag = await pseudonymize(installId);

  const body = await readJsonBody(request, config.maxRequestBytes);
  const chat = parseChatRequest(body, config);

  if (config.integrity.mode === "enforce") {
    const token = request.headers.get("x-integrity-token") ?? "";
    const verdict = await verifyIntegrityToken({
      config: config.integrity,
      installId,
      token,
      fetchImpl: deps.fetchImpl,
      nowMs: deps.now(),
    });
    if (!verdict.ok) {
      deps.logger.warn("chat.integrity_failed", { installTag, reason: verdict.reason });
      throw new HttpError(401, "integrity_failed", "This request could not be verified.");
    }
  }

  const decision = await checkRateLimits(deps.store(env), installId, config.limits, deps.now());
  if (!decision.allowed) {
    deps.logger.warn("chat.rate_limited", { installTag, scope: decision.scope });
    throw new HttpError(
      429,
      "rate_limited",
      "Too many requests. Please try again later.",
      { "retry-after": String(decision.retryAfterSeconds ?? 60) },
    );
  }

  const lastUserMessage = [...chat.messages].reverse().find((message) => message.role === "user");
  const emergencyKind = lastUserMessage ? detectEmergency(lastUserMessage.content) : undefined;
  if (emergencyKind) {
    deps.logger.info("chat.emergency", { installTag, kind: emergencyKind, durationMs: deps.now() - startedAt });
    return jsonResponse({
      reply: emergencyReply(config.emergencyNumber),
      emergency: true,
      kind: emergencyKind,
    });
  }

  const client = deps.llm(config);
  let reply: string;
  try {
    reply = await client.complete({
      system: buildSystemPrompt(chat.medicationContext),
      messages: chat.messages,
      model: config.model,
      maxOutputTokens: config.maxOutputTokens,
      timeoutMs: config.timeoutMs,
    });
  } catch (error) {
    if (error instanceof LlmError) {
      const status = error.code === "llm_timeout" ? 504 : 502;
      deps.logger.warn("chat.llm_failed", { installTag, code: error.code, status });
      throw new HttpError(status, error.code, error.message);
    }
    deps.logger.error("chat.unexpected", { installTag });
    throw error;
  }

  deps.logger.info("chat.ok", {
    installTag,
    provider: config.provider,
    model: config.model,
    messages: chat.messages.length,
    sharedContext: chat.medicationContext !== undefined,
    durationMs: deps.now() - startedAt,
  });
  return jsonResponse({ reply, emergency: false });
}

export function createApp(overrides: Partial<AppDeps> = {}): ExportedHandler<Env> {
  const deps: AppDeps = { ...defaultDeps(), ...overrides };

  return {
    async fetch(request: Request, env: Env): Promise<Response> {
      const url = new URL(request.url);
      try {
        if (url.pathname === "/health") {
          if (request.method !== "GET") {
            throw new HttpError(405, "method_not_allowed", "Method not allowed.");
          }
          return jsonResponse({ status: "ok" });
        }
        if (url.pathname === "/v1/chat") {
          if (request.method !== "POST") {
            throw new HttpError(405, "method_not_allowed", "Method not allowed.");
          }
          return await handleChat(request, env, deps);
        }
        throw new HttpError(404, "not_found", "Not found.");
      } catch (error) {
        const response = errorResponse(error);
        if (!(error instanceof HttpError)) {
          deps.logger.error("request.unhandled");
        }
        return response;
      }
    },
  };
}
