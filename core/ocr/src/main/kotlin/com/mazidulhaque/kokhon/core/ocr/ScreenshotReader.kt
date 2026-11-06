package com.mazidulhaque.kokhon.core.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.mazidulhaque.kokhon.core.parser.OcrLine
import com.mazidulhaque.kokhon.core.parser.OcrWord
import com.mazidulhaque.kokhon.core.parser.ScreenshotMessage
import com.mazidulhaque.kokhon.core.parser.ScreenshotText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime

/** Why a screenshot could not be read, with technical details the user can copy and send. */
class ScreenshotReadException(
    val reason: Reason,
    cause: Throwable?,
) : Exception(reason.name + (cause?.let { ": ${it.javaClass.simpleName}: ${it.message}" } ?: ""), cause) {
    enum class Reason { CANNOT_OPEN, RECOGNITION_FAILED, NO_TEXT }
}

/**
 * Reads the text of a screenshot or photo on the device (ML Kit, bundled model): nothing is uploaded.
 */
class ScreenshotReader(
    private val context: Context,
    private val clock: Clock,
) {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    /** Recognises the text and cleans it as a chat screenshot. Throws [ScreenshotReadException]. */
    suspend fun read(uri: Uri): ScreenshotMessage {
        val image = open(uri)
        val result =
            try {
                recognizer.process(image).await()
            } catch (e: Exception) {
                throw ScreenshotReadException(ScreenshotReadException.Reason.RECOGNITION_FAILED, e)
            }
        val lines =
            result.textBlocks.flatMap { block ->
                block.lines.mapNotNull { line ->
                    val box = line.boundingBox ?: return@mapNotNull null
                    val words = line.elements.mapNotNull { e -> e.boundingBox?.let { OcrWord(e.text, it.height()) } }
                    OcrLine(line.text, box.left, box.top, box.right, box.bottom, words)
                }
            }
        if (lines.isEmpty()) throw ScreenshotReadException(ScreenshotReadException.Reason.NO_TEXT, null)
        val height = if (image.rotationDegrees % 180 == 0) image.height else image.width
        val message = ScreenshotText.clean(lines, height, capturedAt(uri))
        if (message.text.isBlank()) throw ScreenshotReadException(ScreenshotReadException.Reason.NO_TEXT, null)
        return message
    }

    /**
     * Opens the image two ways: decoding it ourselves (works with most apps' sharing and the photo picker),
     * then ML Kit's own loader. Very large images are scaled down, which also keeps memory use low.
     */
    private suspend fun open(uri: Uri): InputImage =
        withContext(Dispatchers.IO) {
            val first = runCatching { InputImage.fromBitmap(decode(uri), rotationOf(uri)) }
            first.getOrNull() ?: runCatching { InputImage.fromFilePath(context, uri) }.getOrElse { second ->
                first.exceptionOrNull()?.let { second.addSuppressed(it) }
                throw ScreenshotReadException(ScreenshotReadException.Reason.CANNOT_OPEN, second)
            }
        }

    private fun decode(uri: Uri): Bitmap =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > MAX_SIDE) decoder.setTargetSampleSize((longest + MAX_SIDE - 1) / MAX_SIDE)
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_SIDE) sample *= 2
            requireNotNull(
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                },
            ) { "The image could not be decoded" }
        }

    /** ImageDecoder already applies the photo's orientation; older Android needs it passed on. */
    private fun rotationOf(uri: Uri): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) return 0
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        }.getOrDefault(0)
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

    private companion object {
        const val MAX_SIDE = 2600
    }
}
