# Clinical review — TODO(clinical-review)

Every medical string, explanation, and default value must be reviewed by a
clinician before release (AGENTS.md > Medical Safety Rules). This file is the
running inventory. Format: item, location, why it needs review, status.

Status legend: **Open** (not yet reviewed), **Reviewed** (date + reviewer).

---

## Review rules

- Nothing here diagnoses, recommends starting/stopping/changing a medication,
  or claims a combination is safe. Reviewers should confirm that stays true.
- Mechanical validation strings (form errors) are engineering rules; a reviewer
  only needs to confirm the wording is neutral and non-judgemental.
- Any reviewer change must be mirrored in `values/` (Arabic) **and** `values-en/`
  (English).

---

## T3 — Add/edit/stop medication (2026-10-17)

| Item | Location | Why flagged | Status |
| --- | --- | --- | --- |
| `stop_dialog_title` + `stop_dialog_message` ("لن يظهر ضمن جدول اليوم بعد الإيقاف…") | `values/strings.xml:53-55` (`TODO(clinical-review): tone and wording of the stop explanation`) | Tone around stopping a medication; must stay calm, non-judgemental, and must not imply stopping is safe or unsafe | Open |
| `stop_dialog_reason_label` (free-text stop reason) | `values/strings.xml:56` | Free text the patient may treat as a record; wording should not invite self-diagnosis | Open |
| `field_strength_hint` ("مثال: 500 ملغ") | `values/strings.xml:67-69` (`TODO(clinical-review): example strength wording`) | Example strength units; must not read as a recommended dose | Open |
| `field_instructions_hint` ("مثال: يُؤخذ بعد الأكل") | `values/strings.xml:70-72` (`TODO(clinical-review): example instructions wording`) | Example instruction could be mistaken for general advice | Open |
| `field_dose_amount_hint` ("مثال: قرص واحد") | `values/strings.xml:86-88` (`TODO(clinical-review): example dose amount wording`) | Example dose could be mistaken for a recommended dose | Open |
| `form_tablet` … `form_other` (7 form names) | `values/strings.xml:91-99` (`TODO(clinical-review): form names`) | Terminology accuracy in Arabic (physical forms only) | Open |
| `food_any` / `food_before` / `food_with` / `food_after` | `values/strings.xml:106-111` (`TODO(clinical-review): food relation labels`) | Food-relation phrasing patients rely on for real instructions | Open |
| Validation rule: interval must be 1–24 hours (`error_interval_range`, `ScheduleValidation.MIN/MAX_INTERVAL`) | `domain/schedule/ScheduleValidation.kt`, `values/strings.xml:129` | Engineering bound; confirm it is clinically acceptable (no schedule allowed outside 1..24 h) | Open |
| Form defaults: form `TABLET`, source `SELF`, schedule type `DAILY_TIMES`, food relation `ANY`, dose amount empty | `edit/MedicationEditUiState.kt` | Pre-selected defaults shape what patients enter; confirm none read as a medical suggestion | Open |

English mirrors of every row above: `values-en/strings.xml` (same
`TODO(clinical-review)` comments).

---

## T1 — Standing notices (2026-10-17)

| Item | Location | Why flagged | Status |
| --- | --- | --- | --- |
| `notice_not_medical_advice` | `values/strings.xml:31` | Core safety notice; also `TODO(legal)` for final wording — clinician + legal sign-off needed | Open |

---

## Not yet written (future tasks — keep this list growing)

- T5: missed-dose / follow-up notification wording, grace-period explanation,
  permission explanation screens.
- T6: severity scale (1–5) labels and any guidance text; urgent-symptom
  emergency message and the local emergency number.
- T7: history/adherence summary labels (never shaming language).
- T9/T10: assistant disclaimer, first-use consent text, system prompt rules
  (Appendix A in AGENTS.md).
