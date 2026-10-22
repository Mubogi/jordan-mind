package com.jordanmind.agent.ui.tabs

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jordanmind.agent.engine.LlmState
import com.jordanmind.agent.tools.Voice
import com.jordanmind.agent.ui.AppViewModel
import com.jordanmind.agent.ui.ChatMessage
import com.jordanmind.agent.ui.SectionLabel
import com.jordanmind.agent.ui.TabHeader

@Composable
fun ChatTab(vm: AppViewModel) {
    val context = LocalContext.current
    val messages by vm.messages.collectAsStateWithLifecycle()
    val state by vm.llmState.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    var input by remember { mutableStateOf("") }
    var speakReplies by remember { mutableStateOf(false) }
    val speaker = remember { Voice.Speaker(context) }

    DisposableEffect(Unit) { onDispose { speaker.shutdown() } }

    val modelPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(vm::importModel) }

    val speech = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val transcript = if (result.resultCode == Activity.RESULT_OK) {
            Voice.extractTranscript(result.data)
        } else {
            null
        }
        if (transcript != null) input = transcript else vm.notify("Dictation unavailable — type instead")
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    // Speak only the newest completed assistant turn.
    val lastCompleted = messages.lastOrNull()
        ?.takeIf { !it.streaming && it.role == ChatMessage.Role.ASSISTANT }
    LaunchedEffect(lastCompleted?.text, speakReplies) {
        if (speakReplies && lastCompleted != null && lastCompleted.text.isNotBlank()) {
            speaker.speak(lastCompleted.text)
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        TabHeader(
            title = "Jordan Mind",
            subtitle = when (val s = state) {
                is LlmState.Ready -> "${s.modelName} · ${s.backend} · offline"
                is LlmState.Loading -> s.message
                is LlmState.Error -> "Error: ${s.message}"
                LlmState.Unloaded -> "No model loaded"
            },
            action = "New" to { vm.newChat() },
        )

        if (state !is LlmState.Ready) {
            ModelSetupPanel(vm, onPickFile = { modelPicker.launch(arrayOf("*/*")) })
        }
        if (busy && state is LlmState.Loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (messages.isEmpty() && state is LlmState.Ready) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionLabel("Try asking")
                        listOf(
                            "Summarise photosynthesis in three lines",
                            "Write a polite email asking for a deadline extension",
                            "Explain recursion with a simple example",
                        ).forEach { prompt ->
                            AssistChip(
                                onClick = { input = prompt },
                                label = { Text(prompt, maxLines = 1) },
                            )
                        }
                    }
                }
            }
            items(messages) { message -> MessageBubble(message) }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = { speech.launch(Voice.speechIntent()) }) {
                Icon(Icons.Filled.Mic, contentDescription = "Dictate")
            }
            IconButton(onClick = { speakReplies = !speakReplies }) {
                Icon(
                    Icons.Filled.VolumeUp,
                    contentDescription = "Toggle spoken replies",
                    tint = if (speakReplies) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask anything…") },
                maxLines = 4,
            )
            if (messages.any { it.streaming }) {
                IconButton(onClick = { vm.stopGeneration() }) {
                    Icon(Icons.Filled.Stop, contentDescription = "Stop")
                }
            } else {
                IconButton(
                    enabled = input.isNotBlank() && state is LlmState.Ready,
                    onClick = {
                        val prompt = input.trim()
                        input = ""
                        vm.send(prompt)
                    },
                ) {
                    Icon(Icons.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == ChatMessage.Role.USER
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Box(
            Modifier
                .fillMaxWidth(0.88f)
                .background(
                    if (isUser) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(14.dp),
                )
                .padding(12.dp),
        ) {
            Text(
                text = message.text.ifBlank { if (message.streaming) "…" else "" },
                style = MaterialTheme.typography.bodyMedium,
                color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun ModelSetupPanel(vm: AppViewModel, onPickFile: () -> Unit) {
    val installed by vm.installed.collectAsStateWithLifecycle()
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionLabel("Model")
        Text(
            "Jordan Mind runs entirely on this device. Pick a LiteRT / MediaPipe model to begin. " +
                "Qwen2.5 0.5B q8 (~547 MB) is the lightest option; 1.5B q8 (~1.6 GB) reasons better " +
                "but wants 6 GB+ RAM.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.installBundledModel() }) { Text("Install bundled") }
            Button(onClick = onPickFile) { Text("Import file") }
        }
        if (installed.isNotEmpty()) {
            SectionLabel("Installed")
            installed.forEach { file ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${file.name} · ${file.length() / 1_048_576} MB",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(onClick = { vm.loadModel(file) }) { Text("Load") }
                    IconButton(onClick = { vm.deleteModel(file) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                }
            }
        }
    }
}
