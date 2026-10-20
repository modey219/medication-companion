import { normalizeForMatching } from "./emergency";

/**
 * Removes the "please confirm with your doctor or pharmacist" line that the
 * model appends to almost every answer (T15 polish, D-030).
 *
 * The prompt asks for it to stop and the model still does it, so this is the
 * deterministic half. It is deliberately narrow:
 *
 * - only trailing sentences are considered, at most two of them;
 * - the sentence must mention a clinician AND a reminder cue;
 * - a sentence with an emergency word or the emergency number is never removed;
 * - a reply is never shortened to less than a short paragraph, so a one-line
 *   answer that is itself a reminder stays intact.
 *
 * The app shows one persistent disclaimer under the chat, which is why the
 * boilerplate can be removed at all. TODO(clinical-review, T15): confirm these
 * patterns and the two-sentence cap with the medical reviewer.
 */

const CLINICIAN_WORDS = ["طبيب", "صيدلي", "doctor", "pharmacist", "clinician"];

const REMINDER_CUES = [
  "يرجى",
  "تذكر",
  "تاكد",
  "استشر",
  "راجع",
  "اسال",
  "سؤال",
  "من فضلك",
  "للتاكد",
  "قبل اتخاذ",
  "قبل تغيير",
  "قبل البدء",
  "قبل الاستخدام",
  "consult",
  "ask your",
  "speak to",
  "talk to",
  "check with",
  "confirm with",
  "remember to",
  "please",
];

const EMERGENCY_CUES = ["طوارئ", "فورا", "حالا", "911", "999", "112", "emergency", "immediately"];

const WRAPPERS = /^[\s([{*_"'“”«»]+|[\s)\]}*_"'“”«»]+$/g;

const MAX_REMINDER_CHARS = 220;
const MIN_KEPT_CHARS = 20;
const MAX_STRIPPED_SENTENCES = 2;

function isClosingReminder(sentence: string): boolean {
  const cleaned = sentence.replace(WRAPPERS, "");
  const text = normalizeForMatching(cleaned);
  if (text.length === 0 || text.length > MAX_REMINDER_CHARS) {
    return false;
  }
  if (EMERGENCY_CUES.some((cue) => text.includes(cue))) {
    return false;
  }
  if (!CLINICIAN_WORDS.some((word) => text.includes(word))) {
    return false;
  }
  return REMINDER_CUES.some((cue) => text.includes(cue));
}

/** Returns [answer] without a trailing consult reminder, if one is there. */
export function stripClosingReminder(answer: string): string {
  let text = answer.trim();
  for (let removed = 0; removed < MAX_STRIPPED_SENTENCES; removed += 1) {
    const match = /([^.!?\u061F\n]+[.!?\u061F][)\]}*_"'“”«»\s]*)$/.exec(text);
    if (!match || match.index === undefined) {
      break;
    }
    if (!isClosingReminder(match[1])) {
      break;
    }
    const rest = text.slice(0, match.index).trimEnd();
    if (rest.length < MIN_KEPT_CHARS) {
      break;
    }
    text = rest;
  }
  return text;
}
