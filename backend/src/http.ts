/**
 * Small helpers for structured JSON responses and typed HTTP errors.
 *
 * Error messages are patient-facing and deliberately generic: they never echo
 * request content and never expose internal details or provider payloads.
 */
export class HttpError extends Error {
  readonly status: number;
  readonly code: string;
  readonly extraHeaders: Record<string, string> | undefined;

  constructor(status: number, code: string, message: string, extraHeaders?: Record<string, string>) {
    super(message);
    this.name = "HttpError";
    this.status = status;
    this.code = code;
    this.extraHeaders = extraHeaders;
  }
}

const JSON_HEADERS: Record<string, string> = {
  "content-type": "application/json; charset=utf-8",
  "cache-control": "no-store",
};

export function jsonResponse(body: unknown, status = 200, headers: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(body), { status, headers: { ...JSON_HEADERS, ...headers } });
}

export function errorResponse(error: unknown): Response {
  if (error instanceof HttpError) {
    return jsonResponse(
      { error: { code: error.code, message: error.message } },
      error.status,
      error.extraHeaders ?? {},
    );
  }
  return jsonResponse(
    { error: { code: "internal_error", message: "Something went wrong. Please try again." } },
    500,
  );
}
