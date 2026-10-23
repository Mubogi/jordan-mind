package com.jordanmind.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jordanmind.agent.core.ActionType
import com.jordanmind.agent.core.AgentState
import com.jordanmind.agent.core.Article
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Same amber palette as the Android build, plus a light scheme for daytime desktop use.
private val Ink = Color(0xFF0C0A05)
private val Surface1 = Color(0xFF16130B)
private val Surface2 = Color(0xFF211C11)
private val Gold = Color(0xFFFFC91F)
private val GoldDeep = Color(0xFF6B4E00)
private val Amber = Color(0xFFFF9E2C)

private val DarkScheme = darkColorScheme(
    primary = Gold,
    onPrimary = Color(0xFF241A00),
    primaryContainer = GoldDeep,
    onPrimaryContainer = Color(0xFFFFE08A),
    secondary = Amber,
    background = Ink,
    onBackground = Color(0xFFF3EEE2),
    surface = Surface1,
    onSurface = Color(0xFFF3EEE2),
    surfaceVariant = Surface2,
    onSurfaceVariant = Color(0xFFBFB49B),
    outline = Color(0xFF4A4130),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF8A6100),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFE08A),
    onPrimaryContainer = Color(0xFF2A1D00),
    secondary = Color(0xFFB85C00),
    background = Color(0xFFFFFBEF),
    onBackground = Color(0xFF201B10),
    surface = Color(0xFFFFF8E3),
    onSurface = Color(0xFF201B10),
    surfaceVariant = Color(0xFFF0E6C8),
    onSurfaceVariant = Color(0xFF52452A),
    outline = Color(0xFFD8C79A),
)

private enum class Page(val label: String) {
    AGENT("Agent"),
    CHAT("Chat"),
    CONTENT("Content"),
    MEMORY("Memory"),
    SETTINGS("Settings"),
}

@Composable
fun JordanMindApp(
    store: DesktopStore,
    model: LocalModel,
    actions: DesktopActions,
) {
    var dark by remember { mutableStateOf(true) }
    MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            Row(Modifier.fillMaxSize()) {
                SideRail(
                    onToggleTheme = { dark = !dark },
                    dark = dark,
                )
                Box(Modifier.fillMaxSize()) {
                    MainContent(store, model, actions)
                }
            }
        }
    }
}

