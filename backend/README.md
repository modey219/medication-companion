# Medication Companion — Assistant backend

A small, provider-agnostic proxy for the app's **Assistant** tab. The Android app
never holds an LLM API key; it calls this one endpoint instead.

- **Stack**: Cloudflare Workers + TypeScript (bundled with Wrangler).
- **Endpoint**: `POST /v1/chat` (plus `GET /health`).
- **Safety**: applies the Appendix A system prompt from
  [`prompts/system-prompt.txt`](prompts/system-prompt.txt) and short-circuits
  urgent symptoms itself, without calling the model.
- **Privacy**: stores no conversations, logs no message content and no identifiers.
- **Abuse protection**: per-install and global rate limits, plus optional Play
  Integrity verification (off by default).

See `docs/DECISIONS.md` D-018 for why these choices were made.

---

## Endpoint contract

### `POST /v1/chat`

Headers:

| Header | Required | Notes |
| --- | --- | --- |
| `content-type: application/json` | yes | |
| `X-Install-Id` | yes | Opaque random id the app generates once, 8–128 chars of `A-Za-z0-9._-` |
| `X-Integrity-Token` | only when `PLAY_INTEGRITY_MODE=enforce` | Play Integrity standard token |

Request body:

```json
{
  "messages": [
    { "role": "user", "content": "هل يمكنني أخذ الدواء مع الطعام؟" }
  ],
  "shareMedications": false,
  "medicationContext": "optional, only accepted when shareMedications is true"
}
```

- `messages`: 1–20 entries, each `user` or `assistant`. A `system` role is
  rejected so a client can never overwrite the safety prompt.
  Each message ≤ 2000 characters.
- `shareMedications`: default `false`. **Off by default**; when off, the app sends
  nothing about the patient's medication.
- `medicationContext`: optional, ≤ 4000 chars, only accepted when sharing is on.
  It is fenced and labelled as data before being added to the system prompt.

Response `200`:

```json
{ "reply": "…", "emergency": false }
```

When the server detects an urgent symptom it returns the calm emergency message
instead, with `"emergency": true` and a `kind` such as `"chest_pain"`, and it does
**not** call the model.

Structured errors (`{"error":{"code":"...","message":"..."}}`):

| Status | Code (examples) | Meaning |
| --- | --- | --- |
| 400 | `invalid_install_id`, `invalid_json`, `invalid_messages`, `invalid_role`, `message_too_long`, `medication_context_not_allowed` | Bad request; the message is patient-safe and never echoes content |
| 401 | `integrity_failed` | Play Integrity enforcement rejected the request |
| 413 | `payload_too_large` | Body over the configured limit |
| 429 | `rate_limited` | Includes a `Retry-After` header |
| 502 | `llm_http_error`, `llm_unreachable`, `llm_empty_response` | Upstream model problem |
| 504 | `llm_timeout` | Upstream model exceeded the timeout |
| 500 | `internal_error` | Unexpected failure |

### `GET /health`

Returns `{ "status": "ok" }`.

---

## Configuration

All values are environment variables. Copy `.env.example` to `.env` for
`wrangler dev`; for a deployed Worker set secrets with `wrangler secret put`.

| Variable | Example | Meaning |
| --- | --- | --- |
| `LLM_PROVIDER` | `openai-compatible` \| `mock` | Which adapter to build |
| `LLM_MODEL` | `REPLACE_ME_MODEL` | Model name passed to the provider |
| `LLM_API_KEY` | *secret* | Provider key; **never committed** |
| `LLM_BASE_URL` | `https://api.openai.com/v1` | Base URL for the OpenAI-compatible adapter |
| `LLM_MAX_OUTPUT_TOKENS` | `512` | Upper bound on the reply length |
| `REQUEST_TIMEOUT_MS` | `20000` | Upstream timeout |
| `MAX_REQUEST_BYTES` | `65536` | Body size limit |
| `MAX_MESSAGE_CHARS` | `2000` | Per-message limit |
| `MAX_MESSAGES` | `20` | Conversation length limit |
| `MAX_MEDICATION_CONTEXT_CHARS` | `4000` | Medication-context limit |
| `RATE_LIMIT_PER_MINUTE` | `10` | Per install, per minute |
| `RATE_LIMIT_PER_DAY` | `50` | Per install, per day |
| `RATE_LIMIT_GLOBAL_PER_DAY` | `2000` | Across all installs, per day |
| `EMERGENCY_NUMBER` | `REPLACE_ME_EMERGENCY_NUMBER` | Shown in the emergency reply — **must be set before release** |
| `PLAY_INTEGRITY_MODE` | `off` \| `enforce` | Verification mode |
| `PLAY_INTEGRITY_PROJECT_NUMBER` | | Google Cloud project number |
| `PLAY_INTEGRITY_PACKAGE_NAME` | `com.medcompanion.app` | Application id |
| `PLAY_INTEGRITY_SERVICE_ACCOUNT_JSON` | *secret* | Service account with the Play Integrity API enabled |

Optional binding: a KV namespace named `RATE_LIMIT_KV` (see `wrangler.toml`). When
it is not bound, the Worker uses an in-memory counter, which is correct for local
development and tests but is not shared across isolates.

---

## Develop, test and deploy

```bash
cd backend
npm install

npm run dev        # local Worker on http://localhost:8787
npm test           # vitest (no network, no Cloudflare account needed)
npm run typecheck  # tsc --noEmit
npm run build      # wrangler deploy --dry-run --outdir dist (validates the bundle)
```

Deploy:

```bash
npx wrangler login
npx wrangler secret put LLM_API_KEY
npx wrangler secret put PLAY_INTEGRITY_SERVICE_ACCOUNT_JSON   # only if enforcing
npx wrangler kv namespace create RATE_LIMIT_KV                # then uncomment the binding
npx wrangler deploy
```

Smoke test:

```bash
curl -s https://<worker-url>/v1/chat \
  -H 'content-type: application/json' \
  -H 'x-install-id: test-install-0001' \
  -d '{"messages":[{"role":"user","content":"متى آخذ دوائي؟"}]}'
```

---

## Safety and privacy rules (do not break these)

- The API key lives **only** here. Never ship it in the app.
- Never log or store message content, medication names or any identifier; logs
  carry metadata only (`src/logging.ts` refuses to log free-text keys).
- The system prompt is Appendix A of `AGENTS.md`, kept verbatim in
  `prompts/system-prompt.txt`. It is medical text and needs clinical review:
  see `docs/CLINICAL_REVIEW.md` (T9 section).
- Urgent symptoms must produce a short, calm emergency reply with the local
  emergency number, without asking further questions (`src/safety/emergency.ts`).
- Play Integrity is an abuse control, not a substitute for the limits; keep it off
  until the app-side token (T10) and the Google Cloud project are ready.

## Layout

```
backend/
├── prompts/system-prompt.txt   # Appendix A, loaded at build time
├── src/
│   ├── app.ts                  # routes + chat orchestration
│   ├── config.ts               # env parsing, defaults, limits
│   ├── env.ts                  # Worker bindings
│   ├── http.ts                 # JSON responses, typed HTTP errors
│   ├── logging.ts              # content-free logging
│   ├── validation.ts           # request validation + install id
│   ├── limits/rateLimit.ts     # KV / in-memory counters
│   ├── llm/                    # provider adapters (openai-compatible, mock)
│   ├── safety/                 # system prompt + emergency detection
│   └── security/integrity.ts   # optional Play Integrity verification
└── tests/                      # vitest suite
```
