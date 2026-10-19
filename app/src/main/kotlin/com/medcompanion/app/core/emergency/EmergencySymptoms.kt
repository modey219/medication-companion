package com.medcompanion.app.core.emergency

/**
 * Local emergency keyword check for text the patient types (Arabic and
 * English). It is a safety net, never a diagnosis: when a term matches, the
 * screen shows the emergency card and urges contacting emergency services
 * (AGENTS.md > Medical Safety Rules).
 *
 * Runs on the device only; nothing is sent anywhere.
 *
 * TODO(clinical-review): confirm the term list, the Arabic wording and whether
 * urgent terms are missing or too broad.
 * TODO(eng, T8): reuse this check as the assistant's pre-send guard and make
 * the emergency number configurable in the profile.
 */
object EmergencySymptoms {
    /** Lower-cased fragments; a match on any of them triggers the card. */
    private val KEYWORDS =
        setOf(
            "chest pain",
            "chest tightness",
            "pressure in my chest",
            "shortness of breath",
            "difficulty breathing",
            "cannot breathe",
            "can't breathe",
            "fainting",
            "fainted",
            "passed out",
            "stroke",
            "slurred speech",
            "numbness on one side",
            "severe bleeding",
            "heavy bleeding",
            "anaphylaxis",
            "swollen tongue",
            "swelling in my face",
            "suicide",
            "suicidal",
            "kill myself",
            "self harm",
            "self-harm",
            "harm myself",
            "ألم في الصدر",
            "الم في الصدر",
            "ألم بالصدر",
            "ضيق في النفس",
            "ضيق في التنفس",
            "ضيق نفس",
            "صعوبة في التنفس",
            "صعوبة بالتنفس",
            "لا أستطيع التنفس",
            "إغماء",
            "اغماء",
            "فقدان الوعي",
            "فقدت الوعي",
            "جلطة",
            "سكتة",
            "نزيف",
            "حساسية شديدة",
            "تورم اللسان",
            "تورم في الوجه",
            "انتحار",
            "إيذاء النفس",
            "ايذاء النفس",
        )

    /**
     * True when [text] mentions anything from the urgent list. Matching
     * ignores case, surrounding spaces, Arabic diacritics and the common
     * letter variants, so "ضِيق في التنفّس" still matches.
     */
    fun isEmergencyText(text: String): Boolean {
        val normalized = normalize(text)
        if (normalized.isEmpty()) return false
        return KEYWORDS.any { normalized.contains(it) }
    }

    private fun normalize(text: String): String =
        text
            .lowercase()
            .filterNot { it.code in DIACRITICS || it == '\u0653' }
            .replace('أ', 'ا')
            .replace('إ', 'ا')
            .replace('آ', 'ا')
            .replace('ى', 'ي')
            .replace('ة', 'ه')
            .replace('\u0640', ' ')
            .trim()
            .replace(WHITESPACE, " ")

    /** Arabic short vowels, tanween and shadda. */
    private val DIACRITICS = '\u064B'.code..'\u0652'.code
    private val WHITESPACE = Regex("\\s+")
}
