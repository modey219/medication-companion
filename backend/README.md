# Medication Companion — Assistant backend

A small, provider-agnostic proxy for the app's **Assistant** tab. The Android app
never holds an LLM API key; it calls this one endpoint instead.

- **Stack**: Cloudflare Workers + TypeScript (bundled with Wrangler).
- **Endpoint**: `POST /v1/chat` (plus `GET /health`).
- **Safety**: applies the Appendix A system prompt from
  [`prompts/system-prompt.txt`](prompts/system-prompt.txt), short-circuits
  urgent symptoms itself without calling the model, and runs a **review pass**
  before every answer (see below).
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
    { "role": "user", "content": "?? ?????? ??? ?????? ?? ???????" }
  ],
  "mode": "personalized",
  "context": {
    "ageYears": 62,
    "allergies": [{ "label": "??????", "severity": 3 }],
    "sections": { "allergies": "LISTED", "conditions": "NOT_ASKED" }
  }
}
```

- `messages`: 1-20 entries, each `user` or `assistant`. A `system` role is
  rejected so a client can never overwrite the safety prompt.
  Each message � 2000 characters.
- `mode`: `generic` (default) or `personalized`. **`generic` sends nothing about
  the patient.** Unknown values are rejected.
- `context`: the structured patient profile, required in `personalized` mode and
  rejected in `generic` mode. Every field is optional except the arrays, which
  default to `[]`; every field is strictly typed and capped (40 allergies or
  conditions, 40 history entries, 60 medicines with 20 schedules each, 20
  symptoms), and the whole object is capped by `MAX_CONTEXT_CHARS`. Unknown keys
  are ignored. `sections` carries `NOT_ASKED` / `NONE_CONFIRMED` / `LISTED` so
  the model can tell "never asked" from "the patient has none".
- **Legacy shape** (an app built before T13): no `mode`, but
  `shareMedications` (default `false`) and `medicationContext` (� 4000 chars,
  only accepted when sharing is on). It is treated as `generic`; once `mode` is
  present the two legacy fields are ignored so the overlap is safe.

### Two passes: review, then answer (T14)

1. **Review** (`prompts/review-prompt.txt`, `LLM_MAX_REVIEW_TOKENS`): the model
   returns JSON only - `urgency` (`EMERGENCY` / `URGENT_TODAY` /
   `SEE_DOCTOR_SOON` / `SELF_CARE`), `forbiddenRequest`, and short
   `considered` / `gaps` / `questions` phrases. Bullets that are too long or
   that contain an instruction are dropped. A review that cannot be parsed
   fails closed with `502 llm_invalid_review`; no answer is attempted.
2. **Gates**: `EMERGENCY` returns the emergency card and skips the answer pass;
   `forbiddenRequest` returns a calm decline and skips it too.
3. **Answer** (`prompts/answer-prompt.txt`): the model answers with the fenced
   profile and the review attached. A last-line guard checks the answer for
   leaked prompt markers, affirmative medicine or dose instructions and
   "safe to combine" claims (negation-aware). A blocked answer is replaced by a
   calm fallback and logged as `chat.answer_blocked` - no content is logged.

Response `200`:

```json
{
  "reply": "...",
  "emergency": false,
  "urgency": "SEE_DOCTOR_SOON",
  "review": { "considered": ["..."], "gaps": [], "questions": ["..."] }
}
```

The T13 app ignores `urgency` and `review`; the T15 app reads them.
When the server detects an urgent symptom by keyword, or the review returns
`EMERGENCY`, it returns the calm emergency message with `"emergency": true`, and
it does **not** call the answer pass.

Structured errors (`{"error":{"code":"...","message":"..."}}`):

| Status | Code (examples) | Meaning |
| --- | --- | --- |
| 400 | `invalid_install_id`, `invalid_json`, `invalid_messages`, `invalid_role`, `message_too_long`, `invalid_mode`, `context_required`, `context_not_allowed`, `invalid_context`, `context_too_large`, `invalid_share_flag`, `medication_context_not_allowed`, `medication_context_too_long` | Bad request; the message is patient-safe and never echoes content |
| 401 | `integrity_failed` | Play Integrity enforcement rejected the request |
| 413 | `payload_too_large` | Body over the configured limit |
| 429 | `rate_limited` | Includes a `Retry-After` header |
| 502 | `llm_http_error`, `llm_unreachable`, `llm_empty_response`, `llm_invalid_review` | Upstream model problem (the review pass fails closed) |
| 504 | `llm_timeout` | Upstream model exceeded the timeout |
| 500 | `internal_error` | Unexpected failure |

Upstream status and the provider's own explanation are **diagnostics only**: the
`chat.llm_failed` log line carries `upstream` (the provider's HTTP status) and
`providerDetail` (the provider's `error.*` fields, single-line, secrets redacted,
capped at 300 characters). Neither is ever returned to the client, and a
non-JSON provider body is reported by content type alone. Read them with
`npx wrangler tail medication-companion` or Workers Logs in the dashboard.

### `GET /health`

Returns `{ "status": "ok" }`.

---

## Configuration

All values are environment variables. Copy `.env.example` to `.env` for
`wrangler dev`; for a deployed Worker set secrets with `wrangler secret put`.

`LLM_PROVIDER`, `LLM_MODEL` and `LLM_BASE_URL` are intentionally **not** in
`wrangler.toml`: they are set on the Worker (dashboard or `wrangler secret
put`), and `keep_vars = true` in the config keeps them across deploys. Without
that, every deploy - including each Workers Builds run - deletes any variable
the config does not declare, which is how a working provider was silently reset
to `mock` during T15 diagnostics.

| Variable | Example | Meaning |
| --- | --- | --- |
| `LLM_PROVIDER` | `openai-compatible` \| `mock` | Which adapter to build |
| `LLM_MODEL` | `REPLACE_ME_MODEL` | Model name passed to the provider |
| `LLM_API_KEY` | *secret* | Provider key; **never committed** |
| `LLM_BASE_URL` | `https://api.openai.com/v1` | Base URL for the OpenAI-compatible adapter |
| `LLM_MAX_OUTPUT_TOKENS` | `512` | Upper bound on the reply length |
| `LLM_MAX_REVIEW_TOKENS` | `400` | Upper bound on the review JSON |
| `LLM_REASONING_EFFORT` | `low` | Optional thinking hint (`minimal` \| `low` \| `medium` \| `high`) for reasoning models; unset sends no field |
| `REQUEST_TIMEOUT_MS` | `45000` | One provider call (review and answer each get this) |
| `MAX_REQUEST_BYTES` | `65536` | Body size limit |
| `MAX_MESSAGE_CHARS` | `2000` | Per-message limit |
| `MAX_MESSAGES` | `20` | Conversation length limit |
| `MAX_MEDICATION_CONTEXT_CHARS` | `4000` | Medication-context limit (legacy shape) |
| `MAX_CONTEXT_CHARS` | `12000` | Limit on the serialized structured context; `0` disables personalized mode |
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
