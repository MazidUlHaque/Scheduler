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
import com.mazidulhaque.kokhon.core.model.EntryStatus
import com.mazidulhaque.kokhon.core.model.EntryType
import com.mazidulhaque.kokhon.core.model.Importance
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
