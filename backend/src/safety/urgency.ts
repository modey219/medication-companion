/**
 * The urgency vocabulary of the assistant (T14, AGENTS.md > T12-T18 rules).
 *
 * The review pass picks one level; the answer pass must stay consistent with
 * it. Levels are ordered so two opinions can be combined by taking the safer
 * (higher) one, never the lower.
 */
export type Urgency = "EMERGENCY" | "URGENT_TODAY" | "SEE_DOCTOR_SOON" | "SELF_CARE";

export const URGENCIES: readonly Urgency[] = ["SELF_CARE", "SEE_DOCTOR_SOON", "URGENT_TODAY", "EMERGENCY"];

const RANK: Record<Urgency, number> = {
  SELF_CARE: 0,
  SEE_DOCTOR_SOON: 1,
  URGENT_TODAY: 2,
  EMERGENCY: 3,
};

export function isUrgency(value: unknown): value is Urgency {
  return typeof value === "string" && (URGENCIES as readonly string[]).includes(value);
}

/** The safer of two levels; a tie keeps the same level. */
export function higherUrgency(left: Urgency, right: Urgency): Urgency {
  return RANK[right] > RANK[left] ? right : left;
}
