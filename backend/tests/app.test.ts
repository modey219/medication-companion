import { describe, expect, it } from "vitest";
import { createApp, type AppDeps } from "../src/app";
import type { Env } from "../src/env";
import { createMemoryCounterStore } from "../src/limits/rateLimit";
import type { LlmClient } from "../src/llm/types";
import { LlmError } from "../src/llm/types";
import { createConsoleLogger } from "../src/logging";
import { callWorker, chatRequest, userMessage } from "./helpers";

const FIXED_NOW = Date.UTC(2026, 0, 15, 12, 0, 0);

interface Harness {
  handler: ExportedHandler<Env>;
  llmCalls: { count: number };
  logs: string[];
}

function harness(overrides: Partial<AppDeps> = {}): Harness {
  const logs: string[] = [];
  const llmCalls = { count: 0 };
  const counterStore = createMemoryCounterStore(() => FIXED_NOW);
  const llm: LlmClient = {
    complete: () => {
      llmCalls.count += 1;
      return Promise.resolve("أنا هنا لمساعدتك في فهم خطتك.");
    },
  };
  const deps: Partial<AppDeps> = {
    llm: () => llm,
    store: () => counterStore,
    logger: createConsoleLogger((line) => logs.push(line)),
    now: () => FIXED_NOW,
    ...overrides,
  };
  return { handler: createApp(deps), llmCalls, logs };
}

async function body(response: Response): Promise<Record<string, unknown>> {
  return (await response.json()) as Record<string, unknown>;
}

describe("routing", () => {
  it("answers the health check", async () => {
    const { handler } = harness();
    const response = await callWorker(handler, new Request("https://a.example/health"));
    expect(response.status).toBe(200);
    await expect(body(response)).resolves.toEqual({ status: "ok" });
  });

  it("rejects a non-GET health check", async () => {
    const { handler } = harness();
    const response = await callWorker(handler, new Request("https://a.example/health", { method: "POST" }));
    expect(response.status).toBe(405);
  });

  it("returns 404 for an unknown path", async () => {
    const { handler } = harness();
    const response = await callWorker(handler, new Request("https://a.example/nope"));
    expect(response.status).toBe(404);
    expect((await body(response)).error).toMatchObject({ code: "not_found" });
  });

  it("rejects a non-POST chat call", async () => {
    const { handler } = harness();
    const response = await callWorker(handler, new Request("https://a.example/v1/chat"));
    expect(response.status).toBe(405);
  });
});

describe("POST /v1/chat validation", () => {
  it("requires an install id", async () => {
    const { handler } = harness();
    const request = new Request("https://a.example/v1/chat", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ messages: [userMessage("hi")] }),
    });
    const response = await callWorker(handler, request);
    expect(response.status).toBe(400);
    expect((await body(response)).error).toMatchObject({ code: "invalid_install_id" });
  });

  it("rejects malformed JSON", async () => {
    const { handler } = harness();
    const request = new Request("https://a.example/v1/chat", {
      method: "POST",
      headers: { "content-type": "application/json", "x-install-id": "install-1234567890" },
      body: "{oops",
    });
    const response = await callWorker(handler, request);
    expect(response.status).toBe(400);
    expect((await body(response)).error).toMatchObject({ code: "invalid_json" });
  });

  it("rejects an empty conversation", async () => {
    const { handler } = harness();
    const response = await callWorker(handler, chatRequest({ messages: [] }));
    expect(response.status).toBe(400);
    expect((await body(response)).error).toMatchObject({ code: "invalid_messages" });
  });
});

describe("POST /v1/chat behaviour", () => {
  it("answers an ordinary question and reports no emergency", async () => {
    const { handler, llmCalls } = harness();
    const response = await callWorker(handler, chatRequest({ messages: [userMessage("متى آخذ الدواء؟")] }));
    expect(response.status).toBe(200);
    await expect(body(response)).resolves.toMatchObject({ emergency: false });
    expect(llmCalls.count).toBe(1);
  });

  it("short-circuits urgent symptoms without calling the model", async () => {
    const { handler, llmCalls } = harness();
    const response = await callWorker(
      handler,
      chatRequest({ messages: [userMessage("عندي ألم شديد في الصدر")] }),
      { EMERGENCY_NUMBER: "997" },
    );
    expect(response.status).toBe(200);
    const payload = await body(response);
    expect(payload.emergency).toBe(true);
    expect(String(payload.reply)).toContain("997");
    expect(llmCalls.count).toBe(0);
  });

  it("rejects integrity-less requests when enforcement is on", async () => {
    const { handler, llmCalls } = harness();
    const response = await callWorker(handler, chatRequest({ messages: [userMessage("مرحبا")] }), {
      PLAY_INTEGRITY_MODE: "enforce",
      PLAY_INTEGRITY_PACKAGE_NAME: "com.medcompanion.app",
    });
    expect(response.status).toBe(401);
    expect((await body(response)).error).toMatchObject({ code: "integrity_failed" });
    expect(llmCalls.count).toBe(0);
  });

  it("rate limits a burst with a Retry-After header", async () => {
    const { handler } = harness();
    const env: Env = { RATE_LIMIT_PER_MINUTE: "1" };
    await callWorker(handler, chatRequest({ messages: [userMessage("first")] }), env);
    const response = await callWorker(handler, chatRequest({ messages: [userMessage("second")] }), env);
    expect(response.status).toBe(429);
    expect(response.headers.get("retry-after")).toBeTruthy();
    expect((await body(response)).error).toMatchObject({ code: "rate_limited" });
  });

  it("maps an LLM timeout to 504", async () => {
    const { handler } = harness({
      llm: () => ({
        complete: () => Promise.reject(new LlmError("llm_timeout", "The assistant took too long to reply.")),
      }),
    });
    const response = await callWorker(handler, chatRequest({ messages: [userMessage("مرحبا")] }));
    expect(response.status).toBe(504);
    expect((await body(response)).error).toMatchObject({ code: "llm_timeout" });
  });

  it("maps an LLM failure to 502", async () => {
    const { handler } = harness({
      llm: () => ({
        complete: () => Promise.reject(new LlmError("llm_http_error", "The assistant is unavailable right now.")),
      }),
    });
    const response = await callWorker(handler, chatRequest({ messages: [userMessage("مرحبا")] }));
    expect(response.status).toBe(502);
    expect((await body(response)).error).toMatchObject({ code: "llm_http_error" });
  });

  it("never writes message content to the logs", async () => {
    const { handler, logs } = harness();
    const secret = "سر طبي خاص بالمريض";
    await callWorker(handler, chatRequest({ messages: [userMessage(secret)] }));
    expect(logs.length).toBeGreaterThan(0);
    expect(logs.join("\n")).not.toContain(secret);
  });
});
