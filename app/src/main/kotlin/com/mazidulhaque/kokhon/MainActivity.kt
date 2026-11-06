package com.mazidulhaque.kokhon

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import com.mazidulhaque.kokhon.core.designsystem.theme.KokhonTheme
import com.mazidulhaque.kokhon.navigation.KokhonNavHost
import kotlinx.coroutines.flow.MutableStateFlow

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
        setContent {
            KokhonTheme {
                KokhonNavHost(entryToOpen = entryToOpen, sharedText = sharedText, sharedImage = sharedImage)
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
