package com.mazidulhaque.kokhon.ui.capture

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

/** The two ways to bring in an image: pick a screenshot, or take a photo (of a printed invite, a notice board). */
class ImageSources internal constructor(
    val pickScreenshot: () -> Unit,
    val takePhoto: () -> Unit,
)

/**
 * Uses the system Photo Picker (no storage permission needed) and the system camera app
 * (no camera permission needed). [onImage] receives the chosen image.
 */
@Composable
fun rememberImageSources(onImage: (Uri) -> Unit): ImageSources {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(onImage) }
    var pendingPhoto by rememberSaveableUri()
    val camera =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
            val uri = pendingPhoto
            if (saved && uri != null) onImage(uri)
        }
    return remember(picker, camera) {
        ImageSources(
            pickScreenshot = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            takePhoto = {
                val dir = File(context.cacheDir, "camera").apply { mkdirs() }
                val file = File(dir, "photo-${System.currentTimeMillis()}.jpg")
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                pendingPhoto = uri
                camera.launch(uri)
            },
        )
    }
}

@Composable
private fun rememberSaveableUri() = rememberSaveable { androidx.compose.runtime.mutableStateOf<Uri?>(null) }
