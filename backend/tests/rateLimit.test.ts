import { describe, expect, it } from "vitest";
import { checkRateLimits, createMemoryCounterStore, type RateLimits } from "../src/limits/rateLimit";

const LIMITS: RateLimits = { perMinute: 3, perDay: 10, globalPerDay: 100 };
const START = Date.UTC(2026, 0, 15, 12, 0, 0);

describe("createMemoryCounterStore", () => {
  it("counts increments and expires after the ttl", async () => {
    let now = START;
    const store = createMemoryCounterStore(() => now);
    expect(await store.increment("k", 60)).toBe(1);
    expect(await store.increment("k", 60)).toBe(2);
    now += 61_000;
    expect(await store.increment("k", 60)).toBe(1);
  });
});

describe("checkRateLimits", () => {
  it("allows requests up to the per-minute limit", async () => {
    const store = createMemoryCounterStore(() => START);
    for (let i = 0; i < LIMITS.perMinute; i += 1) {
      await expect(checkRateLimits(store, "install-a", LIMITS, START)).resolves.toMatchObject({ allowed: true });
    }
  });

  it("blocks the request after the per-minute limit and reports retry seconds", async () => {
    const store = createMemoryCounterStore(() => START);
    for (let i = 0; i < LIMITS.perMinute; i += 1) {
      await checkRateLimits(store, "install-a", LIMITS, START);
    }
    const decision = await checkRateLimits(store, "install-a", LIMITS, START);
    expect(decision).toMatchObject({ allowed: false, scope: "minute" });
    expect(decision.retryAfterSeconds).toBeGreaterThan(0);
    expect(decision.retryAfterSeconds).toBeLessThanOrEqual(60);
  });

  it("recovers after the minute window passes", async () => {
    const store = createMemoryCounterStore(() => START);
    for (let i = 0; i < LIMITS.perMinute; i += 1) {
      await checkRateLimits(store, "install-a", LIMITS, START);
    }
    const nextMinute = START + 60_000;
    await expect(checkRateLimits(store, "install-a", LIMITS, nextMinute)).resolves.toMatchObject({ allowed: true });
  });

  it("enforces the daily per-install cap", async () => {
    let now = START;
    const store = createMemoryCounterStore(() => now);
    const limits: RateLimits = { perMinute: 100, perDay: 2, globalPerDay: 1000 };
    await checkRateLimits(store, "install-b", LIMITS, START);
    await checkRateLimits(store, "install-b", LIMITS, START);
    const decision = await checkRateLimits(store, "install-b", limits, START);
    expect(decision).toMatchObject({ allowed: false, scope: "day" });
  });

  it("enforces the global daily ceiling across installs", async () => {
    const store = createMemoryCounterStore(() => START);
    const limits: RateLimits = { perMinute: 100, perDay: 100, globalPerDay: 1 };
    await expect(checkRateLimits(store, "install-a", limits, START)).resolves.toMatchObject({ allowed: true });
    await expect(checkRateLimits(store, "install-b", limits, START)).resolves.toMatchObject({
      allowed: false,
      scope: "global",
    });
  });

  it("keeps installs independent", async () => {
    const store = createMemoryCounterStore(() => START);
    for (let i = 0; i < LIMITS.perMinute; i += 1) {
      await checkRateLimits(store, "install-a", LIMITS, START);
    }
    await expect(checkRateLimits(store, "install-b", LIMITS, START)).resolves.toMatchObject({ allowed: true });
  });
});
