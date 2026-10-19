import { describe, expect, it } from "vitest";
import { createApp, type AppDeps } from "../src/app";
import type { Env } from "../src/env";
import { createMemoryCounterStore } from "../src/limits/rateLimit";
import type { LlmClient, LlmCompleteInput } from "../src/llm/types";
import { LlmError } from "../src/llm/types";
import { createConsoleLogger } from "../src/logging";
import { safeBlockedReply } from "../src/safety/safeReplies";
import { callWorker, chatRequest, userMessage } from "./helpers";

const FIXED_NOW = Date.UTC(2026, 0, 15, 12, 0, 0);
const DEFAULT_ANSWER = "أنا هنا لمساعدتك في فهم خطتك.";
const DEFAULT_REVIEW =
  '{"urgency":"SELF_CARE","forbiddenRequest":false,"usesProfile":false,"considered":["your schedule"],"gaps":[],"questions":[]}';

interface Harness {
  handler: ExportedHandler<Env>;
  llmCalls: { count: number; purposes: string[]; inputs: LlmCompleteInput[] };
  logs: string[];
  setReview: (raw: string) => void;
  setAnswer: (text: string) => void;
}

function harness(overrides: Partial<AppDeps> = {}): Harness {
  const logs: string[] = [];
  const llmCalls = { count: 0, purposes: [] as string[], inputs: [] as LlmCompleteInput[] };
  const counterStore = createMemoryCounterStore(() => FIXED_NOW);
  let reviewRaw = DEFAULT_REVIEW;
  let answerText = DEFAULT_ANSWER;
  const llm: LlmClient = {
    complete: (input: LlmCompleteInput) => {
      llmCalls.count += 1;
      llmCalls.purposes.push(input.purpose);
      llmCalls.inputs.push(input);
      return Promise.resolve(input.purpose === "review" ? reviewRaw : answerText);
    },
  };
  const deps: Partial<AppDeps> = {
    llm: () => llm,
    store: () => counterStore,
    logger: createConsoleLogger((line) => logs.push(line)),
    now: () => FIXED_NOW,
    ...overrides,
  };
  return {
    handler: createApp(deps),
    llmCalls,
    logs,
    setReview: (raw: string) => {
      reviewRaw = raw;
    },
    setAnswer: (text: string) => {
      answerText = text;
    },
  };
}

async function body(response: Response): Promise<Record<string, unknown>> {
  return (await response.json()) as Record<string, unknown>;
}

function personalizedChat(content = "متى آخذ دوائي؟"): Record<string, unknown> {
  return {
    messages: [userMessage(content)],
    mode: "personalized",
    context: { allergies: [{ label: "بنسلين" }], sections: { allergies: "LISTED", conditions: "NOT_ASKED" } },
  };
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
  it("reviews first, then answers, and reports the urgency", async () => {
    const { handler, llmCalls } = harness();
    const response = await callWorker(handler, chatRequest({ messages: [userMessage("متى آخذ الدواء؟")] }));
    expect(response.status).toBe(200);
    const payload = await body(response);
    expect(payload.emergency).toBe(false);
    expect(payload.urgency).toBe("SELF_CARE");
    expect(payload.review).toMatchObject({ considered: ["your schedule"] });
    expect(llmCalls.purposes).toEqual(["review", "answer"]);
  });

  it("sends the review prompt to the first pass and the answer prompt to the second", async () => {
    const { handler, llmCalls } = harness();
    await callWorker(handler, chatRequest({ messages: [userMessage("مرحبا")] }));
    expect(llmCalls.inputs[0]?.system).toContain("You are the safety reviewer");
    expect(llmCalls.inputs[1]?.system).toContain("Answer the patient's last message");
  });

  it("fences the profile only when the request is personalized", async () => {
    const { handler, llmCalls } = harness();
    await callWorker(handler, chatRequest(personalizedChat()));
    const answerSystem = llmCalls.inputs[1]?.system ?? "";
    expect(answerSystem).toContain("<<<PATIENT_CONTEXT");
    expect(answerSystem).toContain("section allergies: LISTED");
    expect(answerSystem).toContain("section chronic conditions: NOT_ASKED");
    expect(answerSystem).toContain("not asked");

    const second = harness();
    await callWorker(second.handler, chatRequest({ messages: [userMessage("مرحبا")] }));
    expect(second.llmCalls.inputs[1]?.system).not.toContain("PATIENT_CONTEXT");
  });

  it("still accepts the legacy medication block from an older app", async () => {
    const { handler, llmCalls } = harness();
    const response = await callWorker(
      handler,
      chatRequest({ messages: [userMessage("مرحبا")], shareMedications: true, medicationContext: "Aspirin 100 mg" }),
    );
    expect(response.status).toBe(200);
    expect(llmCalls.inputs[1]?.system).toContain("MEDICATION_CONTEXT");
    expect(llmCalls.inputs[1]?.system).toContain("Aspirin 100 mg");
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

  it("turns an emergency review into the emergency card without answering", async () => {
    const { handler, llmCalls, setReview } = harness();
    setReview('{"urgency":"EMERGENCY","forbiddenRequest":false,"usesProfile":true,"considered":[],"gaps":[],"questions":[]}');
    const response = await callWorker(handler, chatRequest(personalizedChat("أشعر بضيق شديد")), {
      EMERGENCY_NUMBER: "997",
    });
    expect(response.status).toBe(200);
    const payload = await body(response);
    expect(payload.emergency).toBe(true);
    expect(payload.kind).toBe("review");
    expect(String(payload.reply)).toContain("997");
    expect(llmCalls.purposes).toEqual(["review"]);
  });

  it("declines a request to change a medicine without answering", async () => {
    const { handler, llmCalls, setReview } = harness();
    setReview('{"urgency":"SEE_DOCTOR_SOON","forbiddenRequest":true,"usesProfile":false,"considered":[],"gaps":[],"questions":[]}');
    const response = await callWorker(handler, chatRequest({ messages: [userMessage("هل أوقف دوائي؟")] }));
    expect(response.status).toBe(200);
    const payload = await body(response);
    expect(payload.emergency).toBe(false);
    expect(payload.urgency).toBe("SEE_DOCTOR_SOON");
    expect(String(payload.reply)).toContain("لا أستطيع");
    expect(llmCalls.purposes).toEqual(["review"]);
  });

  it("fails closed when the review cannot be parsed", async () => {
    const { handler, llmCalls, setReview } = harness();
    setReview("I think this is fine, no JSON here.");
    const response = await callWorker(handler, chatRequest({ messages: [userMessage("مرحبا")] }));
    expect(response.status).toBe(502);
    expect((await body(response)).error).toMatchObject({ code: "llm_invalid_review" });
    expect(llmCalls.purposes).toEqual(["review"]);
  });

  it("replaces an answer that instructs a medicine change", async () => {
    const { handler, logs, setAnswer } = harness();
    setAnswer("Stop taking your medicine today and double the dose tomorrow.");
    const response = await callWorker(handler, chatRequest({ messages: [userMessage("ماذا أفعل؟")] }));
    expect(response.status).toBe(200);
    const payload = await body(response);
    expect(payload.reply).toBe(safeBlockedReply());
    expect(logs.join("\n")).toContain("chat.answer_blocked");
  });

  it("keeps a cautious warning that tells the patient not to change anything", async () => {
    const { handler, setAnswer } = harness();
    const cautious = "Do not stop taking your medicine without asking your doctor or pharmacist first.";
    setAnswer(cautious);
    const response = await callWorker(handler, chatRequest({ messages: [userMessage("ماذا أفعل؟")] }));
    expect((await body(response)).reply).toBe(cautious);
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
