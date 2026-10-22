package com.jordanmind.agent.engine

import android.content.Context
import android.os.Build
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.ProgressListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.io.File

sealed interface LlmState {
    data object Unloaded : LlmState
    data class Loading(val message: String) : LlmState
    data class Ready(val modelName: String, val backend: String) : LlmState
    data class Error(val message: String) : LlmState
}

data class LlmSettings(
    val backend: LlmInference.Backend = LlmInference.Backend.CPU,
    val maxTokens: Int = 1024,
    val topK: Int = 40,
    val topP: Float = 0.95f,
    val temperature: Float = 0.8f,
    /** Rough conversation token ceiling before the session is rebuilt. */
    val contextBudget: Int = 1800,
)

/**
 * Single on-device text generator backed by the MediaPipe / LiteRT LLM Inference API.
 *
 * One [LlmInference] handle is shared for the whole process; a [LlmInferenceSession] holds the
 * KV cache for a conversation so follow-up turns do not re-prefill the whole history.
 */
class LlmEngine(private val context: Context) {

    private var inference: LlmInference? = null
    private var session: LlmInferenceSession? = null
    private var settings = LlmSettings()

    private val _state = MutableStateFlow<LlmState>(LlmState.Unloaded)
    val state: StateFlow<LlmState> = _state.asStateFlow()

    val isReady: Boolean get() = inference != null

    /** Tokens currently resident in the live session's KV cache. */
    private var sessionTokens = 0
    private var turns = mutableListOf<Pair<String, String>>()

    suspend fun load(model: File, newSettings: LlmSettings = settings): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                unload()
                settings = newSettings
                _state.value = LlmState.Loading("Opening ${model.name}…")
                require(model.exists()) { "Model file not found: ${model.absolutePath}" }

                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(model.absolutePath)
                    .setMaxTokens(settings.maxTokens)
                    .setMaxTopK(settings.topK)
                    .setPreferredBackend(settings.backend)
                    .build()

                val created = LlmInference.createFromOptions(context, options)
                inference = created
                _state.value = LlmState.Ready(
                    modelName = model.name,
                    backend = settings.backend.name,
                )
            }.onFailure { error ->
                inference = null
                _state.value = LlmState.Error(error.message ?: error::class.java.simpleName)
            }
        }

    fun unload() {
        runCatching { session?.close() }
        session = null
        runCatching { inference?.close() }
        inference = null
        sessionTokens = 0
        turns = mutableListOf()
        _state.value = LlmState.Unloaded
    }

    /** Begin a fresh conversation. [system] is folded into the first user turn. */
    fun newChat() {
        runCatching { session?.close() }
        session = null
        sessionTokens = 0
        turns = mutableListOf()
    }

    fun stop() {
        runCatching { session?.cancelGenerateResponseAsync() }
    }

    /**
     * Streams the assistant reply for [prompt]. Partial results arrive as they are decoded and the
     * final emission is the complete text.
     */
    fun send(prompt: String, system: String = ""): Flow<String> = callbackFlow {
        val engine = inference
        if (engine == null) {
            close(IllegalStateException("No model loaded"))
            return@callbackFlow
        }

        val isFirstTurn = turns.isEmpty()
        val effective = if (isFirstTurn && system.isNotBlank()) "$system\n\n$prompt" else prompt

        val turnCost = runCatching { engine.sizeInTokens(effective) }.getOrDefault(0)
        if (session == null || sessionTokens + turnCost > settings.contextBudget) {
            // Rebuild the session and replay a trimmed history as plain text.
            runCatching { session?.close() }
            sessionTokens = 0
            val rebuilt = LlmInferenceSession.createFromOptions(engine, sessionOptions())
            session = rebuilt
            if (turns.isNotEmpty()) {
                val replay = buildString {
                    append(system.ifBlank { DEFAULT_SYSTEM }).append("\n\n")
                    turns.takeLast(4).forEach { (u, a) ->
                        append("User: ").append(u).append('\n')
                        append("Assistant: ").append(a).append('\n')
                    }
                    append("User: ").append(prompt)
                }
                sessionTokens = runCatching { engine.sizeInTokens(replay) }.getOrDefault(0)
                streamInto(rebuilt, replay)
                awaitClose { }
                return@callbackFlow
            }
        }

        val active = session ?: LlmInferenceSession.createFromOptions(engine, sessionOptions())
            .also { session = it }
        sessionTokens += turnCost
        streamInto(active, effective)
        awaitClose { }
    }

    private fun kotlinx.coroutines.channels.ProducerScope<String>.streamInto(
        target: LlmInferenceSession,
        text: String,
    ) {
        val listener = ProgressListener<String> { partial, done ->
            trySend(partial)
            if (done) {
                turns.add(text to partial)
                close()
            }
        }
        runCatching { target.addQueryChunk(text) }
            .onFailure { close(it); return }
        runCatching { target.generateResponseAsync(listener) }
            .onFailure { close(it) }
    }

    private fun sessionOptions(): LlmInferenceSession.LlmInferenceSessionOptions =
        LlmInferenceSession.LlmInferenceSessionOptions.builder()
            .setTopK(settings.topK)
            .setTopP(settings.topP)
            .setTemperature(settings.temperature)
            .build()

    fun deviceSummary(): String = buildString {
        append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
        append(" · Android ").append(Build.VERSION.RELEASE)
    }

    companion object {
        const val DEFAULT_SYSTEM =
            "You are Jordan Mind, a precise offline assistant running entirely on the user's phone. " +
                "You have no internet access. Answer directly, keep replies short unless asked for detail, " +
                "and say plainly when you do not know something."
    }
}
