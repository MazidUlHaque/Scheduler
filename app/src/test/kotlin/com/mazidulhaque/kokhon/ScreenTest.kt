package com.mazidulhaque.kokhon

import android.Manifest
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.mazidulhaque.kokhon.core.alarm.RingingAlarm
import com.mazidulhaque.kokhon.core.alarm.ui.AlarmScreen
import com.mazidulhaque.kokhon.core.designsystem.theme.KokhonTheme
import com.mazidulhaque.kokhon.core.model.AlarmBehavior
import com.mazidulhaque.kokhon.core.model.Entry
import com.mazidulhaque.kokhon.core.model.EntrySource
import com.mazidulhaque.kokhon.core.model.EntryStatus
import com.mazidulhaque.kokhon.core.model.EntryType
import com.mazidulhaque.kokhon.core.model.Importance
import com.mazidulhaque.kokhon.ui.capture.CaptureScreen
import com.mazidulhaque.kokhon.ui.home.HomeScreen
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Renders real screens on the JVM, checks key behaviour, and saves screenshots for review. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-xxhdpi")
class ScreenTest {
    @get:Rule val compose = createComposeRule()

    private val app: KokhonApplication = ApplicationProvider.getApplicationContext()

    private fun save(name: String) {
        val dir = File(System.getProperty("kokhon.screenshots.dir") ?: "build/screenshots").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /**
     * Waits for work that finishes on a background thread (the database) and then hands back to the main thread.
     * Compose's own waitUntil does not run the main looper's queue in that case, so pump it explicitly.
     */
    private fun awaitCondition(condition: () -> Boolean) {
        repeat(100) {
            if (condition()) return
            shadowOf(android.os.Looper.getMainLooper()).idle()
            compose.waitForIdle()
            Thread.sleep(50)
        }
        check(condition()) { "Condition not met within 5 seconds" }
    }

    private fun alarm(snoozeCount: Int = 0) =
        RingingAlarm(
            entryId = 1,
            stepIndex = 2,
            title = "Team meeting",
            entryStart = Instant.now().plus(Duration.ofMinutes(5)),
            isApproximate = true,
            importance = Importance.IMPORTANT,
            stepNumber = 3,
            stepCount = 3,
            nextReminderAt = null,
            snoozeCount = snoozeCount,
            behavior = AlarmBehavior(),
            quietAt = Instant.now().plusSeconds(52),
            withSound = true,
        )

    @Test
    fun alarmScreenOffersClearlyLabelledStopAndSnooze() {
        var stopped = 0
        var snoozed = 0
        compose.setContent {
            KokhonTheme(darkTheme = true) { AlarmScreen(alarm(), onStop = { stopped++ }, onSnooze = { snoozed++ }, onAware = {}) }
        }
        compose.onNodeWithText("Stop").assertIsDisplayed()
        compose.onNodeWithText("Snooze 5 min").assertIsDisplayed()
        compose.onNodeWithText("Ends this alarm. This was the last reminder.").assertIsDisplayed()
        compose.onNodeWithText("I'm aware - turn off the rest for this plan").assertIsDisplayed()
        save("alarm")
        compose.onNodeWithText("Stop").performClick()
        compose.onNodeWithText("Snooze 5 min").performClick()
        assertEquals(1, stopped)
        assertEquals(1, snoozed)
    }

    @Test
    fun snoozeDisappearsWhenNoneAreLeft() {
        compose.setContent {
            KokhonTheme(darkTheme = true) { AlarmScreen(alarm(snoozeCount = 3), onStop = {}, onSnooze = {}, onAware = {}) }
        }
        compose.onNodeWithText("No snoozes left").assertIsDisplayed()
        compose.onNodeWithText("Snooze 5 min").assertDoesNotExist()
    }

    @Test
    fun homeShowsTheDayAsATimeline() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)

