package com.jordanmind.agent.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jordanmind.agent.data.RagStore
import com.jordanmind.agent.data.Workspace
import com.jordanmind.agent.engine.LlmEngine
import com.jordanmind.agent.engine.LlmSettings
import com.jordanmind.agent.engine.LlmState
import com.jordanmind.agent.engine.ModelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ChatMessage(
    val role: Role,
    val text: String,
    val streaming: Boolean = false,
) {
    enum class Role { USER, ASSISTANT, SYSTEM }
}

/** Single shared state holder for all six tabs. */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    val workspace = Workspace(application)
    val models = ModelRepository(application)
    val rag = RagStore(application)

    private val engine = LlmEngine(application)
    val llmState: StateFlow<LlmState> = engine.state

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    private val _installed = MutableStateFlow<List<File>>(emptyList())
    val installed: StateFlow<List<File>> = _installed.asStateFlow()

    private val _settings = MutableStateFlow(LlmSettings())
    val settings: StateFlow<LlmSettings> = _settings.asStateFlow()

    private val _activeModel = MutableStateFlow<File?>(null)
    val activeModel: StateFlow<File?> = _activeModel.asStateFlow()

    private var generation: Job? = null

    init {
        refreshModels()
    }

    fun refreshModels() {
        _installed.value = models.installedModels
    }

    fun notify(message: String) {
        _status.value = message
    }

    fun clearStatus() {
        _status.value = null
    }

    // ------------------------------------------------------------------ model

    fun installBundledModel() = viewModelScope.launch {
        _busy.value = true
        models.extractBundledModel { msg -> _status.value = msg }
            .onSuccess {
                refreshModels()
                _status.value = "Bundled model installed"
                loadModel(it)
            }
            .onFailure { _status.value = "Install failed: ${it.message}" }
        _busy.value = false
    }

    fun importModel(uri: Uri) = viewModelScope.launch {
        _busy.value = true
        models.importFromUri(uri) { msg -> _status.value = msg }
            .onSuccess {
                refreshModels()
                _status.value = "Imported ${it.name}"
                loadModel(it)
            }
            .onFailure { _status.value = "Import failed: ${it.message}" }
        _busy.value = false
    }

    fun loadModel(file: File) = viewModelScope.launch {
        _busy.value = true
        engine.load(file, _settings.value)
            .onSuccess {
                _activeModel.value = file
                engine.newChat()
                _messages.value = emptyList()
                _status.value = "Loaded ${file.name}"
            }
            .onFailure { _status.value = "Load failed: ${it.message}" }
        _busy.value = false
    }

    fun deleteModel(file: File) = viewModelScope.launch {
        withContext(Dispatchers.IO) { models.delete(file) }
        refreshModels()
        _status.value = "Deleted ${file.name}"
    }

    fun unloadModel() {
        engine.unload()
        _activeModel.value = null
        _status.value = "Model unloaded"
    }

    fun updateSettings(transform: (LlmSettings) -> LlmSettings) {
        _settings.value = transform(_settings.value)
    }

    // -------------------------------------------------------------------- chat

    fun send(prompt: String, system: String = LlmEngine.DEFAULT_SYSTEM) {
        if (prompt.isBlank() || generation?.isActive == true) return
        _messages.value = _messages.value + ChatMessage(ChatMessage.Role.USER, prompt)
        val placeholderIndex = _messages.value.size
        _messages.value = _messages.value + ChatMessage(ChatMessage.Role.ASSISTANT, "", streaming = true)

        generation = viewModelScope.launch {
            var accumulated = ""
            try {
                engine.send(prompt, system).collect { partial ->
                    accumulated = partial
                    updateAt(placeholderIndex, partial, streaming = true)
                }
                updateAt(placeholderIndex, accumulated, streaming = false)
            } catch (error: Exception) {
                updateAt(
                    placeholderIndex,
                    accumulated.ifBlank { "⚠️ ${error.message ?: "generation failed"}" },
                    streaming = false,
                )
            }
        }
    }

    fun stopGeneration() {
        engine.stop()
        generation?.cancel()
        _messages.value = _messages.value.map { if (it.streaming) it.copy(streaming = false) else it }
    }

    fun newChat() {
        engine.newChat()
        _messages.value = emptyList()
    }

    private fun updateAt(index: Int, text: String, streaming: Boolean) {
        val current = _messages.value.toMutableList()
        if (index in current.indices) {
            current[index] = current[index].copy(text = text, streaming = streaming)
            _messages.value = current
        }
    }

    // ----------------------------------------------------------- generic tasks

    /** Runs a one-shot prompt off the chat transcript and hands back the full answer. */
    fun runOnce(
        prompt: String,
        system: String = LlmEngine.DEFAULT_SYSTEM,
        onResult: (String) -> Unit,
    ): Job = viewModelScope.launch {
        _busy.value = true
        var accumulated = ""
        try {
            engine.send(prompt, system).collect { accumulated = it }
            onResult(accumulated)
        } catch (error: Exception) {
            onResult("⚠️ ${error.message ?: "generation failed"}")
        } finally {
            _busy.value = false
        }
    }

    override fun onCleared() {
        engine.unload()
        super.onCleared()
    }
}
