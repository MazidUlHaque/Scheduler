package com.mazidulhaque.kokhon.core.alarm

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import com.mazidulhaque.kokhon.core.data.settings.ReminderSettings
import com.mazidulhaque.kokhon.core.model.Entry
import com.mazidulhaque.kokhon.core.model.EntryStatus
import com.mazidulhaque.kokhon.core.model.EntryType
import com.mazidulhaque.kokhon.core.model.Importance
import com.mazidulhaque.kokhon.core.model.ReminderStyle
import com.mazidulhaque.kokhon.core.model.scheduledReminders
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.time.Duration
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(application = TestApp::class, sdk = [35])
class ReminderEngineTest {
    private val app: TestApp = ApplicationProvider.getApplicationContext()
    private val alarmManager get() = app.getSystemService(AlarmManager::class.java)
    private val notificationManager get() = app.getSystemService(NotificationManager::class.java)
    private val start: Instant = Instant.parse("2026-11-07T15:30:00Z")

    @Before
    fun setUp() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun meeting(importance: Importance = Importance.IMPORTANT) =
        Entry(title = "Team meeting", type = EntryType.MEETING, start = start).withImportance(importance)

    @Test
    fun savingAnImportantPlanRegistersThreeAlarmsAtTheRightTimes() =
        runBlocking {
            app.reminderCoordinator.save(meeting())
            val times = shadowOf(alarmManager).scheduledAlarms.map { Instant.ofEpochMilli(it.triggerAtMs) }.sorted()
            assertEquals(listOf(start.minusSeconds(30 * 60), start.minusSeconds(15 * 60), start.minusSeconds(5 * 60)), times)
            // The ringing step uses setAlarmClock, the most reliable path, which also shows the alarm icon.
            assertEquals(start.minusSeconds(5 * 60).toEpochMilli(), alarmManager.nextAlarmClock?.triggerTime)
        }

    @Test
    fun reschedulingReplacesTheOldAlarms() =
        runBlocking {
            val saved = app.reminderCoordinator.save(meeting())
            app.reminderCoordinator.save(saved.rescheduledTo(start.plus(Duration.ofMinutes(30))))
            val times = shadowOf(alarmManager).scheduledAlarms.map { Instant.ofEpochMilli(it.triggerAtMs) }.sorted()
            assertEquals(3, times.size)
            assertEquals(start, times.first())
        }

    @Test
    fun turningRemindersOffCancelsEveryAlarm() =
        runBlocking {
            val saved = app.reminderCoordinator.save(meeting())
            app.reminderCoordinator.setRemindersEnabled(saved.id, false)
            assertTrue(shadowOf(alarmManager).scheduledAlarms.isEmpty())
        }

    @Test
    fun markingDoneOrDeletingCancelsEveryAlarm() =
        runBlocking {
            val done = app.reminderCoordinator.save(meeting())
            app.reminderCoordinator.setStatus(done.id, EntryStatus.COMPLETED)
            assertTrue(shadowOf(alarmManager).scheduledAlarms.isEmpty())

            val deleted = app.reminderCoordinator.save(meeting())
            app.reminderCoordinator.delete(deleted)
            assertTrue(shadowOf(alarmManager).scheduledAlarms.isEmpty())
        }

    @Test
    fun everyChangeIsRecordedInTheHistory() =
        runBlocking {
            val created = app.reminderCoordinator.save(meeting(), sourceText = "meeting tomorrow at 9:30 pm")
            app.reminderCoordinator.save(created.rescheduledTo(start.plus(Duration.ofMinutes(30))), sourceText = "make it 10")
            app.reminderCoordinator.setStatus(created.id, EntryStatus.CANCELLED)
            val history = app.entryRepository.observeHistory(created.id).first()
            assertEquals(
                listOf("Cancelled", "Moved from Sat, 7 Nov, 3:30 PM to Sat, 7 Nov, 4:00 PM", "Created from a message"),
                history.map { it.summary },
            )
            assertEquals("make it 10", history[1].sourceText)
        }