@Composable
private fun SideRail(onToggleTheme: () -> Unit, dark: Boolean) {
    var page by remember { mutableStateOf(Page.AGENT) }
    Column(
        Modifier
            .width(200.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "Jordan Mind",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "offline agent",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        Page.entries.forEach { entry ->
            FilterChip(
                selected = page == entry,
                onClick = { page = entry },
                label = { Text(entry.label) },
                modifier = Modifier.fillMaxWidth(),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        }
        Spacer(Modifier.height(18.dp))
        TextButton(onClick = onToggleTheme) {
            Text(if (dark) "Light theme" else "Dark theme")
        }
    }
    CurrentPage.value = page
}

/** Bridges the rail's selection to the content pane without threading state through both. */
private val CurrentPage = androidx.compose.runtime.mutableStateOf(Page.AGENT)

@Composable
private fun MainContent(store: DesktopStore, model: LocalModel, actions: DesktopActions) {
    val page by CurrentPage
    when (page) {
        Page.AGENT -> AgentPage(store, model, actions)
        Page.CHAT -> ChatPage(model, store)
        Page.CONTENT -> ContentPage(store, model)
        Page.MEMORY -> MemoryPage(store)
        Page.SETTINGS -> SettingsPage(store, model)
    }
}

// ------------------------------------------------------------------------- agent

@Composable
private fun AgentPage(store: DesktopStore, model: LocalModel, actions: DesktopActions) {
    val scope = rememberCoroutineScope()
    var instruction by remember { mutableStateOf("") }
    val log = remember { mutableStateListOf<String>() }
    var busy by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Header("Agent", "Tell it what to do. It learns the phrasings you repeat.")

        OutlinedTextField(
            value = instruction,
            onValueChange = { instruction = it },
            modifier = Modifier.fillMaxWidth().height(96.dp),
            placeholder = { Text("open chrome, remind me to call the bank at 6pm, write an article about solar") },
            colors = OutlinedTextFieldDefaults.colors(),
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                enabled = !busy && instruction.isNotBlank(),
                onClick = {
                    val text = instruction.trim()
                    log.add("> $text")
                    instruction = ""
                    busy = true
                    scope.launch {
                        val planner = Planner(store.state) { prompt ->
                            withContext(Dispatchers.IO) { model.generate(prompt) }
                        }
                        val plan = planner.plan(text)
                        log.add("  planned: ${plan.action.type} (${plan.source.name.lowercase()})")
                        when (plan.action.type) {
                            ActionType.NOTE -> {
                                store.state.putFact(text.take(40), plan.action.args["text"] ?: text)
                                store.persist()
                                log.add("  remembered")
                            }
                            ActionType.REMIND -> {
                                val at = parseWhen(plan.action.args["time"].orEmpty())
                                if (at == null) {
                                    log.add("  ! could not read the time")
                                } else {
                                    store.addReminder(plan.action.args["task"] ?: text, at)
                                    log.add("  reminder set for ${DesktopStore.format(at)}")
                                }
                            }
                            ActionType.DRAFT_ARTICLE, ActionType.SCHEDULE_ARTICLE -> {
                                val topic = plan.action.args["topic"].orEmpty()
                                val body = withContext(Dispatchers.IO) {
                                    model.generate(DesktopPrompts.article(topic))
                                }
                                if (body.isBlank()) {
                                    log.add("  ! no model available, so nothing was written")
                                } else {
                                    val article = Article(
                                        id = System.currentTimeMillis().toString(),
                                        title = body.lineSequence().firstOrNull { it.isNotBlank() }?.take(80).orEmpty(),
                                        body = body,
                                        topic = topic,
                                        publishAt = if (plan.action.type == ActionType.SCHEDULE_ARTICLE) {
                                            System.currentTimeMillis() + 24 * 3600_000L
                                        } else 0L,
                                    )
                                    store.addArticle(article)
                                    val file = store.exportArticle(article)
                                    log.add("  saved ${file.name}")
                                }
                            }
                            ActionType.MONEY -> {
                                val ideas = withContext(Dispatchers.IO) {
                                    model.generate(DesktopPrompts.money(plan.action.args["topic"].orEmpty()))
                                }
                                if (ideas.isBlank()) log.add("  ! no model available") else {
                                    val article = Article(
                                        System.currentTimeMillis().toString(),
                                        "Money ideas",
                                        ideas,
                                        plan.action.args["topic"].orEmpty(),
                                    )
                                    store.addArticle(article)
                                    log.add("  saved ${store.exportArticle(article).name}")
                                }
                            }
                            ActionType.ANSWER -> log.add("  " + plan.action.reply.take(500))
                            else -> {
                                val result = actions.run(plan.action)
                                log.add((if (result.ok) "  " else "  ! ") + result.message)
                            }
                        }
                        store.persist()
                        busy = false
                    }
                },
            ) { Text(if (busy) "Working…" else "Run") }
            TextButton(onClick = { log.clear() }) { Text("Clear") }
            TextButton(onClick = {
                val text = DesktopPrompts.catchUp(store.state.pendingReminders(), store.state.facts())
                log.add(text)
            }) { Text("What did I forget?") }
        }

        Spacer(Modifier.height(16.dp))
        Panel(Modifier.fillMaxSize()) {
            SectionLabel("Activity")
            val listState = rememberLazyListState()
            SelectionContainer {
                LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(log) { line ->
                        Text(
                            line,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = if (line.startsWith("  !")) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------- chat

@Composable
private fun ChatPage(model: LocalModel, store: DesktopStore) {
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    val turns = remember { mutableStateListOf<Pair<String, String>>() }
    var busy by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Header("Chat", if (model.available) model.describe() else "No local model — set one up in Settings")

        Panel(Modifier.weight(1f)) {
            SelectionContainer {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(turns) { (question, answer) ->
                        Column {
                            Text(question, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                            Text(answer, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask anything") },
            )
            Button(
                enabled = !busy && input.isNotBlank(),
                onClick = {
                    val question = input.trim()
                    input = ""
                    busy = true
                    scope.launch {
                        val system = LocalModel.DEFAULT_SYSTEM + store.state.factBlock()
                        val answer = withContext(Dispatchers.IO) {
                            model.generate(question, system).ifBlank {
                                "No local model is configured. The agent features still work — " +
                                    "templates and learned patterns do not need one."
                            }
                        }
                        turns.add(question to answer)
                        busy = false
                    }
                },
            ) { Text("Send") }
        }
    }
}

// ----------------------------------------------------------------------- content

@Composable
private fun ContentPage(store: DesktopStore, model: LocalModel) {
    var topic by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var articles by remember { mutableStateOf(store.state.articles()) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Header("Content", "Draft articles and money ideas, saved as readable files")

        Panel {
            SectionLabel("Topic")
            OutlinedTextField(
                value = topic,
                onValueChange = { topic = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("solar power for small farms") },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    enabled = !busy && topic.isNotBlank(),
                    onClick = {
                        busy = true
                        scope.launch {
                            val body = withContext(Dispatchers.IO) {
                                model.generate(DesktopPrompts.article(topic))
                            }
                            output = body.ifBlank { "No local model configured, so nothing was written." }
                            if (body.isNotBlank()) {
                                val article = Article(
                                    System.currentTimeMillis().toString(),
                                    body.lineSequence().firstOrNull { it.isNotBlank() }?.take(80).orEmpty(),
                                    body,
                                    topic,
                                )
                                store.addArticle(article)
                                store.exportArticle(article)
                                articles = store.state.articles()
                            }
                            busy = false
                        }
                    },
                ) { Text("Write article") }
                Button(
                    enabled = !busy && topic.isNotBlank(),
                    onClick = {
                        busy = true
                        scope.launch {
                            val ideas = withContext(Dispatchers.IO) { model.generate(DesktopPrompts.money(topic)) }
                            output = ideas.ifBlank { "No local model configured." }
                            if (ideas.isNotBlank()) {
                                val article = Article(
                                    System.currentTimeMillis().toString(),
                                    "Money ideas: $topic",
                                    ideas,
                                    topic,
                                )
                                store.addArticle(article)
                                store.exportArticle(article)
                                articles = store.state.articles()
                            }
                            busy = false
                        }
                    },
                ) { Text("Money ideas") }
            }
            if (output.isNotBlank()) {
                SelectionContainer { MonoLine(output) }
            }
        }

        Spacer(Modifier.height(16.dp))
        Panel {
            SectionLabel("Saved (${articles.size})")
            if (articles.isEmpty()) MonoLine("Nothing saved yet.")
            articles.take(20).forEach { article ->
                Column {
                    Text(article.title.ifBlank { article.topic }, fontWeight = FontWeight.SemiBold)
                    MonoLine(
                        if (article.publishAt > 0) "scheduled ${DesktopStore.format(article.publishAt)}"
                        else "draft",
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------------ memory

@Composable
private fun MemoryPage(store: DesktopStore) {
    var patterns by remember { mutableStateOf(store.state.patterns()) }
    var reminders by remember { mutableStateOf(store.state.pendingReminders()) }
    val facts = store.state.facts()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Header("Memory", "What the agent has learned and what you asked it to remember")

        Panel {
            SectionLabel("Learned patterns (${store.state.learnedCount()})")
            MonoLine(
                "When the model works out a new phrasing, the mapping is stored here. " +
                    "Next time it runs instantly, with no model call.",
            )
            patterns.take(20).forEach { pattern ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(pattern.phrase, style = MaterialTheme.typography.bodySmall)
                        MonoLine("${pattern.action} · used ${pattern.hits}×")
                    }
                    TextButton(onClick = {
                        store.state.forget(pattern.phrase)
                        store.persist()
                        patterns = store.state.patterns()
                    }) { Text("Forget") }
                }
            }
            TextButton(onClick = {
                store.state.resetLearned()
                store.persist()
                patterns = store.state.patterns()
            }) { Text("Reset learned patterns") }
        }

        Spacer(Modifier.height(16.dp))
        Panel {
            SectionLabel("Reminders (${reminders.size})")
            if (reminders.isEmpty()) MonoLine("Nothing pending.")
            reminders.forEach { reminder ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(reminder.task)
                        MonoLine(DesktopStore.format(reminder.at))
                    }
                    TextButton(onClick = {
                        store.state.completeReminder(reminder.id)
                        store.persist()
                        reminders = store.state.pendingReminders()
                    }) { Text("Done") }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Panel {
            SectionLabel("Remembered facts (${facts.size})")
            if (facts.isEmpty()) MonoLine("Nothing yet. Say \"remember that …\".")
            facts.forEach { (key, value) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(key, style = MaterialTheme.typography.bodySmall)
                        MonoLine(value)
                    }
                    TextButton(onClick = {
                        store.state.forgetFact(key)
                        store.persist()
                    }) { Text("Forget") }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------- settings

@Composable
private fun SettingsPage(store: DesktopStore, model: LocalModel) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
        Header("Settings", "Where things live and how the model is wired up")

        Panel {
            SectionLabel("Local model")
            MonoLine(model.describe())
            MonoLine("")
            MonoLine("To enable chat and article writing, install llama.cpp so llama-cli is on your PATH,")
            MonoLine("then drop a .gguf model into ~/.jordanmind/models.")
            MonoLine("")
            MonoLine("Or point at an existing setup:")
            MonoLine("  JORDANMIND_MODEL=/path/to/model.gguf")
            MonoLine("  JORDANMIND_LLAMA=/path/to/llama-cli")
            MonoLine("")
            MonoLine("Everything runs on this machine. The app makes no network calls.")
        }

        Spacer(Modifier.height(16.dp))
        Panel {
            SectionLabel("Storage")
            MonoLine(store.memorySummary())
        }

        Spacer(Modifier.height(16.dp))
        Panel {
            SectionLabel("Honest limits")
            MonoLine("Desktop apps cannot send SMS or set phone alarms; those are Android-only.")
            MonoLine("Opening apps and folders works here instead.")
            MonoLine("Nothing is deleted, and only paths inside your home directory are opened.")
            MonoLine("Scheduled posts cannot publish themselves — there is no network access.")
        }
    }
}

// ---------------------------------------------------------------------- widgets

@Composable
private fun Header(title: String, subtitle: String) {
    Column(Modifier.padding(bottom = 16.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun MonoLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Accepts a clock time or a relative offset; mirrors the Android implementation. */
private fun parseWhen(raw: String): Long? {
    val text = raw.lowercase().trim()
    val relative = Regex("(\\d+)\\s*(min|hour|hr|sec)").find(text)
    if (text.startsWith("in ") || relative != null) {
        var seconds = 0
        Regex("(\\d+)\\s*(hours?|hrs?|h)").find(text)?.let { seconds += it.groupValues[1].toInt() * 3600 }
        Regex("(\\d+)\\s*(minutes?|mins?|m)\\b").find(text)?.let { seconds += it.groupValues[1].toInt() * 60 }
        Regex("(\\d+)\\s*(seconds?|secs?|s)\\b").find(text)?.let { seconds += it.groupValues[1].toInt() }
        if (seconds > 0) return System.currentTimeMillis() + seconds * 1000L
    }
    val clock = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?").find(text) ?: return null
    var hour = clock.groupValues[1].toIntOrNull() ?: return null
    val minute = clock.groupValues[2].toIntOrNull() ?: 0
    when (clock.groupValues[3]) {
        "pm" -> if (hour < 12) hour += 12
        "am" -> if (hour == 12) hour = 0
    }
    if (hour > 23 || minute > 59) return null
    val calendar = java.util.Calendar.getInstance()
    calendar.set(java.util.Calendar.HOUR_OF_DAY, hour)
    calendar.set(java.util.Calendar.MINUTE, minute)
    calendar.set(java.util.Calendar.SECOND, 0)
    if (calendar.timeInMillis <= System.currentTimeMillis()) {
        calendar.add(java.util.Calendar.DAY_OF_YEAR, 1)
    }
    return calendar.timeInMillis
}
