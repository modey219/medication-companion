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
import { guardAnswer } from "./safety/answerGuard";
import { detectEmergency, emergencyReply } from "./safety/emergency";
import { parseReview, ReviewError, toPublicReview, type Review } from "./safety/review";
import { safeBlockedReply, safeDeclineReply } from "./safety/safeReplies";
import { buildAnswerPrompt, buildReviewPrompt } from "./safety/systemPrompt";
import { verifyIntegrityToken } from "./security/integrity";
import { parseChatRequest, readJsonBody, requireInstallId, type ChatRequest } from "./validation";

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

function llmFailure(error: unknown, installTag: string, deps: AppDeps): never {
  if (error instanceof LlmError) {
    const status = error.code === "llm_timeout" ? 504 : 502;
    deps.logger.warn("chat.llm_failed", {
      installTag,
      code: error.code,
      status,
      upstream: error.status,
      providerDetail: error.detail,
    });
    throw new HttpError(status, error.code, error.message);
  }
  deps.logger.error("chat.unexpected", { installTag });
  throw error;
}

/**
 * The review pass (T14, D-024): the model inspects the request and the profile
 * before it is allowed to write anything for the patient. A review that cannot
 * be parsed fails closed: no answer is produced.
 */
async function review(
  client: LlmClient,
  chat: ChatRequest,
  config: Config,
  deps: AppDeps,
  installTag: string,
): Promise<Review> {
  try {
    const raw = await client.complete({
      purpose: "review",
      system: buildReviewPrompt(),
      messages: chat.messages,
      model: config.model,
      maxOutputTokens: config.maxReviewTokens,
      timeoutMs: config.timeoutMs,
    });
    return parseReview(raw);
  } catch (error) {
    if (error instanceof ReviewError) {
      deps.logger.warn("chat.invalid_review", { installTag });
      throw new HttpError(502, "llm_invalid_review", "The assistant could not check the request. Please try again.");
    }
    return llmFailure(error, installTag, deps);
  }
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
  const reviewed = await review(client, chat, config, deps, installTag);

  if (reviewed.urgency === "EMERGENCY") {
    deps.logger.info("chat.emergency", {
      installTag,
      kind: "review",
      urgency: reviewed.urgency,
      durationMs: deps.now() - startedAt,
    });
    return jsonResponse({
      reply: emergencyReply(config.emergencyNumber),
      emergency: true,
      kind: "review",
      urgency: reviewed.urgency,
      review: toPublicReview(reviewed),
    });
  }

  if (reviewed.forbiddenRequest) {
    deps.logger.info("chat.declined", {
      installTag,
      urgency: reviewed.urgency,
      durationMs: deps.now() - startedAt,
    });
    return jsonResponse({
      reply: safeDeclineReply(),
      emergency: false,
      urgency: "SEE_DOCTOR_SOON",
      review: toPublicReview(reviewed),
    });
  }

  let reply: string;
  try {
    reply = await client.complete({
      purpose: "answer",
      system: buildAnswerPrompt({
        ...(chat.medicationContext !== undefined ? { medicationContext: chat.medicationContext } : {}),
        ...(chat.context !== undefined ? { context: chat.context } : {}),
        review: reviewed,
        maxContextChars: config.maxContextChars,
      }),
      messages: chat.messages,
      model: config.model,
      maxOutputTokens: config.maxOutputTokens,
      timeoutMs: config.timeoutMs,
    });
  } catch (error) {
    return llmFailure(error, installTag, deps);
  }

  // The answer itself is checked before it can reach the patient (T14).
  const guarded = guardAnswer(reply);
  if (!guarded.ok) {
    deps.logger.warn("chat.answer_blocked", { installTag, reason: guarded.reason });
    reply = safeBlockedReply();
  }

  deps.logger.info("chat.ok", {
    installTag,
    provider: config.provider,
    model: config.model,
    mode: chat.mode,
    contextShared: chat.context !== undefined,
    urgency: reviewed.urgency,
    reviewed: true,
    answerBlocked: !guarded.ok,
    messages: chat.messages.length,
    durationMs: deps.now() - startedAt,
  });
  return jsonResponse({
    reply,
    emergency: false,
    urgency: reviewed.urgency,
    review: toPublicReview(reviewed),
  });
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
