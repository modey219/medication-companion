import { describe, expect, it } from "vitest";
import { createLlmClient } from "../src/llm";
import { createMockClient, MOCK_REPLY, MOCK_REVIEW } from "../src/llm/mock";
import { createOpenAiCompatibleClient } from "../src/llm/openaiCompatible";
import { LlmError } from "../src/llm/types";
import { testConfig } from "./helpers";

const INPUT = {
  purpose: "answer" as const,
  system: "system prompt",
  messages: [{ role: "user" as const, content: "مرحبا" }],
  model: "test-model",
  maxOutputTokens: 128,
  timeoutMs: 5_000,
};

describe("createOpenAiCompatibleClient", () => {
  it("returns the assistant reply and sends the system prompt first", async () => {
    let capturedBody: Record<string, unknown> | undefined;
    const fetchImpl = (async (_input: RequestInfo | URL, init?: RequestInit) => {
      capturedBody = JSON.parse(String(init?.body ?? "{}")) as Record<string, unknown>;
      return new Response(JSON.stringify({ choices: [{ message: { content: "أهلاً بك" } }] }), { status: 200 });
    }) as unknown as typeof fetch;

    const client = createOpenAiCompatibleClient({
      apiKey: "test-key",
      baseUrl: "https://api.example.com/v1/",
      fetchImpl,
    });

    await expect(client.complete(INPUT)).resolves.toBe("أهلاً بك");
    expect(capturedBody?.model).toBe("test-model");
    expect(capturedBody?.max_tokens).toBe(128);
    const messages = capturedBody?.messages as { role: string; content: string }[];
    expect(messages[0]).toEqual({ role: "system", content: "system prompt" });
    expect(messages[1]).toEqual({ role: "user", content: "مرحبا" });
  });

  it("maps a provider error status to an llm_http_error", async () => {
    const fetchImpl = (async () => new Response("nope", { status: 429 })) as unknown as typeof fetch;
    const client = createOpenAiCompatibleClient({ apiKey: "k", baseUrl: "https://api.example.com", fetchImpl });
    await expect(client.complete(INPUT)).rejects.toMatchObject({ code: "llm_http_error", status: 429 });
  });

  it("keeps the provider status and a redacted detail for the log", async () => {
    const payload = JSON.stringify({
      error: {
        type: "invalid_request_error",
        code: "invalid_api_key",
        message: "Incorrect API key provided: sk-abcdef1234567890",
      },
    });
    const fetchImpl = (async () =>
      new Response(payload, {
        status: 401,
        headers: { "content-type": "application/json" },
      })) as unknown as typeof fetch;
    const client = createOpenAiCompatibleClient({ apiKey: "k", baseUrl: "https://api.example.com", fetchImpl });

    const failure = await client.complete(INPUT).catch((reason: unknown) => reason);

    expect(failure).toBeInstanceOf(LlmError);
    const llmError = failure as LlmError;
    expect(llmError.status).toBe(401);
    expect(llmError.detail).toContain("invalid_api_key");
    expect(llmError.detail).toContain("Incorrect API key provided");
    expect(llmError.detail).not.toContain("abcdef1234567890");
    expect((llmError.detail ?? "").length).toBeLessThanOrEqual(300);
  });

  it("reports a non-JSON provider error by content type only", async () => {
    const fetchImpl = (async () =>
      new Response("<html>bad gateway</html>", {
        status: 502,
        headers: { "content-type": "text/html; charset=utf-8" },
      })) as unknown as typeof fetch;
    const client = createOpenAiCompatibleClient({ apiKey: "k", baseUrl: "https://api.example.com", fetchImpl });

    const failure = await client.complete(INPUT).catch((reason: unknown) => reason);

    expect((failure as LlmError).detail).toBe("non-json text/html");
  });

  it("says so when the provider error body is empty", async () => {
    const fetchImpl = (async () => new Response("", { status: 404 })) as unknown as typeof fetch;
    const client = createOpenAiCompatibleClient({ apiKey: "k", baseUrl: "https://api.example.com", fetchImpl });

    const failure = await client.complete(INPUT).catch((reason: unknown) => reason);

    expect((failure as LlmError).detail).toBe("empty-body");
  });

  it("falls back to the JSON key names when no known field is present", async () => {
    const fetchImpl = (async () =>
      new Response(JSON.stringify({ error: { status: "NOT_FOUND" } }), {
        status: 404,
        headers: { "content-type": "application/json" },
      })) as unknown as typeof fetch;
    const client = createOpenAiCompatibleClient({ apiKey: "k", baseUrl: "https://api.example.com", fetchImpl });

    const failure = await client.complete(INPUT).catch((reason: unknown) => reason);

    expect((failure as LlmError).detail).toBe("json-keys: status");
  });

  it("reads an array-wrapped provider error", async () => {
    const payload = JSON.stringify([
      { error: { code: 404, message: "models/gemini-2.0-flash is not found", status: "NOT_FOUND" } },
    ]);
    const fetchImpl = (async () =>
      new Response(payload, {
        status: 404,
        headers: { "content-type": "application/json" },
      })) as unknown as typeof fetch;
    const client = createOpenAiCompatibleClient({ apiKey: "k", baseUrl: "https://api.example.com", fetchImpl });

    const failure = await client.complete(INPUT).catch((reason: unknown) => reason);

    expect((failure as LlmError).detail).toBe("models/gemini-2.0-flash is not found");
  });

  it("maps an aborted request to an llm_timeout", async () => {
    const fetchImpl = (async () => {
      throw new DOMException("The operation was aborted.", "AbortError");
    }) as unknown as typeof fetch;
    const client = createOpenAiCompatibleClient({ apiKey: "k", baseUrl: "https://api.example.com", fetchImpl });
    await expect(client.complete(INPUT)).rejects.toMatchObject({ code: "llm_timeout" });
  });

  it("maps an empty completion to an llm_empty_response", async () => {
    const fetchImpl = (async () =>
      new Response(JSON.stringify({ choices: [] }), { status: 200 })) as unknown as typeof fetch;
    const client = createOpenAiCompatibleClient({ apiKey: "k", baseUrl: "https://api.example.com", fetchImpl });
    await expect(client.complete(INPUT)).rejects.toMatchObject({ code: "llm_empty_response" });
  });

  it("maps an unexpected network failure to llm_unreachable", async () => {
    const fetchImpl = (async () => {
      throw new TypeError("fetch failed");
    }) as unknown as typeof fetch;
    const client = createOpenAiCompatibleClient({ apiKey: "k", baseUrl: "https://api.example.com", fetchImpl });
    await expect(client.complete(INPUT)).rejects.toMatchObject({ code: "llm_unreachable" });
  });

  it("raises a typed error instance", async () => {
    const fetchImpl = (async () => new Response("nope", { status: 500 })) as unknown as typeof fetch;
    const client = createOpenAiCompatibleClient({ apiKey: "k", baseUrl: "https://api.example.com", fetchImpl });
    await expect(client.complete(INPUT)).rejects.toBeInstanceOf(LlmError);
  });
});

describe("createMockClient", () => {
  it("returns the canned reply without touching the network", async () => {
    const client = createMockClient();
    await expect(client.complete(INPUT)).resolves.toBe(MOCK_REPLY);
  });

  it("returns a valid review for the review pass", async () => {
    const client = createMockClient();
    const raw = await client.complete({ ...INPUT, purpose: "review" });
    expect(raw).toBe(MOCK_REVIEW);
    expect(JSON.parse(raw)).toMatchObject({ urgency: "SELF_CARE", forbiddenRequest: false });
  });
});

describe("createLlmClient", () => {
  it("builds the mock client", () => {
    expect(createLlmClient(testConfig({ LLM_PROVIDER: "mock" }))).toBeDefined();
  });

  it("refuses to build an openai-compatible client without an api key", () => {
    expect(() => createLlmClient(testConfig({ LLM_PROVIDER: "openai-compatible", LLM_API_KEY: "" }))).toThrow();
  });

  it("rejects an unknown provider", () => {
    expect(() => createLlmClient(testConfig({ LLM_PROVIDER: "mystery" }))).toThrow(/Unsupported LLM_PROVIDER/);
  });
});
