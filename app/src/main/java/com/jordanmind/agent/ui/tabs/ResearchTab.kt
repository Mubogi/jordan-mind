package com.jordanmind.agent.ui.tabs

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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jordanmind.agent.data.RagStore
import com.jordanmind.agent.engine.LlmState
import com.jordanmind.agent.ui.AppViewModel
import com.jordanmind.agent.ui.MonoLine
import com.jordanmind.agent.ui.Panel
import com.jordanmind.agent.ui.SectionLabel
import com.jordanmind.agent.ui.TabHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ResearchTab(vm: AppViewModel) {
    val scope = rememberCoroutineScope()
    val state by vm.llmState.collectAsStateWithLifecycle()

    var documents by remember { mutableStateOf(emptyList<com.jordanmind.agent.data.RagDb.DocumentRow>()) }
    var query by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf(emptyList<RagStore.Hit>()) }
    var log by remember { mutableStateOf("") }

    fun reload() {
        documents = vm.rag.documents()
        log = "Index: ${vm.rag.chunkCount()} passages from ${documents.size} document(s)"
    }

    LaunchedEffect(Unit) { reload() }

    val importer = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            var added = 0
            uris.forEach { uri ->
                val name = uri.lastPathSegment?.substringAfterLast('/') ?: "document"
                val local = File(vm.workspace.knowledge, name)
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        vm.workspace.knowledge.mkdirs()
                        vm.getApplication<android.app.Application>().contentResolver
                            .openInputStream(uri)?.use { input ->
                                local.outputStream().use { input.copyTo(it) }
                            }
                        vm.rag.addDocument(local).getOrThrow()
                    }
                }
                added += ok.getOrDefault(0)
            }
            reload()
            log = "Indexed $added new passage(s). $log"
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TabHeader(
            title = "Research",
            subtitle = "Ask questions against documents stored on this device",
        )

        Column(
            Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Panel {
                SectionLabel("Knowledge base")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { importer.launch(arrayOf("application/pdf", "text/*", "*/*")) }) {
                        Text("Add documents")
                    }
                    Button(onClick = { reload() }) { Text("Refresh") }
                }
                MonoLine(log.ifBlank { "No documents indexed yet." })
                documents.forEach { document ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            "${document.name} · ${document.chunks} passages",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        IconButton(onClick = {
                            vm.rag.deleteDocument(document.id)
                            reload()
                        }) { Text("✕") }
                    }
                }
            }

            Panel {
                SectionLabel("Ask")
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Your question") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    enabled = query.isNotBlank() && state is LlmState.Ready,
                    onClick = {
                        scope.launch {
                            answer = "Searching…"
                            val found = withContext(Dispatchers.IO) { vm.rag.search(query, limit = 4) }
                            hits = found
                            if (found.isEmpty()) {
                                answer = "Nothing relevant in the indexed documents."
                                return@launch
                            }
                            val context = found.joinToString("\n\n---\n\n") { it.text.take(1200) }
                            val prompt = buildString {
                                append("Answer the question using only the excerpts below. ")
                                append("If they do not contain the answer, say so plainly.\n\n")
                                append("Excerpts:\n").append(context)
                                append("\n\nQuestion: ").append(query)
                            }
                            vm.runOnce(
                                prompt = prompt,
                                system = "You are a careful research assistant. Ground every claim in the " +
                                    "provided excerpts and do not invent details.",
                            ) { result -> answer = result }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Search and answer") }

                if (answer.isNotBlank()) {
                    Text(answer, style = MaterialTheme.typography.bodyMedium)
                }
                if (hits.isNotEmpty()) {
                    SectionLabel("Retrieved passages")
                    hits.forEach { hit ->
                        Text(
                            "${hit.document} · score %.2f".format(hit.score),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            hit.text.take(220) + "…",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

