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
| Repeat reminder cadence (every 5 minutes while unacted, within the 60 minute grace period) | `core/reminder/ReminderDefaults.kt` (`REPEAT_INTERVAL_MINUTES`), `data/reminders/ReminderEngineImpl.kt` | Reminder frequency for the target users; confirm it is helpful rather than distressing, and that repeats stop at the grace boundary | Open |

---

## T6 — Symptoms log (2026-10-19)

| Item | Location | Why flagged | Status |
| --- | --- | --- | --- |
| Severity labels 1 to 5 (`symptom_severity_1` .. `symptom_severity_5`) and `symptom_severity_required` | `values/strings.xml`, `feature/symptoms/SymptomLabels.kt` | The words used for intensity must be clear to elderly patients and must not imply a diagnosis or a decision | Open |
| Log copy: `symptoms_log_title`, `symptom_name_hint`, `symptom_time_hint`, `symptoms_recent_title`, `symptoms_empty`, `symptoms_error_save`, `symptoms_error_delete`, `action_delete` | `values/strings.xml`, `feature/symptoms/SymptomsScreen.kt` | Tone must stay calm and non-judgemental; "delete" must read as correcting a mistake, never as hiding a symptom | Open |
| Emergency card: `emergency_title`, `emergency_body` | `values/strings.xml`, `feature/symptoms/SymptomsScreen.kt` | Urges contacting emergency services immediately; wording and the listed red flags need clinical sign-off | Open |
| Urgent-term list (Arabic + English) and the diacritic/letter-variant normalisation | `core/emergency/EmergencySymptoms.kt` | Must not miss a serious term and must not fire on ordinary symptoms; confirm that "نزيف" alone is acceptable and whether more terms are needed | Open |
| `emergency_number` value (currently an empty placeholder) with `emergency_number_hint` / `emergency_call` | `values/strings.xml`, `feature/symptoms/SymptomsScreen.kt` | Needs the local emergency number for the target country; the call button stays hidden until then (TODO(clinical-review), TODO(legal)) | Open |
| Decision: severity 5 does **not** trigger the emergency card on its own (no triage) | `docs/DECISIONS.md` D-012 point 6 | Confirm that logging severity 5 without emergency wording is acceptable, or specify a reviewed message | Open |

---

## T7 — History timeline, adherence summary and PDF export (2026-10-19)

| Item | Location | Why flagged | Status |
| --- | --- | --- | --- |
| Adherence wording: `history_summary_title`, `history_summary_taken`, `history_summary_missed`, `history_summary_skipped`, `history_summary_pending`, `history_summary_percent`, `history_summary_none` | `values/strings.xml`, `feature/history/HistoryScreen.kt`, `data/export/HistoryPdfExporter.kt` | The summary is the most sensitive text in the app: it must never read as a grade or a judgement about missed doses, and "not recorded yet" must not imply the patient failed | Open |
| Period labels `history_period_7_days` / `history_period_30_days` ("last week" / "last month" covering 7 / 30 days) | `values/strings.xml`, `feature/history/HistoryLabels.kt` | The labels avoid digits on purpose; confirm the wording is not misleading about the actual window | Open |
| Timeline labels: `history_entries_title`, `history_entry_dose`, `history_entry_symptom`, `history_entry_medication_started`, `history_empty` | `values/strings.xml`, `feature/history/HistoryScreen.kt` | Neutral event names only; confirm nothing implies cause or effect between a dose and a symptom | Open |
| Export copy: `history_export`, `history_export_disclaimer`, `history_share_title`, `history_error_export` | `values/strings.xml`, `feature/history/HistoryScreen.kt` | The disclaimer must be true and clear (the file stays on the device until the patient picks an app) | Open |
| PDF content: `history_pdf_title`, `history_pdf_generated`, `history_pdf_period`, `history_pdf_doses_title`, `history_pdf_symptoms_title`, `history_pdf_medications_title`, `history_pdf_note`, `history_pdf_unknown_medication`, `history_pdf_footer` | `values/strings.xml`, `data/export/HistoryPdfExporter.kt` | The footer is the safety notice a doctor or pharmacist reads; it must state plainly that this is patient-entered data and not a diagnosis or a substitute for professional advice | Open |
| Decision: the PDF footer and the summary do not interpret the data, and a dose with an unknown medication is listed as "unknown medication" | `docs/DECISIONS.md` D-013 points 3, 4, 9; `data/export/HistoryPdfExporter.kt` | Confirm no interpretation is expected from the export, and that the fallback wording is acceptable | Open |
| Decision: the percentage is taken/acted and stays empty until a dose was acted on | `docs/DECISIONS.md` D-013 point 4; `domain/history/HistoryReport.kt` | Confirm that an incomplete-but-honest number is preferable to any extrapolation | Open |
| Medication status labels added for the export: `medication_status_active` / `_completed` / `_stopped` | `values/strings.xml`, `feature/medications/MedicationLabels.kt` | Terminology in Arabic for a medication's state; must not imply the patient should start or stop anything | Open |

