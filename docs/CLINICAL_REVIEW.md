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
| Low-stock threshold level (default 3 doses, now patient-changeable 1–10) and the rule that taking a dose decrements the tracked quantity | `domain/settings/ReminderPreferences.kt`, `feature/medications/MedicationsViewModel.kt`, `data/reminders/ReminderEngineImpl.kt` (superseded by the T8b row below) | Patient-facing default; confirm it is sensible and that the decrement never implies the dose must be taken | Open |
| Repeat reminder cadence (default every 5 minutes while unacted, now patient-changeable 5–30, inside the grace period) | `domain/settings/ReminderPreferences.kt`, `data/reminders/ReminderEngineImpl.kt` (superseded by the T8b row below) | Reminder frequency for the target users; confirm it is helpful rather than distressing, and that repeats stop at the grace boundary | Open |

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

## T8b — Patient-editable reminder and stock preferences (2026-10-19)

| Item | Location | Why flagged | Status |
| --- | --- | --- | --- |
| Grace-period wording: `reminders_grace_title`, `reminders_grace_desc` | `values/strings.xml`, `feature/reminders/ReminderSetupScreen.kt` | Describes the time before a dose counts as MISSED; must never read as a deadline the patient failed, and "فائتة"/"missed" must stay factual | Open |
| Snooze wording: `reminders_snooze_title`, `reminders_snooze_desc` | `values/strings.xml`, `feature/reminders/ReminderSetupScreen.kt` | Explains what the Snooze action does; must not imply the delayed dose is now safe or unsafe | Open |
| Repeat-cadence wording: `reminders_repeat_title`, `reminders_repeat_desc` | `values/strings.xml`, `feature/reminders/ReminderSetupScreen.kt` | Explains repeated reminders inside the grace period; frequency for elderly patients needs sign-off | Open |
| Preferences heading and value copy: `reminders_preferences_title`, `reminders_preferences_desc`, `reminders_minutes_value` | `values/strings.xml`, `feature/reminders/ReminderSetupScreen.kt` | Sets expectation that changing these affects future reminders; wording must be plain and calm | Open |
| Reminder ranges and defaults: grace 15–180 (default 60, step 15), snooze 5–60 (default 10, step 5), repeat 5–30 (default 5, step 5) | `domain/settings/ReminderPreferences.kt`, `docs/DECISIONS.md` D-017 | The exact numbers a patient can choose; a clinician must confirm the bounds are safe and useful (e.g. that a 180-minute grace or a 5-minute repeat is acceptable) | Open |
| Stepper descriptions: `action_increase`, `action_decrease` (used for the steppers' TalkBack labels) | `values/strings.xml`, `ui/components/ValueStepper.kt`, `feature/profile/ProfileScreen.kt` | Read aloud by TalkBack as "Increase/Decrease <value>"; confirm the phrasing is understandable without sight of the number | Open |
| Low-stock level wording and range: `profile_stock_title`, `profile_stock_desc`, `profile_low_stock_title`, `profile_low_stock_desc`, `profile_low_stock_value`, default 3, range 1–10 | `values/strings.xml`, `feature/profile/ProfileScreen.kt`, `domain/settings/ReminderPreferences.kt` | Patient sets when the refill hint appears; must not read as advice to stop or continue treatment | Open |
| Pharmacy wording: `profile_pharmacy_title`, `profile_pharmacy_label`, `profile_pharmacy_desc`, `profile_pharmacy_hint`, `profile_pharmacy_saved` | `values/strings.xml`, `feature/profile/ProfileScreen.kt` | The saved number is where the WhatsApp refill request is sent; the copy must make clear it is optional and only used for refill requests | Open |
| Decision: the refill request is a plain availability/price request and never a prescription or a recommendation; the saved number only changes the recipient | `feature/medications/MedicationsScreen.kt` (`openWhatsAppRefill`), `docs/DECISIONS.md` D-017 | Confirm the request wording and the "starts a chat, never sends by itself" behaviour are acceptable | Open |

English mirrors of every row above: `values-en/strings.xml` (same
`TODO(clinical-review)` comments).

---

## T9 — Assistant backend (2026-10-19)

| Item | Location | Why flagged | Status |
| --- | --- | --- | --- |
| Assistant system prompt (Appendix A): every rule and every quoted phrase | `backend/prompts/system-prompt.txt` (`TODO(clinical-review, T9)` in `backend/src/safety/systemPrompt.ts`) | The single most important safety artifact of the assistant; the reviewer owns this file and must confirm each "never do" rule | Open |
| Urgent-symptom keyword list (Arabic and English) and the categories it covers | `backend/src/safety/emergency.ts` (chest pain, breathing, allergic reaction, fainting, stroke, bleeding, overdose, self-harm) | A missed phrase means the assistant answers a possible emergency instead of stopping; a false positive means an unnecessary alarm. Both need clinical sign-off | Open |
| Server-side emergency reply wording (Arabic and English) | `backend/src/safety/emergency.ts` `emergencyReply` (`TODO(clinical-review, T9)`) | The whole reply in an emergency; must be short, calm, non-diagnostic, and must not ask questions | Open |
| Emergency number placeholder (`112`, from `EMERGENCY_NUMBER`) | `backend/.env.example`, `backend/src/config.ts` | Must be replaced with the real local emergency number before release | Open |
| Behaviour: the server answers an urgent symptom itself and never calls the model | `backend/src/app.ts` (`handleChat`) | Confirm that stopping the model entirely is the desired behaviour for every listed symptom | Open |
| Patient-facing error and rate-limit copy returned by the backend | `backend/src/http.ts`, `backend/src/app.ts` (e.g. "Too many requests. Please try again later.") | Shown when the assistant is unavailable or limited; must not read as medical guidance or blame the patient | Open |
| Rate-limit values as a patient experience decision (10/minute, 50/day per install) | `backend/src/config.ts`, `docs/DECISIONS.md` D-018 | Decides how often an elderly or anxious patient may ask; a product/clinical judgement, not only a cost guard | Open |
| Decision: the model receives the medication list only when the patient turns sharing on, and it is passed as fenced data, never as instructions | `backend/src/safety/systemPrompt.ts`, `backend/src/validation.ts`, `docs/DECISIONS.md` D-018 | The privacy and prompt-injection boundary; the reviewer should confirm the wording that labels it as data | Open |

English and Arabic are both present in the prompt file and the emergency reply; the
reviewer must confirm both.

---

## T10 — Assistant chat screen (2026-10-19)

| Item | Location | Why flagged | Status |
| --- | --- | --- | --- |
| First-use consent text (`assistant_consent_*`) | `values/strings.xml`, `values-en/strings.xml` (`TODO(clinical-review)`), rendered by `feature/assistant/AssistantScreen.kt` | The only place the patient learns what leaves the device; it also promises what the assistant will not do | Open |
| Persistent one-line disclaimer (`assistant_disclaimer`) | `values/strings.xml`, `values-en/strings.xml` | Shown above every conversation; must stay short enough to remain visible and must not read as reassurance | Open |
| Client-side urgent-symptom check and the decision to block sending entirely | `core/emergency/EmergencySymptoms.kt`, `feature/assistant/AssistantViewModel.kt` (`onSend`), D-019 | While a term matches, the patient cannot send; confirm this is better than sending the question and showing the card next to the answer | Open |
| Reused emergency card wording in the assistant (`emergency_*`) | `ui/components/EmergencyCard.kt`, `values/strings.xml` (shared with the Symptoms tab, T6) | The same card now appears before a message is sent and when the server flags an emergency; confirm the wording suits both | Open |
| Failure and rate-limit sentences (`assistant_error_*`) | `values/strings.xml`, `values-en/strings.xml`, `feature/assistant/AssistantLabels.kt` | Shown when the network fails; must be calm, blame nobody and never suggest a medical action | Open |
| Medication block field names and the decision to include the patient's instruction text | `data/assistant/AssistantMedicationContextBuilder.kt` (`TODO(clinical-review)`) | Only sent when the patient turns sharing on; confirm the block cannot be read as advice | Open |
| "Report a problem" share template (`assistant_report_template`) | `values/strings.xml`, `values-en/strings.xml` (`TODO(legal)`) | Contains the assistant answer and only leaves the device when the patient taps an app | Open |
| Claim in the consent text that the service does not record what is typed | consent strings, `docs/DECISIONS.md` D-018 | Must stay true of the deployed backend; wording is a legal claim as much as a clinical one | Open |

---

## T1 — Standing notices (2026-10-17)

| Item | Location | Why flagged | Status |
| --- | --- | --- | --- |
| `notice_not_medical_advice` | `values/strings.xml:31` | Core safety notice; also `TODO(legal)` for final wording — clinician + legal sign-off needed | Open |

---

## T12 — Health profile (basic facts, medical history, section state) (2026-10-19)

| Item | Location | Why flagged | Status |
| --- | --- | --- | --- |
| Basic-facts wording: `profile_basics_title`, `profile_basics_desc`, `profile_basics_open`, `profile_basics_summary_empty`, `profile_basics_not_set`, `profile_basics_saved` | `values/strings.xml:388-396`, `feature/profile/ProfileScreen.kt`, `feature/profile/basics/ProfileBasicsScreen.kt` | These fields hold the patient's own clinical facts; the prompts must not suggest a diagnosis or that the app validates their accuracy | Open |
| Birth-year wording and rule: `profile_basics_birth_year`, `_hint` ("مثال: 1960"), `_error`, `_birth_year_value`, `profile_basics_age_value` ("العمر: %1$d سنة"), range 1900–current year | `values/strings.xml:398-403`, `domain/model/ProfileYears.kt`, `domain/model/PatientBasics.kt`, `feature/profile/basics/ProfileBasicsViewModel.kt` | The birth *year* is enough to compute an age for the assistant; a clinician must confirm the accepted range and that showing the computed age is helpful and not misleading | Open |
| Sex wording: `profile_basics_sex`, `profile_sex_female` / `_male` / `_other` | `values/strings.xml:405-408`, `domain/model/Enums.kt` (`Sex`) | Sex can be clinically relevant to the assistant's cautions; the Arabic terms and the "other" option need sign-off | Open |
| Pregnancy/breastfeeding wording: `profile_basics_pregnancy`, `_desc`, `_yes` / `_no` / `_unset` | `values/strings.xml:409-413` | A safety-relevant fact; the copy must make clear it is optional and only asked where relevant, and must not read as advice | Open |
| Weight wording and rule: `profile_basics_weight`, `_error` ("أدخل وزنًا بالكيلوغرام بين %1$d و %2$d"), `_weight_value`, range 20–400 kg | `values/strings.xml:415-417`, `domain/model/PatientBasics.kt` (`WEIGHT_KG_RANGE`), `feature/profile/basics/ProfileBasicsUiState.kt` | Used only as context for the assistant; a clinician must confirm the accepted range and that the app never derives a dose from it | Open |
| Severity scale: `profile_entry_severity`, `_severity_unset`, `profile_severity_mild` / `_moderate` / `_severe`, values 1 mild to 3 severe | `values/strings.xml:423-427`, `domain/model/ProfileEntry.kt` (`SEVERITY_RANGE`), `feature/profile/ProfileLabels.kt` (`severityLabelRes`) | A new 1-to-3 severity scale for allergies and conditions; the words and the scale need clinical sign-off (and must stay distinct from the 1-to-5 symptom scale) | Open |
| Reaction wording: `profile_entry_reaction`, `_reaction_hint` ("مثال: طفح جلدي") | `values/strings.xml:428-429`, `feature/profile/ProfileScreen.kt` | Free text describing what happened; the example must not read as medical guidance | Open |
| Since-year wording: `profile_entry_since_year`, `_since_year_value` ("منذ %1$d"), `_since_year_hint`, `profile_entry_year_error`, and the same for history (`profile_history_year`, `_hint`, `_year_value`, `data_pdf_since_year`, `data_pdf_in_year`) | `values/strings.xml:430-433, 445, 448, 461-464`, `domain/model/ProfileYears.kt` | Dates the patient remembers; wording must not imply the app validates when a condition started | Open |
| Section-state wording: `profile_section_not_asked`, `_none_confirmed`, `_listed`, `_hint`, `_confirm_none`, `_clear_confirmation`, `_confirmed`, `_confirmation_cleared` | `values/strings.xml:418-422`, `domain/model/ProfileSectionState.kt` | This is the "absence of data is not evidence of absence" rule in plain language; a clinician must confirm the patient understands "not asked" is not "nothing there" | Open |
| Medical-history wording: `profile_history_title`, `_desc`, `_open`, `_empty`, `_add`, `_edit`, `_count`, `_label_field`, `_type_field`, `_saved`, `profile_history_type_past_illness` / `_surgery` / `_hospitalization` / `_family` / `_other` | `values/strings.xml:434-452`, `feature/profile/history/MedicalHistoryScreen.kt`, `domain/model/Enums.kt` (`MedicalHistoryType`) | The history categories and their Arabic names; family history in particular must be labelled so it is not read as the patient's own diagnosis | Open |
| Derived section state: only the patient's own "none" is stored, `LISTED` and the medications section are derived, and entries always win over an earlier "none" | `domain/model/ProfileSectionState.kt`, `data/repository/ProfileRepositoryImpl.kt` (`observeSections`), `docs/DECISIONS.md` D-022 points 4 | Confirm that a derived state (never a stored guess) is the right rule for what the assistant is told | Open |
| Export grows with the profile: JSON `schemaVersion` 2 (`basics`, `medicalHistory`, `profileSections`; entries gain `severity`/`reaction`/`sinceYear`) and the PDF basic-facts + medical-history sections | `data/export/DataJsonExporter.kt`, `data/export/DataPdfContent.kt`, `values/strings.xml:452-464` (`data_pdf_basics_*`, `data_pdf_history_*`) | The exported document is read by a clinician; the new sections and their wording must be accurate and must not read as interpretation | Open |
| Decision: the birth year (never a full date of birth) and the sex/weight are collected because the assistant context needs them, and nothing is inferred from missing data | `docs/DECISIONS.md` D-022 points 1, 4 | Privacy/clinical balance of the minimum data set; a clinician should confirm nothing else is needed and nothing collected is unnecessary | Open |

English mirrors of every row above: `values-en/strings.xml` (same
`TODO(clinical-review)` comments).

---

## T13 — Patient context and the two consents (2026-10-20)

| Item | Location | Why flagged | Status |
| --- | --- | --- | --- |
| Profile-consent wording: `assistant_consent_profile_title` / `_desc` | `values/strings.xml`, `values-en/strings.xml` (`TODO(clinical-review)`), rendered by `feature/assistant/AssistantScreen.kt` (`ProfileConsentSwitch`) | The switch hands the assistant the patient's own profile; the copy must say exactly what is sent and must not read as a medical promise | Open |
| Updated medicine-block consent body: `assistant_consent_medicine_body` | `values/strings.xml`, `values-en/strings.xml` | Still the sentence that justifies sending medication names and instruction text; must remain exact after the consent was split in two | Open |
| Generic-mode banner: `assistant_mode_generic` | `values/strings.xml`, `values-en/strings.xml` (`TODO(clinical-review)`), `AssistantScreen.kt` | Tells the patient the answer is *not* personalized; must not imply answers without the profile are less safe | Open |
| Failed-context sentence: `assistant_context_failed` | `values/strings.xml`, `values-en/strings.xml` (`TODO(clinical-review)`), `AssistantScreen.kt`, `AssistantViewModel.kt` (`contextFailed`) | Shown when the profile could not be read and **nothing was sent**; must be calm and must not invite the patient to send anyway | Open |
| Context contents and caps: allergies/conditions (20), history (20), active medications (30), symptoms (10, 14 days), adherence (14 days), stopped medications (30 days) | `data/assistant/PatientContextBuilder.kt`, `docs/DECISIONS.md` D-023 point 4 | The exact minimum data set the assistant may see; a clinician must confirm nothing safety-relevant is missing and nothing identifying is included | Open |
| All-or-nothing personalization: a personalized question is not sent when the context cannot be read | `feature/assistant/AssistantViewModel.kt` (`onSend`, D-023 point 3) | This is the rule that stops the assistant answering generically while the patient believes it has the profile | Open |
| Section status travels with the context (`NOT_ASKED` / `NONE_CONFIRMED` / `LISTED`) | `domain/assistant/PatientContext.kt`, `data/assistant/PatientContextBuilder.kt`, `AssistantPayloads.kt` | The wire carries "we never asked"; the model must never read it as "there is nothing there" (AGENTS.md > Medical Safety Rules) | Open |
| Two versioned consents replace the single sharing flag (profile and media, each with version + timestamp) | `domain/settings/ConsentRecord.kt`, `AppSettings.kt`, `data/settings/DataStoreSettingsRepository.kt`, `docs/DECISIONS.md` D-023 point 1 | Re-consent on a version bump is a privacy decision; confirm it is proportionate for both consents | Open |
| Legacy migration: a patient who had the old sharing flag on is **not** silently made to share the full profile (the flag is dropped) | `data/settings/DataStoreSettingsRepository.kt`, D-023 points 1–2 | Defaulting a broader consent to off is the safe choice; confirm it is the right one for existing installs | Open |
| Compat: T13 keeps sending the legacy `shareMedications`/`medicationContext` until the Worker reads `mode`/`context` (T14) | `domain/assistant/AssistantChat.kt`, `data/assistant/AssistantPayloads.kt` (`TODO(eng, T14)`) | During the overlap the deployed Worker still receives patient data in the old field; confirm it is acceptable until T14 is deployed | Open |

---

## T14 — Review, then answer, on the backend (2026-10-20)

| Item | Location | Why flagged | Status |
| --- | --- | --- | --- |
| Review prompt: the four urgency definitions, the `forbiddenRequest` list, the "when unsure choose the higher level" rule and the "never SELF_CARE for a side effect, a missed or doubled dose, or a change in symptoms" rule | `backend/prompts/review-prompt.txt` (`TODO(clinical-review, T14)` in `backend/src/safety/systemPrompt.ts`) | The urgency level decides whether the app shows an emergency card, a same-day card, or nothing urgent; every definition and every exception needs clinical sign-off | Open |
| Review prompt: the allowed `considered` / `gaps` / `questions` phrases | `backend/prompts/review-prompt.txt`, `backend/src/safety/review.ts` | These are shown to the patient in the T15 cards; confirm they can only ever be descriptive and never a recommendation | Open |
| Answer prompt: "match the review's urgency", the gap sentence, the "unknown / none confirmed" rule, the six-sentence limit and the closing reminder | `backend/prompts/answer-prompt.txt` (`TODO(clinical-review, T14)`) | Shapes every answer that reaches the patient; must not invite self-diagnosis or a treatment change | Open |
| Extended system prompt: the "What you may do" list (possible explanations with uncertainty, urgency, medicine-free self-care, profile cautions, questions for a clinician), the profile rules and the urgency section | `backend/prompts/system-prompt.txt` (T14 additions) | New permissions granted to the model; the reviewer must confirm each one stays inside AGENTS.md and that "possible explanation" cannot read as a diagnosis | Open |
| Calm decline wording (`safeDeclineReply`, Arabic + English) | `backend/src/safety/safeReplies.ts` (`TODO(clinical-review, T14)`) | The whole reply when the patient asks for a treatment change or a combination verdict; must be respectful, non-judgemental and must not hint at an answer | Open |
| Calm fallback wording (`safeBlockedReply`, Arabic + English) | `backend/src/safety/safeReplies.ts` (`TODO(clinical-review, T14)`) | Shown when the answer guard rejects a generated answer; must not reveal that a filter fired, blame the patient, or suggest an action | Open |
| Answer guard: the instruction and combination patterns, the negation list and the 40-character look-behind | `backend/src/safety/answerGuard.ts` (`TODO(clinical-review, T14)`) | A false positive replaces a safe answer; a false negative lets an instruction through. Both directions are clinical calls, and the Arabic patterns need a native reviewer | Open |
| Review bullet filter: a bullet containing an instruction is dropped before it can be shown | `backend/src/safety/review.ts` (`readBullets`, `isUnsafeText`) | The T15 cards must never display a "considered" phrase that is actually advice | Open |
| Profile rendering: `NOT_ASKED` / `NONE_CONFIRMED` / `LISTED` wording, "unknown" for an omitted section, the `[the rest of the profile was omitted]` marker, and one-line flattening | `backend/src/safety/patientContext.ts` | This is the "absence of data is not evidence of absence" rule as the model sees it; a clinician must confirm the wording cannot be read as "the patient has none" | Open |
| Decision: a review that cannot be parsed fails closed with `502 llm_invalid_review` and no answer is attempted | `backend/src/app.ts`, `docs/DECISIONS.md` D-024 point 2 | Patient-facing consequence of a safety failure (a calm "try again"); confirm that showing no answer is preferable to a generic one | Open |
| Gate order: emergency keyword, then review emergency, then forbidden request, then answer; the answer pass is skipped when a gate fires | `backend/src/app.ts`, `docs/DECISIONS.md` D-024 point 3 | Confirm a refusal or an emergency must never be followed by a generated answer | Open |
| Caps: 12 000 characters for the serialized context, 400 tokens for the review, 5/5/3 bullets, 160 characters per bullet | `backend/src/config.ts`, `backend/src/safety/review.ts` | Truncation can hide a clinically relevant entry; confirm the caps and that the truncation marker is enough warning | Open |
| Compat: `mode` present ignores the legacy `shareMedications` / `medicationContext`, and the old shape is read as generic | `backend/src/validation.ts`, `docs/DECISIONS.md` D-024 point 7 | During the overlap a legacy request still carries the old free-text block; confirm it may still be used until T15 ships | Open |

---

## T15 — Urgency line and "what I considered" panel (2026-10-20)

| Item | Location | Why flagged | Status |
| --- | --- | --- | --- |
| The four urgency labels: "حالة طارئة" / "Emergency", "راجع الطبيب اليوم" / "See a doctor today", "راجع الطبيب قريبًا" / "See a doctor soon", "معلومة عامة" / "General information" (`SELF_CARE`) | `app/src/main/res/values/strings.xml`, `app/src/main/res/values-en/strings.xml` (`TODO(clinical-review, T15)`) | They translate a backend safety decision into the patient's words; "General information" in particular must not be read as "no doctor needed" | Open |
| The frame around the level: "مستوى الاستعجال: %1$s" / "Urgency level: %1$s", shown under the answer, never with colour or an icon alone | `assistant_urgency_label`, `AssistantScreen.kt` (`UrgencyLabel`), `docs/DECISIONS.md` D-025 point 2 | The wording must stay descriptive. "Emergency" in particular must not compete with the emergency card, which stays the emergency route (it names the number to call) | Open |
| The panel's open/close wording: "ما أخذته في الحسبان" / "What I considered" and "إخفاء التفاصيل" / "Hide details" | `assistant_review_open`, `assistant_review_close` | Must not claim the assistant verified, checked or approved anything | Open |
| The three group titles: "ما أخذته في الحسبان" / "What I considered", "معلومات ناقصة" / "Missing information", "أسئلة يمكنك سؤال طبيبك أو الصيدلي عنها" / "Questions you can ask your doctor or pharmacist" | `assistant_review_considered_title`, `assistant_review_gaps_title`, `assistant_review_questions_title` | "Missing information" must not blame the patient, and the questions group must not read as instructions to act | Open |
| The bullet template "• %1$s" and the 48dp text button used for the panel toggle | `assistant_review_bullet`, `AssistantScreen.kt` (`ReviewPanel`) | A bulleted list can look like a checklist or a plan; confirm the presentation stays neutral, and check the touch target and TalkBack order at 200% font scale | Open |
| Decision: the app shows only what the server sent — unknown urgency is dropped, a missing or empty review shows no panel, and the level is never computed, upgraded or downgraded on the device | `AssistantPayloads.parseReply`, `AssistantUrgency.fromWire`, `docs/DECISIONS.md` D-025 points 1-3 | A client-side guess would bypass every review rule; confirm that "show nothing" is the right fallback when the Worker is older or the field is missing | Open |
| Decision: the device does not re-filter the review text; it only trims, drops blanks and caps the display at 8 bullets and 300 characters per bullet | `AssistantPayloads.toReview`, `docs/DECISIONS.md` D-025 point 5 | Confirm that the server-side filter (T14 row "Review bullet filter") is sufficient, and that the display cap cannot hide a clinically important phrase | Open |

---

## Assistant prompt polish (2026-10-20)

| Item | Location | Why flagged | Status |
| --- | --- | --- | --- |
| The system prompt no longer asks for a closing "confirm with your doctor or pharmacist" in every reply. It now points at the app's persistent one-line disclaimer and allows one brief clinician mention only when the answer depends on a clinician's judgment | `backend/prompts/system-prompt.txt` (last section, "Closing line") | A repeated reminder is easy to ignore; removing it can make a single answer feel more authoritative. Confirm the remaining cases (side effect, missed or doubled dose, pregnancy or breastfeeding, uncertainty) are the right ones, and that the persistent line under the chat is visible enough | Open |
| The server removes up to two trailing "ask your doctor or pharmacist" sentences from a reply (clinician word + reminder cue required; emergency sentences are never removed; a one-sentence reply is never emptied) | `backend/src/safety/closingReminder.ts`, `backend/src/app.ts` (`chat.reminder_stripped`) | Deleting model text is a safety-relevant action: confirm the cue list, the two-sentence cap, and that no reply where the clinician warning is the only content can lose it | Open |
| The client caps a turn at 2000 characters and sends at most the last 20 messages, starting with the patient's turn | `AssistantViewModel.kt` (`MAX_MESSAGE_CHARS`, `MAX_REQUEST_MESSAGES`, `trimHistory`) | Not medical copy. Listed so review knows older turns are dropped, never summarized, and that the newest question is always included | Open |

---

## Assistant symptom flow (2026-10-20)

| Item | Location | Why flagged | Status |
| --- | --- | --- | --- |
| A symptom with too little detail now gets one empathetic sentence and two to four focused questions before any explanation, advice, home care or medicine; the questions are spread over two or three turns | `backend/prompts/system-prompt.txt` ("Symptom questions"), `backend/prompts/answer-prompt.txt` (step 2) | This is the biggest change in behaviour: the model now withholds information on the first turn. Confirm the question set (place and side, severity 0-10, onset and pattern, injury, swelling or numbness, chronic conditions or allergy, current medicines) is the right clinical minimum, and that asking over several turns is acceptable for an elderly user who may only answer once | Open |
| The symptom caveat sentence ("سلامتك، ما بيك شر إن شاء الله") is given to the model as an example of the opening line | `backend/prompts/system-prompt.txt` ("Symptom questions") | A religiously worded reassurance must be checked for tone and for users who would not want it | Open |
| After the answers, the assistant may give a preliminary idea with explicit uncertainty, medicine-free home care, and the name of the clinician who fits (orthopedist for a foot, internist for a persistent fever) | `backend/prompts/answer-prompt.txt` (step 3) | The home-care examples and the specialist matching are clinical content; confirm they are safe and correct for the listed complaints, and that "safe for anyone" stays true | Open |
| The assistant says once that a pharmacist can advise on over-the-counter options and deliberately names no medicine. The user's request to name common over-the-counter products (e.g. paracetamol) was not implemented | `backend/prompts/system-prompt.txt` ("What you never do"), `docs/DECISIONS.md` D-031 point 4 | This is a contested decision. AGENTS.md and D-021 forbid suggesting a medicine; the user asked for it with a caution sentence. The clinical reviewer must confirm that naming no product is correct for a companion app, or approve a narrow exception with exact wording and guard rules | Open |
| A suspected fracture, a sudden severe pain without the emergency signs, and a high fever that does not settle are URGENT_TODAY (same-day care with the named clinician) and not the emergency card; the emergency list itself is unchanged | `backend/prompts/review-prompt.txt` (URGENT_TODAY), `backend/prompts/answer-prompt.txt` (step 4), `backend/prompts/system-prompt.txt` ("Symptom questions") | Previously the emergency wording covered "a severe sudden pain or a likely fracture". Confirm the new split is right and that nothing life-threatening was moved down a level | Open |
| Greetings, thanks, general and non-medical questions are never refused; "I cannot help with that" is reserved for harmful, illegal or dangerous requests | `backend/prompts/system-prompt.txt` ("What you do"), `backend/prompts/review-prompt.txt` (`SELF_CARE`, `forbiddenRequest`) | Confirm the refusal boundary is described correctly and that nothing in the medical rules became optional | Open |

---
## Not yet written (future tasks — keep this list growing)

- T16-T18: every screen and string up to and including T15 is recorded above.
  New tasks add their rows here before release.
