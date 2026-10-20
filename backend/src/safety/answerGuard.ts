import { normalizeForMatching } from "./emergency";

/**
 * A last line of defence on the generated answer (T14, AGENTS.md > Medical
 * Safety Rules).
 *
 * The system prompt and the review pass do the real work; this checks the
 * answer itself before it is stored in a response. It only flags *affirmative*
 * instructions ("stop your medicine", "double the dose", "it is safe to take
 * them together"); a sentence that warns against the same action ("do not stop
 * your medicine without asking") is left alone.
 *
 * The list is deliberately small and conservative, and it is heuristic: it is
 * a net, not a proof. TODO(clinical-review, T14): review the patterns and the
 * false-positive risk with the medical reviewer.
 */
export type GuardReason = "empty_answer" | "system_prompt_leak" | "forbidden_instruction" | "combination_claim";

export type GuardResult = { ok: true } | { ok: false; reason: GuardReason };

const LEAK_MARKERS = [
  "<<<MEDICATION_CONTEXT",
  "MEDICATION_CONTEXT>>>",
  "<<<PATIENT_CONTEXT",
  "PATIENT_CONTEXT>>>",
  "<<<REVIEW",
  "REVIEW>>>",
];

/**
 * Affirmative medicine or dose instructions. Written against the normalised
 * text (lowercase, no Arabic diacritics, unified alef/ya/ta-marbuta), so
 * "الجُرعة" and "الجرعه" both match "الجرعه".
 */
const INSTRUCTION_PATTERNS: RegExp[] = [
  /\b(stop|quit|discontinue|skip|start|begin|change|switch|replace)\b[^.!?]{0,40}\b(dose|doses|medicine|medication|tablet|tablets|pill|pills|drug|treatment)\b/,
  /\b(increase|reduce|lower|raise|double|halve|decrease|cut)\b[^.!?]{0,30}\b(dose|dosage|doses)\b/,
  /\b(take|use)\b[^.!?]{0,20}\b(less|more)\b[^.!?]{0,20}\b(medicine|medication|tablet|pill|dose)\b/,
  /(توقف|توقفي|اوقف|اوقفي|ابدأ|ابدا|تبدا|غير|غيري|بدل|بدلي|استبدل|استبدلي)[^.!?]{0,40}(الدواء|دواء|العلاج|الجرعه|جرعه|الحبوب|حبوب)/,
  /(زد|زيد|زياده|ارفع|خفض|خفف|قلل|انقص|نقص|ضاعف|ضاعفي)[^.!?]{0,30}(الجرعه|جرعه|الدواء|دواء)/,
  /(خذ|تناول|خدي|تناولي)[^.!?]{0,25}(اكثر|اقل)[^.!?]{0,25}(الدواء|دواء|الجرعه|جرعه)/,
];

const COMBINATION_PATTERNS: RegExp[] = [
  /\b(safe|ok|okay|fine|harmless|no problem)\b[^.!?]{0,40}\b(to take|to mix|to combine|together|with)\b/,
  /(امن|امنه|امنين|لا مشكله|عادي|بامكانك)[^.!?]{0,40}(مع|جنب|مع بعض|سويا|سويه)/,
];

const NEGATIONS = ["not ", "never", "dont", "do not", "no need", "لا ", "ولا ", "اياك", "ممنوع", "تجنب", "ابدا", "بدون"];

function hasMatchOutsideNegation(text: string, pattern: RegExp): boolean {
  const flags = pattern.flags.includes("g") ? pattern.flags : `${pattern.flags}g`;
  for (const match of text.matchAll(new RegExp(pattern.source, flags))) {
    if (match.index === undefined) {
      continue;
    }
    const before = text.slice(Math.max(0, match.index - 40), match.index);
    if (!NEGATIONS.some((negation) => before.includes(negation))) {
      return true;
    }
  }
  return false;
}

export function guardAnswer(answer: string): GuardResult {
  const raw = answer.trim();
  if (raw.length === 0) {
    return { ok: false, reason: "empty_answer" };
  }
  if (LEAK_MARKERS.some((marker) => raw.includes(marker))) {
    return { ok: false, reason: "system_prompt_leak" };
  }
  const text = normalizeForMatching(raw);
  if (INSTRUCTION_PATTERNS.some((pattern) => hasMatchOutsideNegation(text, pattern))) {
    return { ok: false, reason: "forbidden_instruction" };
  }
  if (COMBINATION_PATTERNS.some((pattern) => hasMatchOutsideNegation(text, pattern))) {
    return { ok: false, reason: "combination_claim" };
  }
  return { ok: true };
}

/** True when the text must not be shown to the patient as it is. */
export function isUnsafeText(text: string): boolean {
  return !guardAnswer(text).ok;
}
