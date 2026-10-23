package com.jordanmind.agent.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jordanmind.agent.agent.ContentStudio
import com.jordanmind.agent.agent.FloatingBotService
import com.jordanmind.agent.agent.MemoryStore
import com.jordanmind.agent.agent.ReminderStore
import com.jordanmind.agent.engine.LlmState

/**
 * Control centre for the device agent: run instructions, see what it has learned, manage the
 * floating bot and scheduled posts.
 */
@Composable
fun AgentTab(vm: AppViewModel) {
    val context = LocalContext.current
    val memory = remember { MemoryStore(context) }
    val reminders = remember { ReminderStore(context) }
    val studio = remember { ContentStudio(context) }

    var instruction by remember { mutableStateOf("") }
    var transcript by remember { mutableStateOf(listOf<String>()) }
    var patterns by remember { mutableStateOf(memory.all()) }
    var scheduled by remember { mutableStateOf(studio.scheduled()) }
    var openReminders by remember { mutableStateOf(reminders.pending()) }
    var botOn by remember { mutableStateOf(false) }
    var lastSource by remember { mutableStateOf("") }

    val llmState by vm.llmState.collectAsStateWithLifecycle()
    // Must be observed as state: reading busy.value directly would never trigger recomposition,
    // leaving the Run button permanently disabled after the first instruction.
    val busy by vm.busy.collectAsStateWithLifecycle()

    val contactPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    LaunchedEffect(Unit) {
        ReminderStore.ensureChannels(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            contactPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun refresh() {
        patterns = memory.all()
        scheduled = studio.scheduled()
        openReminders = reminders.pending()
    }

    fun append(line: String) {
        transcript = (transcript + line).takeLast(40)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    ) {
        TabHeader(
            title = "Agent",
            subtitle = "Runs instructions on this phone, and learns the ones you repeat",
        )

        Column(
            Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Panel {
                SectionLabel("Instruction")
                OutlinedTextField(
                    value = instruction,
                    onValueChange = { instruction = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    placeholder = {
                        Text("open whatsapp, remind me to call the bank at 6pm, write an article about solar")
                    },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val text = instruction.trim()
                            if (text.isEmpty()) return@Button
                            append("> $text")
                            vm.runAgentInstruction(
                                text,
                                onLine = { line ->
                                    append(line)
                                    refresh()
                                },
                                onSource = { lastSource = it },
                            )
                            instruction = ""
                        },
                        enabled = !busy,
                    ) { Text("Run") }
                    TextButton(onClick = { transcript = emptyList() }) { Text("Clear") }
                }
                if (lastSource.isNotBlank()) {
                    Badge(lastSource, MaterialTheme.colorScheme.tertiary)
                }
                MonoLine(
                    when (llmState) {
                        is LlmState.Ready -> "Model loaded. Unrecognised phrasings go to the model."
                        is LlmState.Loading -> "Model loading…"
                        else -> "No model loaded. Templates and learned patterns still work."
                    },
                )
            }

            if (transcript.isNotEmpty()) {
                Panel {
                    SectionLabel("Activity")
                    transcript.forEach { MonoLine(it) }
                }
            }

            Panel {
                SectionLabel("Floating bot")
                MonoLine(
                    "A bubble over other apps that reminds you of due reminders, scheduled posts " +
                        "and things you asked it to remember.",
                )
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Show bubble", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = botOn,
                        onCheckedChange = { want ->
                            if (want && !Settings.canDrawOverlays(context)) {
                                append("! Grant \"display over other apps\" for the bubble to appear.")
                                runCatching {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}"),
                                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                }
                                botOn = true
                                FloatingBotService.start(context)
                            } else if (want) {
                                FloatingBotService.start(context)
                                botOn = true
                                append("Bubble shown. Drag it anywhere.")
                            } else {
                                FloatingBotService.stop(context)
                                botOn = false
                                append("Bubble hidden.")
                            }
                        },
                    )
                }
            }

            Panel {
                SectionLabel("Scheduled posts")
                if (scheduled.isEmpty()) {
                    MonoLine("Nothing scheduled. Try \"schedule an article about farming in Kenya\".")
                } else {
                    scheduled.forEach { article ->
                        Column {
                            Text(
                                article.title.ifBlank { article.topic },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            MonoLine(ContentStudio.formatTime(article.publishAt))
                        }
                    }
                }
            }

            Panel {
                SectionLabel("Reminders")
                if (openReminders.isEmpty()) {
                    MonoLine("No reminders pending.")
                } else {
                    openReminders.forEach { reminder ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.fillMaxWidth(0.72f)) {
                                Text(reminder.task, style = MaterialTheme.typography.bodyMedium)
                                MonoLine(ReminderStore.format(reminder.at))
                            }
                            TextButton(onClick = {
                                reminders.complete(reminder.id)
                                refresh()
                                append("Done: ${reminder.task}")
                            }) { Text("Done") }
                        }
                    }
                }
            }

            Panel {
                SectionLabel("What it has learned (${memory.learnedCount()})")
                MonoLine(
                    "When the model turns a new phrasing into an action, that mapping is saved here. " +
                        "Next time it runs instantly, without the model.",
                )
                patterns.take(12).forEach { pattern ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.fillMaxWidth(0.78f)) {
                            Text(pattern.phrase, style = MaterialTheme.typography.bodySmall)
                            MonoLine("${pattern.action} · used ${pattern.hits}×")
                        }
                        TextButton(onClick = {
                            memory.forget(pattern.phrase)
                            refresh()
                        }) { Text("Forget") }
                    }
                }
                TextButton(onClick = {
                    memory.clearPatterns()
                    refresh()
                }) { Text("Reset learned patterns") }
            }

            Panel {
                SectionLabel("Money ideas")
                MonoLine(
                    "Ask \"how can I make money with <skill or asset>\" in the box above. " +
                        "The model gives practical options and says plainly when something is hard.",
                )
            }
        }
    }
}
