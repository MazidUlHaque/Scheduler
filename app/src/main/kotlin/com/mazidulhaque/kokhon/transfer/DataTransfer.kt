package com.mazidulhaque.kokhon.transfer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.FileProvider
import com.mazidulhaque.kokhon.KokhonApplication
import com.mazidulhaque.kokhon.core.data.backup.Backup
import com.mazidulhaque.kokhon.core.model.Entry
import com.mazidulhaque.kokhon.core.model.duplicateIn
import com.mazidulhaque.kokhon.core.model.ics.Ics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Duration
import java.time.Instant

/** Moving plans in and out: calendar files (.ics) for other calendar apps, and full backups for a new phone. */
class DataTransfer(
    private val app: KokhonApplication,
) {
    data class ImportResult(
        val added: Int,
        val skipped: Int,
    )

    private suspend fun allPlans(): List<Entry> = app.entryRepository.startingFrom(Instant.EPOCH)

    suspend fun exportCalendar(uri: Uri): Int {
        // Upcoming plans and the last month, which is what other calendars usually need.
        val from = app.clock.instant().minus(Duration.ofDays(31))
        val plans = allPlans().filter { !it.start.isBefore(from) }
        write(uri, Ics.write(plans, app.clock.instant(), app.clock.zone))
        return plans.size
    }

    suspend fun importCalendar(uri: Uri): ImportResult = add(Ics.read(read(uri), app.clock.zone))

    suspend fun backup(uri: Uri): Int {
        val plans = allPlans()
        write(uri, Backup.encode(plans, app.settingsRepository.current().presets, app.clock.instant()))
        return plans.size
    }

    suspend fun restore(uri: Uri): ImportResult {
        val restored = Backup.decode(read(uri))
        restored.presets.forEach { app.settingsRepository.savePreset(it) }
        return add(restored.entries)
    }

    /** Adds plans that are not already there (same message or same name at the same time). */
    private suspend fun add(plans: List<Entry>): ImportResult {
        val existing = allPlans().toMutableList()
        var added = 0
        plans.forEach { plan ->
            if (plan.duplicateIn(existing) == null) {
                existing += app.reminderCoordinator.save(plan.copy(id = 0))
                added++
            }
        }
        return ImportResult(added, plans.size - added)
    }

    private suspend fun read(uri: Uri): String =
        withContext(Dispatchers.IO) {
            requireNotNull(
                app.contentResolver.openInputStream(uri),
            ) { "Can't open the file" }.use { it.readBytes().toString(Charsets.UTF_8) }
        }

    private suspend fun write(
        uri: Uri,
        text: String,
    ) = withContext(Dispatchers.IO) {
        requireNotNull(app.contentResolver.openOutputStream(uri, "wt")) {
            "Can't write the file"
        }.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    companion object {
        /** Opens the phone's calendar app with the plan filled in. No calendar permission is needed. */
        fun addToPhoneCalendar(
            context: Context,
            entry: Entry,
        ): Boolean {
            val end = entry.start.plus(entry.duration?.takeIf { !it.isZero && !it.isNegative } ?: Duration.ofMinutes(30))
            val intent =
                Intent(Intent.ACTION_INSERT)
                    .setData(CalendarContract.Events.CONTENT_URI)
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, entry.start.toEpochMilli())
                    .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end.toEpochMilli())
                    .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, entry.allDay)
                    .putExtra(CalendarContract.Events.TITLE, entry.title)
                    .putExtra(CalendarContract.Events.EVENT_LOCATION, entry.location)
                    .putExtra(CalendarContract.Events.DESCRIPTION, entry.notes)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return runCatching { context.startActivity(intent) }.isSuccess
        }

        /** Shares one plan as a calendar invite (.ics) through any app: WhatsApp, email, Drive. */
        fun shareAsCalendarFile(
            context: Context,
            entry: Entry,
            now: Instant,
            zone: java.time.ZoneId,
        ) {
            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            val safeName =
                entry.title
                    .replace(Regex("[^\\p{L}\\p{N} _-]"), "")
                    .trim()
                    .ifBlank { "plan" }
                    .take(40)
            val file = File(dir, "$safeName.ics").apply { writeText(Ics.write(listOf(entry), now, zone)) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            val send =
                Intent(Intent.ACTION_SEND)
                    .setType("text/calendar")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .putExtra(Intent.EXTRA_SUBJECT, entry.title)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(send, entry.title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
