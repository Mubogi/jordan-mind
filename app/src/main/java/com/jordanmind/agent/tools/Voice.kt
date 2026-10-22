package com.jordanmind.agent.tools

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

/**
 * Voice helpers.
 *
 * TTS uses the platform engine, which is on-device and needs no network.
 *
 * STT uses [RecognizerIntent]. On most devices the recogniser is the system one and may fall back to
 * a network service; this app declares no INTERNET permission, so where the recogniser requires the
 * network it simply fails and the user is told to type instead. Fully offline dictation would need a
 * bundled model such as Vosk with its own ~50 MB weights, which is deliberately not included here.
 */
object Voice {

    fun speechIntent(locale: Locale = Locale.getDefault()): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak")
        }

    fun extractTranscript(data: Intent?): String? =
        data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()

    class Speaker(context: Context) {

        private var engine: TextToSpeech? = null
        private var ready = false

        init {
            engine = TextToSpeech(context.applicationContext) { status ->
                ready = status == TextToSpeech.SUCCESS
                if (ready) engine?.language = Locale.getDefault()
            }
        }

        fun speak(text: String) {
            if (!ready) return
            engine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jordanmind")
        }

        fun stop() {
            engine?.stop()
        }

        fun shutdown() {
            engine?.shutdown()
            engine = null
            ready = false
        }
    }
}
