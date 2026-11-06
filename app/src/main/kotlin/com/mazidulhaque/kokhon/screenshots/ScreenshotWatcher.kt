package com.mazidulhaque.kokhon.screenshots

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.mazidulhaque.kokhon.KokhonApplication
import com.mazidulhaque.kokhon.MainActivity
import com.mazidulhaque.kokhon.R
import com.mazidulhaque.kokhon.core.alarm.ReminderNotifier
import com.mazidulhaque.kokhon.core.designsystem.format.TimeFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/**
 * Optional: notices new screenshots and, only if one contains a plan, offers to make it a plan.
 * Everything happens on the phone. Uses a content-triggered job, so nothing runs in the background otherwise.
 */
object ScreenshotWatcher {
    /** Outside WorkManager's job id range (see KokhonApplication). */
    private const val JOB_ID = 42
    const val CHANNEL = "screenshot_suggestions"

    fun permission(): String =
        if (Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, permission()) == PackageManager.PERMISSION_GRANTED

    /** Android 14+: the user shared only some photos, so new screenshots stay invisible to Kokhon. */
    fun hasOnlySelectedPhotos(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            !hasPermission(context) &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) ==
            PackageManager.PERMISSION_GRANTED

    /** Starts or stops watching. Watching only starts when photo access was granted. */
    fun update(
        context: Context,
        enabled: Boolean,
    ) {
        val scheduler = context.getSystemService<JobScheduler>() ?: return
        if (!enabled || !hasPermission(context)) {
            scheduler.cancel(JOB_ID)
            return
        }
        val job =
            JobInfo
                .Builder(JOB_ID, ComponentName(context, ScreenshotJobService::class.java))
                .addTriggerContentUri(
                    JobInfo.TriggerContentUri(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS,
                    ),
                ).setTriggerContentUpdateDelay(1_000)
                .setTriggerContentMaxDelay(5_000)
                .build()
        scheduler.schedule(job)
    }

    internal fun isRecentScreenshot(
        context: Context,
        uri: Uri,
        now: Instant,
    ): Boolean {
        val columns =
            arrayOf(MediaStore.MediaColumns.DATE_ADDED, MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.DISPLAY_NAME)
        return runCatching {
            context.contentResolver.query(uri, columns, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use false
                val added = Instant.ofEpochSecond(cursor.getLong(0))
                val place = (cursor.getString(1).orEmpty() + "/" + cursor.getString(2).orEmpty()).lowercase()
                place.contains("screenshot") && Duration.between(added, now) < Duration.ofMinutes(2)
            } ?: false
        }.getOrDefault(false)
    }
}

class ScreenshotJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onStartJob(params: JobParameters): Boolean {
        val app = application as KokhonApplication
        val uris = params.triggeredContentUris?.toList().orEmpty()
        scope.launch {
            try {
                val now = app.clock.instant()
                uris.filter { ScreenshotWatcher.isRecentScreenshot(this@ScreenshotJobService, it, now) }.take(3).forEach { uri ->
                    runCatching { suggest(app, uri) }
                }
            } finally {
                // Content triggers fire once; watch again for the next screenshot.
                ScreenshotWatcher.update(this@ScreenshotJobService, app.settingsRepository.current().watchScreenshots)
                jobFinished(params, false)
            }
        }
        return true
    }

    private suspend fun suggest(
        app: KokhonApplication,
        uri: Uri,
    ) {
        val message = app.screenshotReader.read(uri)
        if (message.text.isBlank()) return
        val result = app.planParser.parse(message.text, message.reference.atZone(app.clock.zone))
        val draft = result.drafts.firstOrNull()
        val summary =
            when {
                draft != null -> {
                    val start =
                        draft.date
                            .atTime(draft.time)
                            .atZone(app.clock.zone)
                            .toInstant()
                    draft.title + " · " + TimeFormat.shortDate(start) + ", " + TimeFormat.time(this, start)
                }

                result.change != null -> {
                    getString(R.string.screenshot_change_found)
                }

                else -> {
                    return
                }
            }
        if (!ReminderNotifier.canPostNotifications(this)) return
        val manager = getSystemService<NotificationManager>() ?: return
        manager.createNotificationChannel(
            NotificationChannel(ScreenshotWatcher.CHANNEL, getString(R.string.screenshot_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open =
            PendingIntent.getActivity(
                this,
                uri.hashCode(),
                Intent(this, MainActivity::class.java)
                    .setAction(MainActivity.ACTION_OPEN_IMAGE)
                    .putExtra(MainActivity.EXTRA_IMAGE_URI, uri.toString())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val notification =
            NotificationCompat
                .Builder(this, ScreenshotWatcher.CHANNEL)
                .setSmallIcon(com.mazidulhaque.kokhon.core.alarm.R.drawable.ic_stat_kokhon)
                .setContentTitle(getString(R.string.screenshot_plan_found))
                .setContentText(summary)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
        @Suppress("MissingPermission") // checked by canPostNotifications
        NotificationManagerCompat.from(this).notify(uri.hashCode(), notification)
    }

    override fun onStopJob(params: JobParameters): Boolean = true
}
