package com.jordanmind.agent.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jordanmind.agent.agent.ActionRunner
import com.jordanmind.agent.agent.ActionType
import com.jordanmind.agent.agent.Article
import com.jordanmind.agent.agent.ContentStudio
import com.jordanmind.agent.agent.MemoryStore
import com.jordanmind.agent.agent.Planner
import com.jordanmind.agent.agent.Prompts
import com.jordanmind.agent.agent.ReminderStore
import com.jordanmind.agent.agent.WorkBot
import com.jordanmind.agent.data.RagStore
import com.jordanmind.agent.data.Workspace
import com.jordanmind.agent.engine.DeviceProfile
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

    /** What this phone can comfortably run; drives defaults and the settings summary. */
    val deviceProfile: DeviceProfile = engine.profile

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    private val _installed = MutableStateFlow<List<File>>(emptyList())
    val installed: StateFlow<List<File>> = _installed.asStateFlow()

    private val _settings = MutableStateFlow(LlmSettings().cappedFor(engine.profile))
    val settings: StateFlow<LlmSettings> = _settings.asStateFlow()

    private val _activeModel = MutableStateFlow<File?>(null)
    val activeModel: StateFlow<File?> = _activeModel.asStateFlow()

    /**
     * Text handed to the app from a share sheet, waiting to be sent. Kept separate from [messages]
     * so the UI can switch to the chat tab before the turn starts.
     */
    private val _sharedText = MutableStateFlow<String?>(null)
    val sharedText: StateFlow<String?> = _sharedText.asStateFlow()

    fun onShareReceived(text: String?) {
        if (!text.isNullOrBlank()) _sharedText.value = text.trim()
    }

    fun consumeSharedText() {
        val text = _sharedText.value ?: return
        _sharedText.value = null
        submitExternalText(text)
    }

    /**
     * Work mode swaps the chat persona. [send] picks the prompt at call time, so toggling it does
     * not disturb a reply already in flight.
     */
    private val _workMode = MutableStateFlow(false)
    val workMode: StateFlow<Boolean> = _workMode.asStateFlow()

    fun setWorkMode(enabled: Boolean) {
        _workMode.value = enabled
    }

    private var generation: Job? = null
    private var autoLoadStarted = false

    init {
        refreshModels()
        autoLoadModel()
    }

    fun refreshModels() {
        _installed.value = models.installedModels
    }

    /**
     * Loads a model without the user having to ask, because a chat app that starts with a disabled
     * send button reads as broken. Prefers an already-extracted model; otherwise unpacks the one
     * baked into the APK. Runs only once per process.
     */
    private fun autoLoadModel() {
        if (autoLoadStarted) return
        autoLoadStarted = true
        viewModelScope.launch {
            val installed = models.installedModels
            if (installed.isNotEmpty()) {
                loadModel(installed.first())
                return@launch
            }
            if (models.bundledModelName() == null) {
                // Lite build: nothing to load, so say what to do instead of sitting idle.
                _status.value = "No model in this build — import a .task file to start chatting"
                return@launch
            }
            _busy.value = true
            models.extractBundledModel { msg -> _status.value = msg }
                .onSuccess {
                    refreshModels()
                    loadModel(it)
                }
                .onFailure { _status.value = "Could not unpack the bundled model: ${it.message}" }
            _busy.value = false
        }
    }

    fun notify(message: String) {
        _status.value = message
    }

    /**
     * Entry point for text shared from another app, so a message or document can be handed to the
     * agent without retyping it. [send] does the message bookkeeping.
     */
    fun submitExternalText(text: String) {
        if (text.isBlank()) return
        send(text.trim())
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

    private fun activeSystem(): String =
        if (_workMode.value) WorkBot.SYSTEM else LlmEngine.DEFAULT_SYSTEM

    fun send(prompt: String, system: String = activeSystem()) {
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

    // ------------------------------------------------------------------ agent

    val memory = MemoryStore(application)
    val reminders = ReminderStore(application)
    val studio = ContentStudio(application)
    private val runner = ActionRunner(application)

    /**
     * Plans an instruction and runs it. Progress lines are handed to [onLine] as they happen so
     * the Agent tab can show what the agent is doing.
     *
     * Order matters: templates and learned patterns are tried before the model, so the agent is
     * usable with no model loaded at all.
     */
    fun runAgentInstruction(
        instruction: String,
        onLine: (String) -> Unit,
        onSource: (String) -> Unit = {},
    ) {
        viewModelScope.launch {
            _busy.value = true
            try {
                val planner = Planner(memory) { prompt ->
                    var out = ""
                    engine.send(prompt, AGENT_SYSTEM).collect { out = it }
                    out
                }
                val plan = planner.plan(instruction)
                onSource(plan.source.name.lowercase())
                onLine("· planned: ${plan.action.type}")

                when (plan.action.type) {
                    ActionType.NOTE -> {
                        memory.putFact(instruction.take(40), plan.action.args["text"] ?: instruction)
                        onLine("· remembered")
                    }
                    ActionType.REMIND -> {
                        val at = parseWhen(plan.action.args["time"].orEmpty())
                        if (at == null) {
                            onLine("! could not read the time \"${plan.action.args["time"]}\"")
                        } else {
                            val task = plan.action.args["task"].orEmpty().ifBlank { instruction }
                            reminders.add(task, at)
                            onLine("· reminder set for ${ReminderStore.format(at)}")
                        }
                    }
                    ActionType.DRAFT_ARTICLE -> {
                        val topic = plan.action.args["topic"].orEmpty()
                        val body = generate(Prompts.article(topic))
                        studio.add(Article(id = nextId(), title = firstLine(body), body = body, topic = topic))
                        onLine("· drafted \"$topic\" (${body.split(" ").size} words)")
                    }
                    ActionType.SCHEDULE_ARTICLE -> {
                        val topic = plan.action.args["topic"].orEmpty()
                        val body = generate(Prompts.article(topic))
                        val at = System.currentTimeMillis() + 24 * 3600_000L
                        studio.add(
                            Article(
                                id = nextId(),
                                title = firstLine(body),
                                body = body,
                                topic = topic,
                                publishAt = at,
                            ),
                        )
                        onLine("· scheduled \"$topic\" for ${ContentStudio.formatTime(at)}")
                        onLine("  note: it will notify you, it cannot post by itself offline")
                    }
                    ActionType.MONEY -> {
                        val ideas = generate(Prompts.money(plan.action.args["topic"].orEmpty()))
                        studio.add(
                            Article(
                                id = nextId(),
                                title = "Money ideas",
                                body = ideas,
                                topic = plan.action.args["topic"].orEmpty(),
                            ),
                        )
                        onLine("· saved money ideas to Docs")
                    }
                    ActionType.ANSWER -> onLine(plan.action.reply.take(600))
                    else -> {
                        val result = runner.run(plan.action)
                        onLine(if (result.ok) "· ${result.message}" else "! ${result.message}")
                    }
                }
            } catch (error: Exception) {
                onLine("! ${error.message ?: "something went wrong"}")
            } finally {
                _busy.value = false
            }
        }
    }

    private suspend fun generate(prompt: String): String {
        var out = ""
        runCatching { engine.send(prompt, LlmEngine.DEFAULT_SYSTEM).collect { out = it } }
        return out.trim().ifBlank { "The model returned nothing. Load a model in Chat first." }
    }

    private fun nextId(): String = System.currentTimeMillis().toString()

    private fun firstLine(body: String): String =
        body.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(80).orEmpty()

    /** Accepts a clock time ("7pm") or a relative offset ("in 20 minutes"). */
    private fun parseWhen(raw: String): Long? {
        val text = raw.lowercase().trim()
        if (text.startsWith("in ") || Regex("\\d+\\s*(min|hour|hr|sec)").containsMatchIn(text)) {
            ActionRunner.parseDuration(text)?.let { return System.currentTimeMillis() + it * 1000L }
        }
        val clock = ActionRunner.parseTime(text) ?: return null
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, clock.first)
        calendar.set(java.util.Calendar.MINUTE, clock.second)
        calendar.set(java.util.Calendar.SECOND, 0)
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        return calendar.timeInMillis
    }

    override fun onCleared() {
        engine.unload()
        super.onCleared()
    }

    companion object {
        private const val AGENT_SYSTEM =
            "You convert phone instructions into a single action. Reply with JSON only."
    }
}
