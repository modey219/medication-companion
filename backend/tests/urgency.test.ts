import { describe, expect, it } from "vitest";
import { higherUrgency, isUrgency, URGENCIES } from "../src/safety/urgency";

describe("urgency", () => {
  it("accepts only the four known levels", () => {
    for (const level of URGENCIES) {
      expect(isUrgency(level)).toBe(true);
    }
    expect(isUrgency("URGENT")).toBe(false);
    expect(isUrgency("emergency")).toBe(false);
    expect(isUrgency(undefined)).toBe(false);
    expect(isUrgency(3)).toBe(false);
  });

  it("orders the levels from the calmest to the most urgent", () => {
    expect(URGENCIES).toEqual(["SELF_CARE", "SEE_DOCTOR_SOON", "URGENT_TODAY", "EMERGENCY"]);
  });

  it("combines two opinions by taking the safer one", () => {
    expect(higherUrgency("SELF_CARE", "EMERGENCY")).toBe("EMERGENCY");
    expect(higherUrgency("EMERGENCY", "SELF_CARE")).toBe("EMERGENCY");
    expect(higherUrgency("SELF_CARE", "URGENT_TODAY")).toBe("URGENT_TODAY");
    expect(higherUrgency("SEE_DOCTOR_SOON", "URGENT_TODAY")).toBe("URGENT_TODAY");
  });

  it("keeps the level on a tie", () => {
    expect(higherUrgency("SELF_CARE", "SELF_CARE")).toBe("SELF_CARE");
    expect(higherUrgency("URGENT_TODAY", "URGENT_TODAY")).toBe("URGENT_TODAY");
  });
});
