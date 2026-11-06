package com.mazidulhaque.kokhon.core.alarm

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.mazidulhaque.kokhon.core.alarm.ui.AlarmActivity
import com.mazidulhaque.kokhon.core.model.ReminderStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration

/**
 * Plays an alarm: full-screen screen, sound that starts quietly and gets louder, vibration.
 * It always stops by itself after the ring limit, so an alarm can never ring forever.
 */
class AlarmService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: MediaPlayer? = null
    private var ringJob: Job? = null
    private val host get() = reminderHost

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val entryId = intent?.getLongExtra(EXTRA_ENTRY_ID, 0) ?: 0
        when (intent?.action) {
            ACTION_START -> {
                // Must enter the foreground within seconds of being started, before any disk access.
                goForeground(placeholderNotification())
                start(
                    entryId = entryId,
                    stepIndex = intent.getIntExtra(EXTRA_STEP_INDEX, 0),
                    snoozeCount = intent.getIntExtra(EXTRA_SNOOZE_COUNT, 0),
                    withSound = intent.getBooleanExtra(EXTRA_WITH_SOUND, true),
                )
            }

            ACTION_SILENCE -> {
                silence()
            }

            ACTION_STOP -> {
                finish()
            }

            ACTION_SNOOZE -> {
                snooze()
            }

            ACTION_AWARE -> {
                aware()
            }

            else -> {
                if (AlarmRingState.current.value == null) stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun start(
        entryId: Long,
        stepIndex: Int,
        snoozeCount: Int,
        withSound: Boolean,
    ) {
        stopRinging()
        ringJob =
            scope.launch {
                val entry = host.entryRepository.get(entryId)
                if (entry == null) {
                    finish()
                    return@launch
                }
                val settings = host.settingsRepository.current()
                val now = host.clock.instant()
                val active =
                    entry.reminders.orderedSteps
                        .withIndex()
                        .filter { it.value.enabled && it.value.style != ReminderStyle.SILENT }
                val position = active.indexOfFirst { it.index == stepIndex }
                val next =
                    active
                        .drop(
                            position + 1,
                        ).map { it.value.fireTime(entry.start) }
                        .firstOrNull { it.isAfter(now) }
                val alarm =
                    RingingAlarm(
                        entryId = entryId,
                        stepIndex = stepIndex,
                        title = entry.title,
                        entryStart = entry.start,
                        isApproximate = entry.isApproximate,
                        importance = entry.importance,
                        stepNumber = (position + 1).coerceAtLeast(1),
                        stepCount = active.size.coerceAtLeast(1),
                        nextReminderAt = next,
                        snoozeCount = snoozeCount,
                        behavior = settings.alarm,
                        quietAt = now.plus(settings.alarm.ringLimit),
                        withSound = withSound,
                    )
                AlarmRingState.set(alarm)
                goForeground(ringingNotification(alarm))
                vibrate()
                if (withSound) playSound(settings.alarm.gentleStart)
                delay(settings.alarm.ringLimit.toMillis())
                // Ring limit reached: go quiet and leave a gentle notification behind.
                ReminderNotifier(this@AlarmService).notifyWentQuiet(entry)
                finish()
            }
    }

    private fun silence() {
        stopSound()
        AlarmRingState.markSilenced()
    }

    private fun snooze() {
        val alarm = AlarmRingState.current.value
        if (alarm != null && alarm.canSnooze) {
            host.alarmScheduler.scheduleSnooze(
                entryId = alarm.entryId,
                stepIndex = alarm.stepIndex,
                style = ReminderStyle.ALARM,
                at = host.clock.instant().plus(alarm.behavior.snoozeLength),
                snoozeCount = alarm.snoozeCount + 1,
            )
        }
        finish()
    }

    private fun aware() {
        val alarm = AlarmRingState.current.value
        stopRinging()
        if (alarm == null) {
            finish()
            return
        }
        scope.launch {
            host.reminderCoordinator.setRemindersEnabled(alarm.entryId, false)
            finish()
        }
    }

    private fun finish() {
        stopRinging()
        AlarmRingState.set(null)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopRinging() {
        ringJob?.cancel()
        ringJob = null
        stopSound()
    }

    private fun stopSound() {
        player?.runCatching {
            stop()
            release()
        }
        player = null
        vibrator()?.cancel()
    }

    private fun playSound(gentleStart: Boolean) {
        val uri =
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: return
        val mediaPlayer =
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(this@AlarmService, uri)
                isLooping = true
            }
        runCatching {
            mediaPlayer.prepare()
            mediaPlayer.setVolume(if (gentleStart) MIN_VOLUME else 1f, if (gentleStart) MIN_VOLUME else 1f)
            mediaPlayer.start()
        }.onFailure {
            mediaPlayer.release()
            return
        }
        player = mediaPlayer
        if (gentleStart) {
            scope.launch {
                val steps = 20
                for (i in 1..steps) {
                    delay(GENTLE_RAMP.toMillis() / steps)
                    val volume = MIN_VOLUME + (1f - MIN_VOLUME) * i / steps
                    player?.setVolume(volume, volume) ?: break
                }
            }
        }
    }

    private fun vibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService<VibratorManager>()?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService<Vibrator>()
        }

    private fun vibrate() {
        val pattern = longArrayOf(0, 600, 400, 600, 1400)
        vibrator()?.vibrate(VibrationEffect.createWaveform(pattern, 0))
    }

    private fun goForeground(notification: Notification) {
        val type =
            if (Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.UPSIDE_DOWN_CAKE
            ) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED
            } else {
                0
            }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun placeholderNotification(): Notification {
        ReminderNotifier.ensureChannels(this)
        return NotificationCompat
            .Builder(this, ReminderNotifier.CHANNEL_ALARMS)
            .setSmallIcon(R.drawable.ic_stat_kokhon)
            .setContentTitle(getString(R.string.alarm_ringing))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .build()
    }

    private fun ringingNotification(alarm: RingingAlarm): Notification {
        val screen =
            PendingIntent.getActivity(
                this,
                NOTIFICATION_ID,
                Intent(
                    this,
                    AlarmActivity::class.java,
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val builder =
            NotificationCompat
                .Builder(this, ReminderNotifier.CHANNEL_ALARMS)
                .setSmallIcon(R.drawable.ic_stat_kokhon)
                .setContentTitle(alarm.title)
                .setContentText(ReminderNotifier.describeStart(this, entryStub(alarm), host.clock.instant()))
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setOngoing(true)
                .setFullScreenIntent(screen, true)
                .setContentIntent(screen)
        if (alarm.canSnooze) {
            builder.addAction(
                0,
                getString(
                    R.string.action_snooze_minutes,
                    alarm.behavior.snoozeLength
                        .toMinutes()
                        .toInt(),
                ),
                serviceIntent(ACTION_SNOOZE),
            )
        }
        builder.addAction(0, getString(R.string.action_stop), serviceIntent(ACTION_STOP))
        return builder.build()
    }

    private fun entryStub(alarm: RingingAlarm) =
        com.mazidulhaque.kokhon.core.model.Entry(
            title = alarm.title,
            type = com.mazidulhaque.kokhon.core.model.EntryType.OTHER,
            start = alarm.entryStart,
            isApproximate = alarm.isApproximate,
        )

    private fun serviceIntent(action: String): PendingIntent =
        PendingIntent.getService(
            this,
            action.hashCode(),
            Intent(this, AlarmService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    override fun onDestroy() {
        stopSound()
        AlarmRingState.set(null)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 0x4B4B
        private const val MIN_VOLUME = 0.15f
        private val GENTLE_RAMP: Duration = Duration.ofSeconds(12)

        const val ACTION_START = "com.mazidulhaque.kokhon.alarm.START"
        const val ACTION_STOP = "com.mazidulhaque.kokhon.alarm.STOP"
        const val ACTION_SNOOZE = "com.mazidulhaque.kokhon.alarm.SNOOZE"
        const val ACTION_SILENCE = "com.mazidulhaque.kokhon.alarm.SILENCE"
        const val ACTION_AWARE = "com.mazidulhaque.kokhon.alarm.AWARE"
        private const val EXTRA_ENTRY_ID = "entry_id"
        private const val EXTRA_STEP_INDEX = "step_index"
        private const val EXTRA_SNOOZE_COUNT = "snooze_count"
        private const val EXTRA_WITH_SOUND = "with_sound"

        fun start(
            context: Context,
            entryId: Long,
            stepIndex: Int,
            snoozeCount: Int,
            withSound: Boolean,
        ) {
            val intent =
                Intent(context, AlarmService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_ENTRY_ID, entryId)
                    .putExtra(EXTRA_STEP_INDEX, stepIndex)
                    .putExtra(EXTRA_SNOOZE_COUNT, snoozeCount)
                    .putExtra(EXTRA_WITH_SOUND, withSound)
            ContextCompat.startForegroundService(context, intent)
        }

        /** Sends a button press from the alarm screen to the service. */
        fun send(
            context: Context,
            action: String,
        ) {
            context.startService(Intent(context, AlarmService::class.java).setAction(action))
        }
    }
}
