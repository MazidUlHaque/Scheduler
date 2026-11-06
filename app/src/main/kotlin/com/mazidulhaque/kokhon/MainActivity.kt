package com.mazidulhaque.kokhon

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mazidulhaque.kokhon.core.data.settings.Appearance
import com.mazidulhaque.kokhon.core.designsystem.theme.KokhonTheme
import com.mazidulhaque.kokhon.navigation.KokhonNavHost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class MainActivity : ComponentActivity() {
    /** An entry to open, set when the app is launched from a notification. */
    private val entryToOpen = MutableStateFlow<Long?>(null)

    /** Text shared from another app, to be turned into a plan. */
    private val sharedText = MutableStateFlow<String?>(null)

    /** A screenshot shared from another app. */
    private val sharedImage = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handle(intent)
        val appearanceFlow = (application as KokhonApplication).settingsRepository.settings.map { it.appearance }
        setContent {
            val appearance by appearanceFlow.collectAsStateWithLifecycle(initialValue = Appearance.SYSTEM)
            val dark =
                when (appearance) {
                    Appearance.SYSTEM -> isSystemInDarkTheme()
                    Appearance.LIGHT -> false
                    Appearance.DARK -> true
                }
            KokhonTheme(darkTheme = dark) {
                // The surface gives every screen readable text colours in both light and dark mode.
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    KokhonNavHost(entryToOpen = entryToOpen, sharedText = sharedText, sharedImage = sharedImage)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        when (intent?.action) {
            ACTION_OPEN_ENTRY -> {
                entryToOpen.value = intent.getLongExtra(EXTRA_ENTRY_ID, 0).takeIf { it != 0L }
            }

            ACTION_OPEN_IMAGE -> {
                sharedImage.value = intent.getStringExtra(EXTRA_IMAGE_URI)
            }

            Intent.ACTION_SEND -> {
                if (intent.type?.startsWith("image/") == true) {
                    val uri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, android.net.Uri::class.java)
                    sharedImage.value = uri?.toString()
                    return
                }
                val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                sharedText.value = listOfNotNull(subject, text).joinToString("\n").takeIf { it.isNotBlank() }
            }
        }
    }

    companion object {
        const val ACTION_OPEN_ENTRY = "com.mazidulhaque.kokhon.action.OPEN_ENTRY"
        const val EXTRA_ENTRY_ID = "entry_id"
        const val ACTION_OPEN_IMAGE = "com.mazidulhaque.kokhon.action.OPEN_IMAGE"
        const val EXTRA_IMAGE_URI = "image_uri"
    }
}
