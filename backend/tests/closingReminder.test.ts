import { describe, expect, it } from "vitest";
import { stripClosingReminder } from "../src/safety/closingReminder";

const BODY = "الميتفورمين دواء يستخدم عادة لعلاج السكري من النوع الثاني ويساعد على خفض سكر الدم.";

describe("stripClosingReminder", () => {
  it("removes an Arabic reminder in parentheses", () => {
    const answer = `${BODY}\n\n(يرجى التأكد من ذلك مع طبيبك أو الصيدلي.)`;
    expect(stripClosingReminder(answer)).toBe(BODY);
  });

  it("removes an Arabic reminder written in italics on its own line", () => {
    const answer = `${BODY}\n\n*تأكد من استشارة طبيبك أو الصيدلي قبل اتخاذ أي قرار.*`;
    expect(stripClosingReminder(answer)).toBe(BODY);
  });

  it("removes an English reminder", () => {
    const answer = "This medicine is usually taken with food. Please consult your doctor or pharmacist.";
    expect(stripClosingReminder(answer)).toBe("This medicine is usually taken with food.");
  });

  it("keeps a reply that is only a reminder", () => {
    const answer = "راجع الطبيب أو الصيدلي قريبًا.";
    expect(stripClosingReminder(answer)).toBe(answer);
  });

  it("keeps emergency directions", () => {
    const answer = "هذه علامات خطيرة. اتصل بالطوارئ على 112 فورًا.";
    expect(stripClosingReminder(answer)).toBe(answer);
  });

  it("keeps a sentence about the doctor that is not a reminder", () => {
    const answer = "وصف لك الطبيب هذا الدواء لارتفاع ضغط الدم.";
    expect(stripClosingReminder(answer)).toBe(answer);
  });

  it("removes at most two trailing reminders", () => {
    const answer = `${BODY} يشرح الطبيب الجرعة في الوصفة. يرجى سؤال الصيدلي. تأكد من مراجعة طبيبك.`;
    const result = stripClosingReminder(answer);
    expect(result).not.toContain("يرجى");
    expect(result).not.toContain("تأكد");
    expect(result).toContain("يشرح الطبيب الجرعة");
    expect(result.startsWith(BODY)).toBe(true);
  });
});
