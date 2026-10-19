import { describe, expect, it } from "vitest";
import { detectEmergency, emergencyReply, normalizeForMatching } from "../src/safety/emergency";

describe("normalizeForMatching", () => {
  it("removes Arabic diacritics and tatweel", () => {
    expect(normalizeForMatching("التَّنَـفُّس")).toBe("التنفس");
  });

  it("unifies alef, alef maqsura and ta marbuta", () => {
    expect(normalizeForMatching("إغماء")).toBe("إغماء".replace("إ", "ا"));
    expect(normalizeForMatching("إغماء")).toBe("اغماء");
    expect(normalizeForMatching("حالة")).toBe("حاله");
  });

  it("lowercases and collapses spaces", () => {
    expect(normalizeForMatching("  Chest   PAIN ")).toBe("chest pain");
  });
});

describe("detectEmergency", () => {
  it("detects Arabic chest pain", () => {
    expect(detectEmergency("عندي ألم شديد في الصدر")).toBe("chest_pain");
  });

  it("detects Arabic breathing difficulty with diacritics", () => {
    expect(detectEmergency("أشعر بضِيق في التَّنفُّس")).toBe("breathing");
  });

  it("detects English chest pain", () => {
    expect(detectEmergency("I have chest pain since this morning")).toBe("chest_pain");
  });

  it("detects English breathing difficulty", () => {
    expect(detectEmergency("I cannot breathe properly")).toBe("breathing");
  });

  it("detects a severe allergic reaction", () => {
    expect(detectEmergency("وجهي متورم والشفاه")).toBe("allergic_reaction");
  });

  it("detects thoughts of self-harm in Arabic", () => {
    expect(detectEmergency("أفكر في الانتحار")).toBe("self_harm");
  });

  it("detects thoughts of self-harm in English", () => {
    expect(detectEmergency("sometimes I want to kill myself")).toBe("self_harm");
  });

  it("detects a suspected overdose", () => {
    expect(detectEmergency("أظن أني تناولت جرعة زائدة")).toBe("overdose");
  });

  it("returns undefined for ordinary questions", () => {
    expect(detectEmergency("متى آخذ الدواء؟")).toBeUndefined();
    expect(detectEmergency("Can I take my tablet with food?")).toBeUndefined();
    expect(detectEmergency("عندي صداع خفيف")).toBeUndefined();
    expect(detectEmergency("")).toBeUndefined();
  });

  it("does not flag an ordinary nosebleed as serious bleeding", () => {
    expect(detectEmergency("عندي نزيف بسيط من الأنف")).toBeUndefined();
  });
});

describe("emergencyReply", () => {
  it("includes the configured number in both languages", () => {
    const reply = emergencyReply("997");
    expect(reply).toContain("997");
    expect(reply).toContain("قسم طوارئ");
    expect(reply).toContain("emergency department");
  });

  it("falls back to a placeholder number when none is configured", () => {
    expect(emergencyReply("  ")).toContain("112");
  });
});
