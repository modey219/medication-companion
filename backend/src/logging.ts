/**
 * Content-free structured logging.
 *
 * Hard rule (AGENTS.md > Privacy and security): never log medication names,
 * symptoms, message text or any other personal data. Callers must only pass
 * metadata; this module additionally refuses to log values that look like free
 * text by keeping to a fixed set of primitive fields.
 */
export interface LogFields {
  [key: string]: string | number | boolean | null | undefined;
}

export interface Logger {
  info(event: string, fields?: LogFields): void;
  warn(event: string, fields?: LogFields): void;
  error(event: string, fields?: LogFields): void;
}

const REDACTED_KEYS = new Set(["message", "content", "text", "reply", "prompt", "note", "name", "medication"]);

function sanitize(fields: LogFields | undefined): LogFields {
  if (!fields) {
    return {};
  }
  const safe: LogFields = {};
  for (const [key, value] of Object.entries(fields)) {
    if (REDACTED_KEYS.has(key.toLowerCase())) {
      continue;
    }
    safe[key] = value;
  }
  return safe;
}

export function createConsoleLogger(sink: (line: string) => void = (line) => console.log(line)): Logger {
  const emit = (level: string) => (event: string, fields?: LogFields) => {
    sink(JSON.stringify({ level, event, ...sanitize(fields) }));
  };
  return {
    info: emit("info"),
    warn: emit("warn"),
    error: emit("error"),
  };
}

/**
 * Turn an install identifier into a short, non-reversible tag so logs can be
 * correlated without storing the identifier itself.
 */
export async function pseudonymize(value: string): Promise<string> {
  const bytes = new TextEncoder().encode(value);
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  const hex = Array.from(new Uint8Array(digest))
    .map((byte) => byte.toString(16).padStart(2, "0"))
    .join("");
  return hex.slice(0, 12);
}
