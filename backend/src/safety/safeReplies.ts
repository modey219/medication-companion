/**
 * Patient-facing replies for the moments when the assistant must not answer
 * medically (T14). They are bilingual like the emergency reply, never name a
 * medicine or a dose, and never suggest a treatment action.
 *
 * TODO(clinical-review, T14): confirm both sentences with the medical reviewer.
 */

/** Used when the patient asked for a treatment change or a combination verdict. */
export function safeDeclineReply(): string {
  return [
    "لا أستطيع تقديم نصيحة بشأن بدء دواء أو إيقافه أو تغيير جرعته، ولا بشأن سلامة الجمع بين دواءين.",
    "يمكنني مساعدتك في تجهيز أسئلتك، والأفضل أن تسأل طبيبك أو الصيدلي قبل أي تغيير.",
    "",
    "I cannot advise on starting, stopping or changing a medicine or a dose, or on whether two medicines are safe together.",
    "I can help you prepare your questions; please ask your doctor or pharmacist before any change.",
  ].join("\n");
}

/** Used when the generated answer itself failed the safety check. */
export function safeBlockedReply(): string {
  return [
    "تعذّر عليّ صياغة إجابة آمنة لهذا السؤال، فلم أعرضها.",
    "أعد صياغة سؤالك، أو اسأل الصيدلي أو طبيبك مباشرة.",
    "",
    "I could not produce a safe answer to this question, so I did not show one.",
    "Try rephrasing it, or ask your pharmacist or doctor directly.",
  ].join("\n");
}
