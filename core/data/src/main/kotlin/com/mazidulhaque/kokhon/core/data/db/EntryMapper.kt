package com.mazidulhaque.kokhon.core.data.db

import com.mazidulhaque.kokhon.core.model.Entry
import com.mazidulhaque.kokhon.core.model.EntrySource
import com.mazidulhaque.kokhon.core.model.EntryStatus
import com.mazidulhaque.kokhon.core.model.EntryType
import com.mazidulhaque.kokhon.core.model.Importance
import com.mazidulhaque.kokhon.core.model.ReminderChain
import com.mazidulhaque.kokhon.core.model.ReminderStep
import com.mazidulhaque.kokhon.core.model.ReminderStyle
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant

@Serializable
internal data class StepJson(
    val offsetSeconds: Long,
    val style: String,
    val enabled: Boolean = true,
)

@Serializable
internal data class ChainJson(
    val steps: List<StepJson>,
    val enabled: Boolean = true,
)

private val json = Json { ignoreUnknownKeys = true }
private val peopleSerializer = ListSerializer(String.serializer())

internal fun ReminderChain.toJson(): String =
    json.encodeToString(
        ChainJson.serializer(),
        ChainJson(steps.map { StepJson(it.offset.seconds, it.style.name, it.enabled) }, enabled),
    )

internal fun reminderChainFromJson(value: String): ReminderChain {
    val chain = json.decodeFromString(ChainJson.serializer(), value)
    return ReminderChain(
        steps =
            chain.steps.mapNotNull { step ->
                val style = enumValueOrNull<ReminderStyle>(step.style) ?: return@mapNotNull null
                ReminderStep(Duration.ofSeconds(step.offsetSeconds.coerceAtLeast(0)), style, step.enabled)
            },
        enabled = chain.enabled,
    )
}

private inline fun <reified T : Enum<T>> enumValueOrNull(name: String?): T? =
    enumValues<T>().firstOrNull {
        it.name ==
            name
    }

fun EntryEntity.toModel(): Entry =
    Entry(
        id = id,
        title = title,
        type = enumValueOrNull<EntryType>(type) ?: EntryType.OTHER,
        start = Instant.ofEpochMilli(startMillis),
        duration = durationMinutes?.let(Duration::ofMinutes),
        allDay = allDay,
        isApproximate = approximate,
        location = location,
        notes = notes,
        people = json.decodeFromString(peopleSerializer, peopleJson),
        importance = enumValueOrNull<Importance>(importance) ?: Importance.NORMAL,
        reminders = reminderChainFromJson(remindersJson),
        manualStatus = enumValueOrNull<EntryStatus>(manualStatus),
        source =
            EntrySource(
                kind = enumValueOrNull<EntrySource.Kind>(sourceKind) ?: EntrySource.Kind.MANUAL,
                originalText = sourceText,
                capturedAt = sourceCapturedMillis?.let(Instant::ofEpochMilli),
            ),
    )

fun Entry.toEntity(updated: Instant): EntryEntity =
    EntryEntity(
        id = id,
        title = title,
        type = type.name,
        startMillis = start.toEpochMilli(),
        durationMinutes = duration?.toMinutes(),
        allDay = allDay,
        approximate = isApproximate,
        location = location,
        notes = notes,
        peopleJson = json.encodeToString(peopleSerializer, people),
        importance = importance.name,
        remindersJson = reminders.toJson(),
        manualStatus = manualStatus?.name,
        sourceKind = source.kind.name,
        sourceText = source.originalText,
        sourceCapturedMillis = source.capturedAt?.toEpochMilli(),
        updatedMillis = updated.toEpochMilli(),
    )
