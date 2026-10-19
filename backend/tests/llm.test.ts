import { describe, expect, it } from "vitest";
import { createLlmClient } from "../src/llm";
import { createMockClient, MOCK_REPLY } from "../src/llm/mock";
import { createOpenAiCompatibleClient } from "../src/llm/openaiCompatible";
import { LlmError } from "../src/llm/types";
import { testConfig } from "./helpers";

const INPUT = {
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
