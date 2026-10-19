/**
 * Urgent-symptom detection (Arabic and English), used on the server.
 *
 * This is the server-side half of the rule in AGENTS.md: an urgent symptom must
 * produce a short, calm emergency message immediately, without asking further
 * questions and without calling the model. The app runs the same check before
 * sending (T10), so this is defence in depth.
 *
 * A rule is "all groups must match; inside a group any alternative matches",
 * which tolerates word order and filler words in real patient sentences
 * ("عندي ألم شديد في الصدر") without matching a bare body-part mention.
 *
 * TODO(clinical-review, T9): confirm the keyword list, the reply wording and the
 * local emergency number with the medical reviewer (see docs/CLINICAL_REVIEW.md).
 */

export type EmergencyKind =
  | "chest_pain"
  | "breathing"
  | "allergic_reaction"
  | "fainting"
  | "stroke"
  | "bleeding"
  | "overdose"
  | "self_harm";

/** Remove Arabic diacritics and tatweel, unify letter shapes, collapse spaces. */
export function normalizeForMatching(input: string): string {
  return input
    .replace(/[\u064B-\u0652\u0640]/g, "")
    .replace(/[\u0622\u0623\u0625]/g, "\u0627")
    .replace(/\u0649/g, "\u064A")
    .replace(/\u0629/g, "\u0647")
    .replace(/\s+/g, " ")
    .trim()
    .toLowerCase();
}

/**
 * A rule matches when every group matches; a group matches when any of its
 * alternatives appears in the text. Alternatives are simple substrings of the
 * normalised text.
 */
interface EmergencyRule {
  kind: EmergencyKind;
  all: string[][];
}

const EMERGENCY_RULES: EmergencyRule[] = [
  {
    kind: "chest_pain",
    all: [
      ["صدر", "صدري", "chest"],
      ["ألم", "وجع", "ضغط", "حرقان", "ثقل", "pain", "pressure", "tight", "hurt", "heavy", "squeez"],
    ],
  },
  {
    kind: "breathing",
    all: [
      ["تنفس", "نفس", "breath", "breathe"],
      ["صعوبة", "ضيق", "لا أستطيع", "تقطع", "صعب", "difficult", "trouble", "short", "cannot", "cant", "hard"],
    ],
  },
  {
    kind: "allergic_reaction",
    all: [
      ["تورم", "انتفاخ", "متورم", "swollen", "swelling", "swell"],
      ["وجه", "شفاه", "شفتين", "لسان", "حلق", "face", "lips", "tongue", "throat"],
    ],
  },
  {
    kind: "allergic_reaction",
    all: [["anaphylaxis", "صدمة تحسسية", "تحسس شديد"]],
  },
  {
    kind: "fainting",
    all: [
      [
        "إغماء",
        "أغمى",
        "فقدت الوعي",
        "فقدان الوعي",
        "fainted",
        "fainting",
        "passed out",
        "unconscious",
        "lost consciousness",
      ],
    ],
  },
  {
    kind: "stroke",
    all: [
      ["سكتة", "جلطة", "stroke"],
      ["دماغ", "مخ"],
    ],
  },
  {
    kind: "stroke",
    all: [
      ["ضعف", "تنميل", "تدلي", "slurred", "droop", "numb", "weak"],
      ["وجه", "جانب", "ذراع", "يد", "قدم", "كلام", "لسان", "face", "speech", "arm", "leg"],
    ],
  },
  {
    kind: "bleeding",
    all: [
      ["نزيف", "bleed"],
      ["شديد", "حاد", "غزير", "غزارة", "كثير", "لا يتوقف", "heavy", "severe", "serious", "a lot", "wont stop"],
    ],
  },
  {
    kind: "overdose",
    all: [
      [
        "جرعة زائدة",
        "جرعة كبيرة",
        "زيادة الجرعة",
        "overdose",
        "overdosed",
        "too many pills",
        "took too much",
      ],
    ],
  },
  {
    kind: "self_harm",
    all: [
      [
        "إيذاء النفس",
        "إيذاء نفسي",
        "أؤذي نفسي",
        "أذي نفسي",
        "انتحار",
        "أنتحر",
        "إنهاء حياتي",
        "أنهي حياتي",
        "لا أريد العيش",
        "self harm",
        "self-harm",
        "suicide",
        "suicidal",
        "kill myself",
        "end my life",
        "hurt myself",
        "want to die",
      ],
    ],
  },
];

const PREPARED_RULES: EmergencyRule[] = EMERGENCY_RULES.map((rule) => ({
  kind: rule.kind,
  all: rule.all.map((alternatives) => alternatives.map((alternative) => normalizeForMatching(alternative))),
}));

/** Return the first urgent-symptom category found in the text, if any. */
export function detectEmergency(text: string): EmergencyKind | undefined {
  const haystack = normalizeForMatching(text);
  if (haystack.length === 0) {
    return undefined;
  }
  for (const rule of PREPARED_RULES) {
    const matched = rule.all.every((alternatives) => alternatives.some((alternative) => haystack.includes(alternative)));
    if (matched) {
      return rule.kind;
    }
  }
  return undefined;
}

/**
 * The whole reply when an urgent symptom is detected: a short, calm message
 * pointing to emergency services. It never asks a follow-up question.
 *
 * TODO(clinical-review, T9): confirm the wording and the local emergency number.
 */
export function emergencyReply(emergencyNumber: string): string {
  const number = emergencyNumber.trim().length > 0 ? emergencyNumber.trim() : "112";
  const arabic = `هذه الحالة قد تكون طارئة. اتصل الآن برقم الطوارئ ${number} أو اذهب إلى أقرب قسم طوارئ.`;
  const english = `This may be an emergency. Call ${number} or go to the nearest emergency department now.`;
  return `${arabic}\n\n${english}`;
}
