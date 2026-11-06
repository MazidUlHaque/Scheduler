package com.mazidulhaque.kokhon.core.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.mazidulhaque.kokhon.core.parser.OcrLine
import com.mazidulhaque.kokhon.core.parser.ScreenshotMessage
import com.mazidulhaque.kokhon.core.parser.ScreenshotText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime

/**
 * Reads the text of a screenshot or photo on the device (ML Kit, bundled model): nothing is uploaded.
 */
class ScreenshotReader(
    private val context: Context,
    private val clock: Clock,
) {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    /** Recognises the text and cleans it as a chat screenshot. */
    suspend fun read(uri: Uri): ScreenshotMessage {
        val image = withContext(Dispatchers.IO) { InputImage.fromFilePath(context, uri) }
        val result = recognizer.process(image).await()
        val lines =
            result.textBlocks.flatMap { block ->
                block.lines.mapNotNull { line ->
                    val box = line.boundingBox ?: return@mapNotNull null
                    OcrLine(line.text, box.left, box.top, box.right, box.bottom)
                }
            }
        val height = if (image.rotationDegrees % 180 == 0) image.height else image.width
        return ScreenshotText.clean(lines, height, capturedAt(uri))
    }

    /** When the screenshot was taken, from the media store when available; otherwise now. */
    private suspend fun capturedAt(uri: Uri): LocalDateTime =
        withContext(Dispatchers.IO) {
            val millis =
                runCatching {
                    context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATE_TAKEN), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
                    }
                }.getOrNull()?.takeIf { it > 0 }
            LocalDateTime.ofInstant(millis?.let(Instant::ofEpochMilli) ?: clock.instant(), clock.zone)
        }

    /** A small preview of the image, so the user sees which screenshot was read. */
    suspend fun thumbnail(
        uri: Uri,
        maxSide: Int = 720,
    ): Bitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                }
            }.getOrNull()
        }
}