English mirrors of every row above: `values-en/strings.xml` (same
`TODO(clinical-review)` comments).

---

## T8a — Profile, first-run consent and my data (2026-10-19)

| Item | Location | Why flagged | Status |
| --- | --- | --- | --- |
| First-run consent text: `onboarding_title`, `onboarding_intro`, `onboarding_point_1`..`3`, `onboarding_accept` | `values/strings.xml`, `feature/onboarding/OnboardingScreen.kt` | The consent must state exactly what the app does with the patient's data, in plain language, before anything is recorded; "not a medical device" must be unmistakable | Open |
| Privacy policy placeholder: `privacy_title`, `privacy_placeholder` | `values/strings.xml`, `feature/onboarding/PrivacyPolicyScreen.kt` | Placeholder only (`TODO(legal)`): the reviewed policy must replace it before release | Open |
| Emergency number: `profile_emergency_title`, `profile_emergency_desc`, `profile_emergency_label`, `profile_emergency_saved` | `values/strings.xml`, `feature/profile/ProfileScreen.kt` | The number shown in an emergency is a medical-safety value; it also feeds the Symptoms emergency card | Open |
| Allergy and condition wording: `profile_allergy_title`, `profile_condition_title`, `profile_add_allergy`, `profile_add_condition`, `profile_entry_label_allergy`, `profile_entry_label_condition`, `profile_entry_note`, `profile_empty_list` | `values/strings.xml`, `feature/profile/ProfileScreen.kt` | These fields hold the patient's own clinical facts; the prompts must not suggest a diagnosis or that the app validates their accuracy | Open |
| Delete-all wording: `profile_delete_all`, `profile_delete_confirm_title`, `profile_delete_confirm_body`, `profile_delete_confirmed` | `values/strings.xml`, `feature/profile/ProfileScreen.kt` | The confirmation must be unambiguous that everything is erased and reminders are cancelled, and must not read as a scare tactic | Open |
| Export wording: `profile_export_json`, `profile_export_pdf`, `profile_export_note`, `profile_export_share_title`, `data_pdf_title`, `data_pdf_counts`, `data_pdf_medications_title`, `data_pdf_every_hours` | `values/strings.xml`, `data/export/DataPdfContent.kt`, `data/export/DataJsonExporter.kt` | The exported document is read by a clinician; the wording and the "every N hours" phrasing must be accurate | Open |
| Decision: the export lists what the patient entered and never interprets it; the safety footer is the same as the History export | `docs/DECISIONS.md` D-014 point 4; `data/export/DataPdfContent.kt` | Confirm no interpretation is expected, and that reusing the History footer (including its "not a substitute for professional advice" line) is right for the my-data document | Open |
| Decision: the app language may be changed by the patient (`language_system`, `language_arabic`, `language_english`) | `docs/DECISIONS.md` D-015; `ui/components/LanguageSelector.kt` | The Arabic option is labelled in Arabic and the English one in English, so neither relies on translation; confirm this is not confusing for the elderly target user | Open |

English mirrors of every row above: `values-en/strings.xml` (same
`TODO(clinical-review)` comments).

---

## T1 — Standing notices (2026-10-17)

| Item | Location | Why flagged | Status |
| --- | --- | --- | --- |
| `notice_not_medical_advice` | `values/strings.xml:31` | Core safety notice; also `TODO(legal)` for final wording — clinician + legal sign-off needed | Open |

---

## Not yet written (future tasks — keep this list growing)

- T9/T10: assistant disclaimer, first-use consent text, system prompt rules
  (Appendix A in AGENTS.md).
