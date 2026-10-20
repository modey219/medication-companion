import { describe, expect, it } from "vitest";
import { guardAnswer, isUnsafeText } from "../src/safety/answerGuard";

describe("guardAnswer", () => {
  it("blocks an empty answer", () => {
    expect(guardAnswer("   ")).toEqual({ ok: false, reason: "empty_answer" });
  });

  it("allows a normal, cautious reply", () => {
    const reply =
      "قد يكون الصداع من الأعراض الشائعة. من الأفضل أن تخبر طبيبك أو الصيدلي قبل أي تغيير. " +
      "اشرب ماء كافيًا واسترح.";
    expect(guardAnswer(reply)).toEqual({ ok: true });
  });

  it("blocks a leaked prompt marker", () => {
    expect(guardAnswer("<<<PATIENT_CONTEXT\nsection allergies: LISTED")).toEqual({
      ok: false,
      reason: "system_prompt_leak",
    });
  });

  it("blocks an English instruction to stop or change a medicine", () => {
    expect(guardAnswer("Stop taking your medicine today and double the dose tomorrow.")).toEqual({
      ok: false,
      reason: "forbidden_instruction",
    });
  });

  it("blocks an Arabic instruction to stop the medicine", () => {
    expect(guardAnswer("توقف عن تناول الدواء من اليوم.")).toEqual({
      ok: false,
      reason: "forbidden_instruction",
    });
  });

  it("blocks an instruction to raise the dose", () => {
    expect(guardAnswer("زد الجرعة إلى حبتين.")).toEqual({ ok: false, reason: "forbidden_instruction" });
  });

  it("leaves a warning that tells the patient not to change anything", () => {
    expect(guardAnswer("Do not stop taking your medicine without asking your doctor first.")).toEqual({
      ok: true,
    });
    expect(guardAnswer("لا توقف الدواء دون استشارة طبيبك.")).toEqual({ ok: true });
  });

  it("blocks a claim that a combination is safe", () => {
    expect(guardAnswer("It is safe to take them together with your other tablets.")).toEqual({
      ok: false,
      reason: "combination_claim",
    });
    expect(guardAnswer("هذا الدواء آمن مع دوائك.")).toEqual({ ok: false, reason: "combination_claim" });
  });

  it("allows a reply that says the combination cannot be verified", () => {
    expect(guardAnswer("لا أستطيع التأكد من أمان الجمع بينهما. اسأل الصيدلي.")).toEqual({ ok: true });
  });
});

describe("isUnsafeText", () => {
  it("is true for an instruction and false for a plain phrase", () => {
    expect(isUnsafeText("ابدأ الدواء غدًا")).toBe(true);
    expect(isUnsafeText("جدول الدواء")).toBe(false);
  });
});
