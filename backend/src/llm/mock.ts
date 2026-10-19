import type { LlmClient, LlmCompleteInput } from "./types";

/** Default, deliberately neutral reply used for local development and tests. */
export const MOCK_REPLY =
  "This is a mock reply for local development. It is not medical advice. I cannot verify this; please ask your pharmacist or doctor.";

/**
 * A deterministic client used when `LLM_PROVIDER=mock` and by the test suite.
 * It never touches the network.
 */
export function createMockClient(reply: string = MOCK_REPLY): LlmClient {
  return {
    complete(input: LlmCompleteInput): Promise<string> {
      const lastUser = [...input.messages].reverse().find((message) => message.role === "user");
      return Promise.resolve(lastUser ? `${reply}` : reply);
    },
  };
}
