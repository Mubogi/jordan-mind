package com.jordanmind.agent.tools

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** On-device OCR via ML Kit. The Latin recognizer is bundled, so no download is required. */
object Ocr {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun recognize(context: Context, uri: Uri): String = suspendCancellableCoroutine { cont ->
        val image = try {
            InputImage.fromFilePath(context, uri)
        } catch (error: Exception) {
            cont.resumeWithException(error)
            return@suspendCancellableCoroutine
        }
        recognizer.process(image)
            .addOnSuccessListener { result ->
                val blocks = result.textBlocks.joinToString("\n\n") { it.text }
                cont.resume(if (blocks.isBlank()) result.text else blocks)
            }
            .addOnFailureListener { cont.resumeWithException(it) }
    }
}
