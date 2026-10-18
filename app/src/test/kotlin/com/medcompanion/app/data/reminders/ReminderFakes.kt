package com.medcompanion.app.data.reminders

import com.medcompanion.app.domain.model.DoseEvent
import com.medcompanion.app.domain.model.DoseStatus
import com.medcompanion.app.domain.model.Medication
import com.medcompanion.app.domain.model.MedicationStatus
import com.medcompanion.app.domain.model.Schedule
import com.medcompanion.app.domain.reminder.DoseAlarmScheduler
import com.medcompanion.app.domain.repository.DoseEventRepository
import com.medcompanion.app.domain.repository.MedicationRepository
import com.medcompanion.app.domain.repository.ScheduleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant

/**
 * Small in-memory fakes with real behaviour (no mocking) so the engine test
 * exercises the actual reconcile and catch-up logic. Test-only helpers
 * (`replace`, `clear`) make scenarios readable.
 */
class FakeDoseEventRepository : DoseEventRepository {
    val events = mutableListOf<DoseEvent>()
    private val stream = MutableStateFlow<List<DoseEvent>>(emptyList())
    private var nextId = 1L

    private fun publish() {
        stream.value = events.toList()
    }

    fun replace(event: DoseEvent) {
        val index = events.indexOfFirst { it.id == event.id }
        if (index >= 0) {
            events[index] = event
            publish()
        }
    }

    override fun observeBetween(
        start: Instant,
        end: Instant,
    ): Flow<List<DoseEvent>> = stream

    override fun observeByMedication(medicationId: Long): Flow<List<DoseEvent>> =
        stream.map { list -> list.filter { it.medicationId == medicationId } }

    override fun observeNextAfter(after: Instant): Flow<DoseEvent?> =
        stream.map { list ->
            list
                .filter {
                    it.scheduledAt > after &&
                        (it.status == DoseStatus.PENDING || it.status == DoseStatus.SNOOZED)
                }.minByOrNull { it.scheduledAt }
        }

    override suspend fun getById(id: Long): DoseEvent? = events.firstOrNull { it.id == id }

    override suspend fun getBetween(
        start: Instant,
        end: Instant,
    ): List<DoseEvent> = events.filter { it.scheduledAt >= start && it.scheduledAt < end }

    override suspend fun getByStatusBefore(
        status: DoseStatus,
        cutoff: Instant,
    ): List<DoseEvent> = events.filter { it.status == status && it.scheduledAt <= cutoff }

    override suspend fun addIgnoringDuplicates(event: DoseEvent): Long {
        if (events.any { it.medicationId == event.medicationId && it.scheduledAt == event.scheduledAt }) {
            return -1L
        }
        val stored = event.copy(id = nextId++)
        events += stored
        publish()
        return stored.id
    }

    override suspend fun addAllIgnoringDuplicates(events: List<DoseEvent>): Int =
        events.count { addIgnoringDuplicates(it) != -1L }

    override suspend fun setStatus(
        id: Long,
        status: DoseStatus,
        actedAt: Instant?,
    ) {
        val index = events.indexOfFirst { it.id == id }
        if (index >= 0) {
            events[index] = events[index].copy(status = status, actedAt = actedAt)
            publish()
        }
    }

    override suspend fun update(event: DoseEvent) = replace(event)

    override suspend fun deleteByMedication(medicationId: Long) {
        events.removeAll { it.medicationId == medicationId }
        publish()
    }

    override suspend fun deleteById(id: Long) {
        events.removeAll { it.id == id }
        publish()
    }
}

class FakeMedicationRepository : MedicationRepository {
    val items = mutableListOf<Medication>()
    private val stream = MutableStateFlow<List<Medication>>(emptyList())

    private fun publish() {
        stream.value = items.toList()
    }

    fun put(medication: Medication) {
        items += medication
        publish()
    }

    override fun observeAll(): Flow<List<Medication>> = stream

    override fun observeByStatus(status: MedicationStatus): Flow<List<Medication>> =
        stream.map { list -> list.filter { it.status == status } }

    override suspend fun getById(id: Long): Medication? = items.firstOrNull { it.id == id }

    override suspend fun add(medication: Medication): Long {
        items += medication
        publish()
        return medication.id
    }

    override suspend fun update(medication: Medication) {
        val index = items.indexOfFirst { it.id == medication.id }
        if (index >= 0) {
            items[index] = medication
            publish()
        }
    }

    override suspend fun stop(
        id: Long,
        reason: String?,
        endDate: Instant,
    ) {
        val index = items.indexOfFirst { it.id == id }
        if (index >= 0) {
            items[index] =
                items[index].copy(
                    status = MedicationStatus.STOPPED,
                    stopReason = reason,
                    endDate = endDate,
                )
            publish()
        }
    }

    override suspend fun delete(medication: Medication) {
        items.removeAll { it.id == medication.id }
        publish()
    }
}

class FakeScheduleRepository : ScheduleRepository {
    val items = mutableListOf<Schedule>()
    private val stream = MutableStateFlow<List<Schedule>>(emptyList())

    private fun publish() {
        stream.value = items.toList()
    }

    fun put(schedule: Schedule) {
        items.removeAll { it.medicationId == schedule.medicationId }
        items += schedule
        publish()
    }

    fun replace(schedule: Schedule) = put(schedule)

    override fun observeAll(): Flow<List<Schedule>> = stream

    override fun observeByMedication(medicationId: Long): Flow<List<Schedule>> =
        stream.map { list -> list.filter { it.medicationId == medicationId } }

    override suspend fun getByMedication(medicationId: Long): List<Schedule> =
        items.filter { it.medicationId == medicationId }

    override suspend fun getById(id: Long): Schedule? = items.firstOrNull { it.id == id }

    override suspend fun add(schedule: Schedule): Long {
        items.removeAll { it.medicationId == schedule.medicationId }
        items += schedule
        publish()
        return schedule.id
    }

    override suspend fun update(schedule: Schedule) {
        add(schedule)
    }

    override suspend fun delete(schedule: Schedule) {
        items.removeAll { it.id == schedule.id }
        publish()
    }
}

/** Records what the engine asked the alarm layer to do. */
class FakeDoseAlarmScheduler : DoseAlarmScheduler {
    var canSchedule = true
    val reminders = mutableMapOf<Long, Instant>()
    val graces = mutableMapOf<Long, Instant>()
    val cancelled = mutableListOf<Long>()

    fun clear() {
        reminders.clear()
        graces.clear()
    }

    override fun scheduleReminder(
        doseId: Long,
        triggerAt: Instant,
    ) {
        reminders[doseId] = triggerAt
    }

    override fun scheduleGrace(
        doseId: Long,
        triggerAt: Instant,
    ) {
        graces[doseId] = triggerAt
    }

    override fun cancel(doseId: Long) {
        cancelled += doseId
        reminders.remove(doseId)
        graces.remove(doseId)
    }

    override fun canScheduleExact(): Boolean = canSchedule
}
