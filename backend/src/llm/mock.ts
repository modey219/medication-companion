import type { LlmClient, LlmCompleteInput } from "./types";

/** Default, deliberately neutral reply used for local development and tests. */
export const MOCK_REPLY =
  "This is a mock reply for local development. It is not medical advice. I cannot verify this; please ask your pharmacist or doctor.";

/**
 * The canned review JSON the mock returns for the review pass. It is valid and
 * deliberately empty: local development must never look more certain than a
 * real provider would.
 */
export const MOCK_REVIEW =
  '{"urgency":"SELF_CARE","forbiddenRequest":false,"usesProfile":false,"considered":[],"gaps":[],"questions":[]}';

/**
 * A deterministic client used when `LLM_PROVIDER=mock` and by the test suite.
 * It never touches the network.
 */
export function createMockClient(reply: string = MOCK_REPLY): LlmClient {
  return {
    complete(input: LlmCompleteInput): Promise<string> {
      if (input.purpose === "review") {
        return Promise.resolve(MOCK_REVIEW);
      }
      const lastUser = [...input.messages].reverse().find((message) => message.role === "user");
      return Promise.resolve(lastUser ? `${reply}` : reply);
    },
  };
}
