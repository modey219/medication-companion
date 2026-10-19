/**
 * Rate limiting for abuse and cost protection (see DECISIONS D-018).
 *
 * Three fixed windows are enforced per request:
 *   - per install, per minute  (burst protection)
 *   - per install, per day     (per-patient daily cap)
 *   - global, per day          (hard cost ceiling across all installs)
 *
 * Counters live in a `CounterStore` so the logic is testable without KV. The
 * production store is Workers KV, which has no atomic increment: worst case, a
 * few concurrent requests can slip past a boundary. That is acceptable here
 * because the limits are soft cost guards, not security boundaries.
 */

export interface CounterStore {
  /** Increment the counter and return its new value. */
  increment(key: string, ttlSeconds: number): Promise<number>;
}

export function createKvCounterStore(kv: KVNamespace): CounterStore {
  return {
    async increment(key: string, ttlSeconds: number): Promise<number> {
      const stored = await kv.get(key);
      const current = Number.parseInt(stored ?? "0", 10);
      const next = (Number.isFinite(current) ? current : 0) + 1;
      await kv.put(key, String(next), { expirationTtl: ttlSeconds });
      return next;
    },
  };
}

export function createMemoryCounterStore(now: () => number = () => Date.now()): CounterStore {
  const counters = new Map<string, { count: number; expiresAt: number }>();
  return {
    increment(key: string, ttlSeconds: number): Promise<number> {
      const nowMs = now();
      const existing = counters.get(key);
      if (!existing || existing.expiresAt <= nowMs) {
        counters.set(key, { count: 1, expiresAt: nowMs + ttlSeconds * 1000 });
        return Promise.resolve(1);
      }
      existing.count += 1;
      return Promise.resolve(existing.count);
    },
  };
}

export interface RateLimits {
  perMinute: number;
  perDay: number;
  globalPerDay: number;
}

export interface RateLimitResult {
  allowed: boolean;
  scope?: "minute" | "day" | "global";
  retryAfterSeconds?: number;
}

const MINUTE_TTL_SECONDS = 120;
const DAY_TTL_SECONDS = 172_800;

function utcDay(nowMs: number): string {
  const date = new Date(nowMs);
  const month = String(date.getUTCMonth() + 1).padStart(2, "0");
  const day = String(date.getUTCDate()).padStart(2, "0");
  return `${date.getUTCFullYear()}${month}${day}`;
}

function secondsUntilUtcMidnight(nowMs: number): number {
  const date = new Date(nowMs);
  const nextMidnight = Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate() + 1, 0, 0, 0);
  return Math.max(1, Math.ceil((nextMidnight - nowMs) / 1000));
}

/**
 * Count the request against every window and report the first one that is over
 * its limit. Coarser windows are only incremented when the finer window passed,
 * so a burst cannot burn the daily budget of a patient.
 */
export async function checkRateLimits(
  store: CounterStore,
  installId: string,
  limits: RateLimits,
  nowMs: number = Date.now(),
): Promise<RateLimitResult> {
  const minuteWindow = Math.floor(nowMs / 60_000);
  const minuteCount = await store.increment(`rl:m:${installId}:${minuteWindow}`, MINUTE_TTL_SECONDS);
  if (minuteCount > limits.perMinute) {
    const secondsIntoMinute = Math.floor((nowMs % 60_000) / 1000);
    return { allowed: false, scope: "minute", retryAfterSeconds: Math.max(1, 60 - secondsIntoMinute) };
  }

  const day = utcDay(nowMs);
  const dayCount = await store.increment(`rl:d:${installId}:${day}`, DAY_TTL_SECONDS);
  if (dayCount > limits.perDay) {
    return { allowed: false, scope: "day", retryAfterSeconds: secondsUntilUtcMidnight(nowMs) };
  }

  const globalCount = await store.increment(`rl:g:${day}`, DAY_TTL_SECONDS);
  if (globalCount > limits.globalPerDay) {
    return { allowed: false, scope: "global", retryAfterSeconds: secondsUntilUtcMidnight(nowMs) };
  }

  return { allowed: true };
}