        fun at(
            day: LocalDate,
            hour: Int,
            minute: Int = 0,
        ) = day.atTime(hour, minute).atZone(zone).toInstant()
        runBlocking {
            val c = app.reminderCoordinator
            c.save(
                Entry(title = "Morning standup", type = EntryType.MEETING, start = at(today, 0, 5), manualStatus = EntryStatus.COMPLETED),
            )
            c.save(Entry(title = "Dinner with Nabila", type = EntryType.DINNER_DATE, start = at(today, 23, 30), location = "Gulshan 2"))
            c.save(Entry(title = "Bangladesh vs Sri Lanka", type = EntryType.MATCH, start = at(today.plusDays(1), 14)))
            c.save(Entry(title = "Team meeting", type = EntryType.MEETING, start = at(today.plusDays(1), 21, 30), isApproximate = true))
            c.save(Entry(title = "Send budget sheet", type = EntryType.TASK, start = at(today.plusDays(2), 11)))
        }
        compose.setContent { KokhonTheme { HomeScreen(onOpenEntry = {}, onAddPlan = {}, onOpenSettings = {}) } }
        compose.waitUntil(5_000) {
            compose
                .onAllNodes(
                    androidx.compose.ui.test
                        .hasText("Team meeting"),
                ).fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithText("Now").assertIsDisplayed()
        compose.onNodeWithText("Add a plan").assertIsDisplayed()
        save("home")
    }

    @Test
    fun aPastedMessageBecomesAPlanInOneTap() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        var opened: Long? = null
        compose.setContent {
            KokhonTheme {
                CaptureScreen(
                    initialText = "let's try to sit for a meeting tomorrow around 9:30 pm",
                    source = EntrySource.Kind.PASTED_TEXT,
                    targetId = 0,
                    onClose = {},
                    onOpenEntry = { opened = it },
                    onGoHome = {},
                    onEditDraft = {},
                    onEditEntry = {},
                )
            }
        }
        compose.waitUntil(5_000) {
            compose
                .onAllNodes(
                    androidx.compose.ui.test
                        .hasText("Save plan"),
                ).fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithText("I found a plan in this message").assertIsDisplayed()
        save("capture")
        compose.onNodeWithText("Save plan").performClick()
        awaitCondition { opened != null }
        val saved = runBlocking { app.entryRepository.get(opened!!) }!!
        val zone = ZoneId.systemDefault()
        assertEquals(
            LocalDate
                .now(zone)
                .plusDays(1)
                .atTime(21, 30)
                .atZone(zone)
                .toInstant(),
            saved.start,
        )
        assertEquals(EntryType.MEETING, saved.type)
        assertEquals(true, saved.isApproximate)
        assertEquals("let's try to sit for a meeting tomorrow around 9:30 pm", saved.source.originalText)
    }

    @Test
    fun aChangeMessageOffersToMoveTheExistingPlan() {
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val zone = ZoneId.systemDefault()
        val tomorrow = LocalDate.now(zone).plusDays(1)
        val meeting =
            runBlocking {
                app.reminderCoordinator.save(
                    Entry(title = "Team meeting", type = EntryType.MEETING, start = tomorrow.atTime(21, 30).atZone(zone).toInstant())
                        .withImportance(Importance.IMPORTANT),
                )
            }
        runBlocking {
            app.reminderCoordinator.save(
                Entry(
                    title = "Client meeting",
                    type = EntryType.MEETING,
                    start =
                        tomorrow
                            .plusDays(1)
                            .atTime(11, 0)
                            .atZone(zone)
                            .toInstant(),
                ),
            )
        }
        var opened: Long? = null
        compose.setContent {
            KokhonTheme {
                CaptureScreen(
                    initialText = "guys let's shift tomorrow's meeting to 10 pm instead",
                    source = EntrySource.Kind.PASTED_TEXT,
                    targetId = 0,
                    onClose = {},
                    onOpenEntry = { opened = it },
                    onGoHome = {},
                    onEditDraft = {},
                    onEditEntry = {},
                )
            }
        }
        compose.waitUntil(5_000) {
            compose
                .onAllNodes(
                    androidx.compose.ui.test
                        .hasText("Yes, move it"),
                ).fetchSemanticsNodes()
                .isNotEmpty()
        }
        compose.onNodeWithText("Move Team meeting to 10:00 PM?").assertIsDisplayed()
        save("update")
        compose.onNodeWithText("Yes, move it").performClick()
        awaitCondition { opened != null }
        assertEquals(meeting.id, opened)
        val moved = runBlocking { app.entryRepository.get(meeting.id) }!!
        assertEquals(tomorrow.atTime(22, 0).atZone(zone).toInstant(), moved.start)
    }

    @Test
    fun emptyHomeExplainsWhatToDo() {
        compose.setContent { KokhonTheme { HomeScreen(onOpenEntry = {}, onAddPlan = {}, onOpenSettings = {}) } }
        compose.waitUntil(
            5_000,
        ) {
            compose
                .onAllNodes(
                    androidx.compose.ui.test
                        .hasText("Your day is clear"),
                ).fetchSemanticsNodes()
                .isNotEmpty()
        }
        save("home-empty")
    }
}