    @Test
    fun aDueNotificationReminderShowsDoneSnoozeAndTurnOffActions() =
        runBlocking {
            val saved = app.reminderCoordinator.save(meeting(Importance.NORMAL))
            val reminder = saved.scheduledReminders(TestApp.NOW).single()
            ReminderNotifier(app).deliver(saved, reminder, snoozeCount = 0, settings = ReminderSettings())

            val posted = shadowOf(notificationManager).allNotifications.single()
            assertEquals(listOf("Done", "Snooze 5 min", "Turn off reminders"), posted.actions.map { it.title.toString() })
            assertEquals(ReminderNotifier.CHANNEL_REMINDERS, posted.channelId)
        }

    @Test
    fun snoozeIsNotOfferedOnceTheLimitIsReached() =
        runBlocking {
            val saved = app.reminderCoordinator.save(meeting(Importance.NORMAL))
            val reminder = saved.scheduledReminders(TestApp.NOW).single()
            ReminderNotifier(app).deliver(saved, reminder, snoozeCount = 3, settings = ReminderSettings())
            val posted = shadowOf(notificationManager).allNotifications.single()
            assertEquals(listOf("Done", "Turn off reminders"), posted.actions.map { it.title.toString() })
        }

    @Test
    fun anAlarmStepStartsTheRingingServiceWithSound() =
        runBlocking {
            val saved = app.reminderCoordinator.save(meeting())
            val alarmStep = saved.scheduledReminders(TestApp.NOW).single { it.style == ReminderStyle.ALARM }
            ReminderNotifier(app).deliver(saved, alarmStep, snoozeCount = 0, settings = ReminderSettings())

            val started = shadowOf(app).nextStartedService
            assertNotNull(started)
            assertEquals(AlarmService::class.java.name, started.component?.className)
            assertTrue(started.getBooleanExtra("with_sound", false))
        }

    @Test
    fun onASilentPhoneTheAlarmOnlyVibrates() =
        runBlocking {
            app.getSystemService(AudioManager::class.java).ringerMode = AudioManager.RINGER_MODE_SILENT
            val saved = app.reminderCoordinator.save(meeting())
            val alarmStep = saved.scheduledReminders(TestApp.NOW).single { it.style == ReminderStyle.ALARM }
            ReminderNotifier(app).deliver(saved, alarmStep, snoozeCount = 0, settings = ReminderSettings())
            assertFalse(shadowOf(app).nextStartedService.getBooleanExtra("with_sound", true))
        }

    @Test
    fun aCriticalPlanMayRingOnSilentWhenTheUserAllowsIt() =
        runBlocking {
            app.getSystemService(AudioManager::class.java).ringerMode = AudioManager.RINGER_MODE_SILENT
            val saved = app.reminderCoordinator.save(meeting(Importance.CRITICAL))
            val alarmStep = saved.scheduledReminders(TestApp.NOW).first { it.style == ReminderStyle.ALARM }
            ReminderNotifier(app).deliver(saved, alarmStep, 0, ReminderSettings(criticalBypassesSilent = true))
            assertTrue(shadowOf(app).nextStartedService.getBooleanExtra("with_sound", false))
        }

    @Test
    fun discreetModeTurnsAnAlarmIntoAVibrateOnlyNotification() =
        runBlocking {
            val saved = app.reminderCoordinator.save(meeting())
            val alarmStep = saved.scheduledReminders(TestApp.NOW).single { it.style == ReminderStyle.ALARM }
            val discreet = ReminderSettings(discreetUntil = TestApp.NOW.plus(Duration.ofHours(1)))
            ReminderNotifier(app).deliver(saved, alarmStep, snoozeCount = 0, settings = discreet)

            assertNull(shadowOf(app).nextStartedService)
            assertEquals(ReminderNotifier.CHANNEL_DISCREET, shadowOf(notificationManager).allNotifications.single().channelId)
        }
}
