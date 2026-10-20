import { describe, expect, it } from "vitest";
import { parseReview, ReviewError, reviewForPrompt, toPublicReview } from "../src/safety/review";

const VALID = {
  urgency: "SEE_DOCTOR_SOON",
  forbiddenRequest: false,
  usesProfile: true,
  considered: ["جدول المضاد الحيوي"],
  gaps: ["آخر قراءة لضغط الدم"],
  questions: ["هل أحتاج تحليل دم؟"],
};

describe("parseReview", () => {
  it("parses a valid review", () => {
    expect(parseReview(JSON.stringify(VALID))).toEqual(VALID);
  });

  it("parses a review wrapped in a markdown fence", () => {
    const raw = `\`\`\`json\n${JSON.stringify(VALID)}\n\`\`\``;
    expect(parseReview(raw).urgency).toBe("SEE_DOCTOR_SOON");
  });

  it("ignores prose around the JSON object", () => {
    const raw = `Here is the review:\n${JSON.stringify(VALID)}\nDone.`;
    expect(parseReview(raw).considered).toEqual(["جدول المضاد الحيوي"]);
  });

  it("rejects a reply that is not JSON", () => {
    expect(() => parseReview("Everything looks fine.")).toThrow(ReviewError);
  });

  it("rejects a missing or unknown urgency", () => {
    expect(() => parseReview(JSON.stringify({ ...VALID, urgency: undefined }))).toThrow(ReviewError);
    expect(() => parseReview(JSON.stringify({ ...VALID, urgency: "URGENT" }))).toThrow(ReviewError);
  });

  it("rejects a review without the forbidden flag", () => {
    expect(() => parseReview(JSON.stringify({ ...VALID, forbiddenRequest: "no" }))).toThrow(ReviewError);
  });

  it("treats usesProfile as false unless it is exactly true", () => {
    expect(parseReview(JSON.stringify({ ...VALID, usesProfile: "yes" })).usesProfile).toBe(false);
  });

  it("caps the bullet lists", () => {
    const review = parseReview(
      JSON.stringify({
        ...VALID,
        considered: ["a", "b", "c", "d", "e", "f", "g"],
        gaps: ["1", "2", "3", "4", "5", "6"],
        questions: ["x", "y", "z", "w"],
      }),
    );
    expect(review.considered).toHaveLength(5);
    expect(review.gaps).toHaveLength(5);
    expect(review.questions).toHaveLength(3);
  });

  it("drops non-string, empty and over-long bullets", () => {
    const review = parseReview(
      JSON.stringify({
        ...VALID,
        considered: ["ok", 42, "   ", "x".repeat(161), null],
      }),
    );
    expect(review.considered).toEqual(["ok"]);
  });

  it("drops a bullet that instructs a medicine change", () => {
    const review = parseReview(
      JSON.stringify({ ...VALID, considered: ["توقف عن تناول الدواء", "جدول الدواء"] }),
    );
    expect(review.considered).toEqual(["جدول الدواء"]);
  });

  it("never returns an answer to a review with no urgency", () => {
    expect(() => parseReview(JSON.stringify({ ...VALID, urgency: null }))).toThrow(ReviewError);
  });
});

describe("review views", () => {
  it("only exposes the patient-safe bullets publicly", () => {
    const review = parseReview(JSON.stringify(VALID));
    expect(toPublicReview(review)).toEqual({
      considered: VALID.considered,
      gaps: VALID.gaps,
      questions: VALID.questions,
    });
    expect(toPublicReview(review)).not.toHaveProperty("urgency");
    expect(toPublicReview(review)).not.toHaveProperty("usesProfile");
  });

  it("gives the answer pass the urgency without leaking the profile flag", () => {
    const review = parseReview(JSON.stringify(VALID));
    const prompt = reviewForPrompt(review);
    expect(prompt).toContain("SEE_DOCTOR_SOON");
    expect(prompt).not.toContain("usesProfile");
  });
});
