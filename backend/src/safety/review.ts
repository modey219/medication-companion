import { isUnsafeText } from "./answerGuard";
import { isUrgency, type Urgency } from "./urgency";

/**
 * The structured review the model produces before it is allowed to answer
 * (T14, D-024). The server owns the schema; the model only fills it in.
 *
 * A review that cannot be read is never guessed: the caller fails closed and
 * the patient gets a calm "try again", not a medical answer.
 *
 * TODO(clinical-review, T14): the schema, the bullet caps and the bullet filter
 * are shown to the patient as cards; the reviewer owns the wording rules.
 */
export interface Review {
  urgency: Urgency;
  /** True when the patient asked for a treatment change or a combination verdict. */
  forbiddenRequest: boolean;
  usesProfile: boolean;
  /** Short, patient-safe phrases naming what the review looked at. */
  considered: string[];
  /** Short, patient-safe phrases naming information that is missing. */
  gaps: string[];
  /** Questions the patient could ask a clinician. */
  questions: string[];
}

/** The part of a review that may be shown to the patient (T15 cards). */
export interface PublicReview {
  considered: string[];
  gaps: string[];
  questions: string[];
}

export class ReviewError extends Error {
  readonly code = "llm_invalid_review";

  constructor(message: string) {
    super(message);
    this.name = "ReviewError";
  }
}

const MAX_CONSIDERED = 5;
const MAX_GAPS = 5;
const MAX_QUESTIONS = 3;
const MAX_BULLET = 160;

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

/** Strip an optional markdown fence and isolate the first JSON object. */
function extractJson(raw: string): string {
  const fenced = /```(?:json)?\s*([\s\S]*?)```/i.exec(raw);
  const body = (fenced?.[1] ?? raw).trim();
  const start = body.indexOf("{");
  const end = body.lastIndexOf("}");
  if (start === -1 || end <= start) {
    throw new ReviewError("The review did not contain a JSON object.");
  }
  return body.slice(start, end + 1);
}

function readBullets(value: unknown, max: number): string[] {
  if (!Array.isArray(value)) {
    return [];
  }
  return value
    .filter((entry): entry is string => typeof entry === "string")
    .map((entry) => entry.trim())
    .filter((entry) => entry.length > 0 && entry.length <= MAX_BULLET && !isUnsafeText(entry))
    .slice(0, max);
}

export function parseReview(raw: string): Review {
  let parsed: unknown;
  try {
    parsed = JSON.parse(extractJson(raw)) as unknown;
  } catch (error) {
    if (error instanceof ReviewError) {
      throw error;
    }
    throw new ReviewError("The review was not valid JSON.");
  }
  if (!isRecord(parsed)) {
    throw new ReviewError("The review was not a JSON object.");
  }
  if (!isUrgency(parsed.urgency)) {
    throw new ReviewError("The review has no usable urgency level.");
  }
  if (typeof parsed.forbiddenRequest !== "boolean") {
    throw new ReviewError("The review has no forbiddenRequest flag.");
  }
  return {
    urgency: parsed.urgency,
    forbiddenRequest: parsed.forbiddenRequest,
    usesProfile: parsed.usesProfile === true,
    considered: readBullets(parsed.considered, MAX_CONSIDERED),
    gaps: readBullets(parsed.gaps, MAX_GAPS),
    questions: readBullets(parsed.questions, MAX_QUESTIONS),
  };
}

export function toPublicReview(review: Review): PublicReview {
  return { considered: review.considered, gaps: review.gaps, questions: review.questions };
}

/** The review as the answer pass sees it; always fenced as data. */
export function reviewForPrompt(review: Review): string {
  return JSON.stringify({
    urgency: review.urgency,
    forbiddenRequest: review.forbiddenRequest,
    considered: review.considered,
    gaps: review.gaps,
    questions: review.questions,
  });
}
