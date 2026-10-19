import { loadConfig, type Config } from "../src/config";
import type { Env } from "../src/env";

export const INSTALL_ID = "install-1234567890";

/** A configuration with every value defaulted, overridable per test. */
export function testConfig(overrides: Partial<Env> = {}, configOverrides: Partial<Config> = {}): Config {
  return { ...loadConfig({ LLM_PROVIDER: "mock", ...overrides }), ...configOverrides };
}

/**
 * Node's global Request and the Workers Request type diverge, so tests funnel
 * every call through this helper to keep the worker typed as it is in
 * production.
 */
export async function callWorker(
  handler: ExportedHandler<Env>,
  request: Request,
  env: Env = {},
): Promise<Response> {
  const fetchHandler = handler.fetch;
  if (!fetchHandler) {
    throw new Error("handler does not expose fetch");
  }
  type WorkerRequest = Parameters<typeof fetchHandler>[0];
  return fetchHandler(request as unknown as WorkerRequest, env, {} as ExecutionContext);
}

export function chatRequest(body: unknown, headers: Record<string, string> = {}): Request {
  return new Request("https://assistant.example/v1/chat", {
    method: "POST",
    headers: {
      "content-type": "application/json",
      "x-install-id": INSTALL_ID,
      ...headers,
    },
    body: JSON.stringify(body),
  });
}

export function userMessage(content: string): { role: "user"; content: string } {
  return { role: "user", content };
}
