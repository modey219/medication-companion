import type {
  ContextEntry,
  ContextMedication,
  PatientContext,
  ProfileSectionStatus,
} from "../validation";

/**
 * Render the validated patient context as a short, plain-text block for the
 * model (T14).
 *
 * The block is built on the server from typed fields, never from the raw JSON
 * the app sent, so a patient-entered value can never open a new "section" or
 * look like a server instruction. Every value is flattened to one line.
 *
 * Missing data is written as missing. A section that was never asked is
 * labelled "not asked", never "none", because absence of data is not evidence
 * of absence (AGENTS.md > Medical Safety Rules).
 *
 * TODO(clinical-review, T14): the section wording, the "not asked" sentence and
 * the truncation marker are read by the model and must not imply absence.
 */
export function renderPatientContext(context: PatientContext, maxChars: number): string {
  const lines: string[] = [];

  if (context.generatedAt !== undefined) {
    lines.push(`context generated at: ${clean(context.generatedAt)}`);
  }
  if (context.ageYears !== undefined) {
    lines.push(`age in years: ${context.ageYears}`);
  }
  if (context.sex !== undefined) {
    lines.push(`sex: ${clean(context.sex)}`);
  }
  if (context.pregnantOrBreastfeeding !== undefined) {
    lines.push(`pregnant or breastfeeding: ${context.pregnantOrBreastfeeding ? "yes" : "no"}`);
  }
  if (context.weightKg !== undefined) {
    lines.push(`weight in kg: ${context.weightKg}`);
  }

  lines.push(...sectionLines("allergies", context.sections.allergies, context.allergies.map(entryLine)));
  lines.push(...sectionLines("chronic conditions", context.sections.conditions, context.conditions.map(entryLine)));
  lines.push(
    ...sectionLines(
      "medical history",
      context.sections.history,
      context.medicalHistory.map((item) => {
        const year = item.year === undefined ? "" : ` | year: ${item.year}`;
        return `- ${clean(item.type)} | ${clean(item.label)}${year}`;
      }),
    ),
  );
  lines.push(...sectionLines("medicines", context.sections.medications, medicationLines(context.medications)));

  const adherence = context.adherenceLast14Days;
  if (adherence !== undefined) {
    lines.push(
      `doses in the last 14 days: ${adherence.taken} taken, ${adherence.missed} missed, ` +
        `${adherence.recorded} recorded in total`,
    );
  }

  if (context.recentSymptoms.length > 0) {
    lines.push("recent symptoms (newest first):");
    for (const symptom of context.recentSymptoms) {
      const at = symptom.loggedAt.length > 0 ? ` | ${clean(symptom.loggedAt)}` : "";
      lines.push(`- ${clean(symptom.name)} | severity ${symptom.severity}/5${at}`);
    }
  }

  if (context.stoppedLast30Days.length > 0) {
    lines.push("medicines stopped in the last 30 days:");
    for (const medication of context.stoppedLast30Days) {
      lines.push(...medicationLines([medication]));
    }
  }

  return cap(lines.join("\n"), maxChars);
}

function sectionLines(title: string, status: ProfileSectionStatus | undefined, entries: string[]): string[] {
  const label = status === undefined ? "UNKNOWN (not provided by the app)" : status;
  const lines = [`section ${title}: ${label}`];
  if (entries.length > 0) {
    lines.push(...entries);
  } else if (status === "NONE_CONFIRMED") {
    lines.push("- the patient confirmed there is nothing to list");
  } else if (status === "LISTED") {
    lines.push("- listed in the app but no entry was included");
  } else if (status === "NOT_ASKED") {
    lines.push("- not asked: the patient has not answered this section yet");
  } else {
    lines.push("- not provided: do not assume anything about this section");
  }
  return lines;
}

function entryLine(entry: ContextEntry): string {
  const parts = [`- ${clean(entry.label)}`];
  if (entry.severity !== undefined) {
    parts.push(`severity ${entry.severity}/3`);
  }
  if (entry.reaction !== undefined) {
    parts.push(`reaction: ${clean(entry.reaction)}`);
  }
  if (entry.sinceYear !== undefined) {
    parts.push(`since ${entry.sinceYear}`);
  }
  return parts.join(" | ");
}

function medicationLines(medications: ContextMedication[]): string[] {
  const lines: string[] = [];
  for (const medication of medications) {
    const strength = medication.strength.length > 0 ? ` ${clean(medication.strength)}` : "";
    const reason = medication.stopReason === undefined ? "" : ` | reason: ${clean(medication.stopReason)}`;
    lines.push(`- ${clean(medication.name)}${strength} | ${clean(medication.status)}${reason}`);
    for (const schedule of medication.schedules) {
      lines.push(`  - ${clean(schedule)}`);
    }
  }
  return lines;
}

/** Flatten any value to one line so it cannot forge a new section. */
function clean(value: string): string {
  return value.replace(/[\u0000-\u001f\u007f]+/g, " ").replace(/\s+/g, " ").trim().slice(0, 200);
}

function cap(text: string, maxChars: number): string {
  if (maxChars <= 0 || text.length <= maxChars) {
    return text;
  }
  const cut = text.slice(0, maxChars);
  const lastNewline = cut.lastIndexOf("\n");
  const body = lastNewline > 0 ? cut.slice(0, lastNewline) : cut;
  return `${body}\n[the rest of the profile was omitted]`;
}
