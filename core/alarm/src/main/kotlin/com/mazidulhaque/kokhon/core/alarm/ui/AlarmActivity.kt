package com.mazidulhaque.kokhon.core.alarm.ui

import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mazidulhaque.kokhon.core.alarm.AlarmRingState
import com.mazidulhaque.kokhon.core.alarm.AlarmService
import com.mazidulhaque.kokhon.core.designsystem.theme.KokhonTheme

/** The full-screen alarm. Shown over the lock screen and turns the screen on. */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O_MR1) {
            // The manifest attributes only work from Android 8.1; Android 8.0 needs the window flags.
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        // Back must not silently dismiss an alarm: the user chooses Stop or Snooze deliberately.
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = Unit
            },
        )
        setContent {
            val alarm by AlarmRingState.current.collectAsStateWithLifecycle()
            LaunchedEffect(alarm == null) {
                if (alarm == null) finish()
            }
            KokhonTheme(darkTheme = true) {
                alarm?.let {
                    AlarmScreen(
                        alarm = it,
                        onStop = { AlarmService.send(this, AlarmService.ACTION_STOP) },
                        onSnooze = { AlarmService.send(this, AlarmService.ACTION_SNOOZE) },
                        onAware = { AlarmService.send(this, AlarmService.ACTION_AWARE) },
                    )
                }
            }
        }
    }

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent?,
    ): Boolean {
        val alarm = AlarmRingState.current.value
        val isVolumeKey =
            keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
                keyCode == KeyEvent.KEYCODE_VOLUME_MUTE
        if (isVolumeKey && alarm != null && alarm.behavior.volumeButtonSilences) {
            AlarmService.send(this, AlarmService.ACTION_SILENCE)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
