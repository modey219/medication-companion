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

## T4 — Today screen (2026-10-17)

| Item | Location | Why flagged | Status |
| --- | --- | --- | --- |
| Dose status labels: `dose_overdue` (متأخرة), `dose_pending` (مجدولة), `dose_taken` (تمت), `dose_skipped` (تم التخطي), `dose_snoozed` (مؤجلة), `dose_missed` (فاتت) | `values/strings.xml` (`TODO(clinical-review): tone of the dose status labels`) | Tone must stay neutral and never judge the patient for a missed dose; especially "فاتت"/"Missed" and "متأخرة"/"Overdue" | Open |
| `snoozed_until` ("مؤجلة حتى %1$s"), `today_empty`, `error_dose_update` | `values/strings.xml` (T4 section) | Mechanical wording; confirm neutral | Open |

English mirrors of every row above: `values-en/strings.xml` (same
`TODO(clinical-review)` comments).

---

## T5 - Dose reminders (2026-10-18)

| Item | Location | Why flagged | Status |
| --- | --- | --- | --- |
| `notification_channel_reminders_name` / `_desc` | `values/strings.xml:149-150` (`TODO(clinical-review): reminder notification wording`) | First thing a patient sees for a dose; neutral, no urgency beyond "it is time for your dose" | Open |
| `notification_channel_follow_up_name` / `_desc` | `values/strings.xml:151-152` | Must not read as a second nag or as judgement for the missed dose | Open |
| `notification_follow_up_text` | `values/strings.xml:153` (also `values-en/strings.xml`) | The only follow-up text; must stay calm and must not imply the dose is now dangerous or harmless | Open |
| `reminders_setup_title` / `_intro` | `values/strings.xml:156-157` | Explains *why* the app needs these permissions; wording must not promise medical accuracy | Open |
| `reminders_setup_notifications_title` / `_desc`, `reminders_setup_exact_alarm_title` / `_desc`, `reminders_setup_battery_title` / `_desc` | `values/strings.xml:158-163` | Plain-language permission explanations (notifications, exact alarms, background battery); no jargon | Open |
| `reminders_setup_oem_xiaomi` / `_samsung` / `_huawei` / `_oppo` / `_vivo` settings paths | `values/strings.xml:172-176` | Manufacturer-specific Settings paths change between OS versions; verify on real devices before release | Open |
| `reminders_setup_all_set` / `_footer` / `_status_ok` / `_status_missing`, `today_reminders_banner` | `values/strings.xml:164-177` | Tone of "setup complete / not complete" and the Today banner; must never imply the app guarantees a dose is taken | Open |
| Grace period (default 60 min) and snooze (default 10 min) defaults | `core/reminder/ReminderDefaults.kt` (`TODO(eng): make configurable in T8`) | Patient-facing timing defaults; confirm they are sensible for the target users | Open |

English mirrors of every row above: `values-en/strings.xml` (same
`TODO(clinical-review)` comments).

---

## Phase 1 - Lock-screen privacy and medication stock (2026-10-19)

| Item | Location | Why flagged | Status |
| --- | --- | --- | --- |
| `notification_lock_title` ("تذكير بالجرعة" / "Dose reminder") | `values/strings.xml` (`TODO(clinical-review): generic lock-screen wording`) | Only text allowed to appear on a locked screen; must not name the medicine and must stay neutral | Open |
| `reminders_lock_privacy_title` / `_desc` | `values/strings.xml` | Explains that the toggle hides the medicine name; wording must not imply the reminder itself becomes less safe | Open |
| `field_stock_title`, `field_expiry_date`, `field_opened_date`, `field_remaining_doses` + `_hint` | `values/strings.xml` (`TODO(clinical-review): stock/expiry wording`) | Labelling patient-entered expiry/opened dates and remaining quantity; must not imply the app validates the medicine is still safe to use after the expiry date | Open |
| `meds_expiry` / `meds_expired` ("انتهت الصلاحية" / "Expired") | `values/strings.xml` | Stating a medicine is expired is medical information; a clinician must confirm the wording does not suggest what to do about it | Open |
| `meds_remaining` / `meds_low_stock` ("المخزون منخفض — يُفضل طلب إعادة التعبئة") | `values/strings.xml` | The low-stock hint borders on advice to refill; must not read as advice to keep taking or to change treatment | Open |
| `action_refill_order` / `refill_message` (WhatsApp prefill) | `values/strings.xml`, `feature/medications/MedicationsScreen.kt` | Opens WhatsApp with a prefilled refill request to the patient's chosen contact; must stay a request for availability/price, never a prescription or a recommendation | Open |
| Low-stock threshold constant (3 doses) and the rule that taking a dose decrements the tracked quantity | `feature/medications/MedicationsViewModel.kt`, `data/reminders/ReminderEngineImpl.kt` (both marked `TODO(eng) T8`/`TODO(clinical-review)` at the call site) | Patient-facing default; confirm it is sensible and that the decrement never implies the dose must be taken | Open |

---

## T1 — Standing notices (2026-10-17)

| Item | Location | Why flagged | Status |
| --- | --- | --- | --- |
| `notice_not_medical_advice` | `values/strings.xml:31` | Core safety notice; also `TODO(legal)` for final wording — clinician + legal sign-off needed | Open |

---

## Not yet written (future tasks — keep this list growing)

- T6: severity scale (1–5) labels and any guidance text; urgent-symptom
  emergency message and the local emergency number.
- T7: history/adherence summary labels (never shaming language).
- T9/T10: assistant disclaimer, first-use consent text, system prompt rules
  (Appendix A in AGENTS.md).
