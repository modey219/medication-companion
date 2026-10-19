package com.medcompanion.app.domain.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory [SettingsRepository] with the real behaviour the tests depend on:
 * every write flows back to [observe], the numeric values are clamped like the
 * DataStore implementation, and [clear] keeps the consent record.
 */
class FakeSettingsRepository(
    initial: AppSettings = AppSettings(),
) : SettingsRepository {
    private val stream = MutableStateFlow(initial)

    val current: AppSettings
        get() = stream.value

    override fun observe(): Flow<AppSettings> = stream

    override suspend fun setLanguage(language: AppLanguage) {
        stream.value = stream.value.copy(language = language)
    }

    override suspend fun setEmergencyNumber(number: String) {
        stream.value = stream.value.copy(emergencyNumber = number.trim())
    }

    override suspend fun setGraceMinutes(minutes: Long) {
        stream.value = stream.value.copy(graceMinutes = ReminderPreferences.clampGraceMinutes(minutes))
    }

    override suspend fun setSnoozeMinutes(minutes: Long) {
        stream.value = stream.value.copy(snoozeMinutes = ReminderPreferences.clampSnoozeMinutes(minutes))
    }

    override suspend fun setRepeatMinutes(minutes: Long) {
        stream.value = stream.value.copy(repeatMinutes = ReminderPreferences.clampRepeatMinutes(minutes))
    }

    override suspend fun setLowStockThreshold(doses: Int) {
        stream.value = stream.value.copy(lowStockThreshold = ReminderPreferences.clampLowStockThreshold(doses))
    }

    override suspend fun setPharmacyNumber(number: String) {
        stream.value = stream.value.copy(pharmacyNumber = number.trim())
    }

    override suspend fun completeOnboarding() {
        stream.value = stream.value.copy(onboardingCompleted = true)
    }

    override suspend fun clear() {
        stream.value = AppSettings(onboardingCompleted = stream.value.onboardingCompleted)
    }
}
