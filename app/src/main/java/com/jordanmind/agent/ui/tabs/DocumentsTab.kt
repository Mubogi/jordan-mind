package com.jordanmind.agent.ui.tabs

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jordanmind.agent.tools.DocWriter
import com.jordanmind.agent.tools.FileTools
import com.jordanmind.agent.ui.AppViewModel
import com.jordanmind.agent.ui.MonoLine
import com.jordanmind.agent.ui.Panel
import com.jordanmind.agent.ui.SectionLabel
import com.jordanmind.agent.ui.TabHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class DocKind(val label: String, val extension: String) {
    PDF("PDF", "pdf"),
    DOCX("Word", "docx"),
    XLSX("Excel", "xlsx"),
    PPTX("Slides", "pptx"),
    TXT("Text", "txt"),
}

@Composable
fun DocumentsTab(vm: AppViewModel) {
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf(DocKind.PDF) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var rows by remember { mutableStateOf("Item,Quantity,Price") }
    var bullets by remember { mutableStateOf("") }
    var log by remember { mutableStateOf("") }
    var useModel by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TabHeader(
            title = "Documents",
            subtitle = "Write PDF, Word, Excel, Slides and text files offline",
        )

        Column(
            Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Panel {
                SectionLabel("Format")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DocKind.entries.forEach { option ->
                        FilterChip(
                            selected = kind == option,
                            onClick = { kind = option },
                            label = { Text(option.label) },
                        )
                    }
                }
            }

            Panel {
                SectionLabel("Content")
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (kind == DocKind.XLSX) {
                    OutlinedTextField(
                        value = rows,
                        onValueChange = { rows = it },
                        label = { Text("Rows (comma separated, one row per line)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                    )
                } else if (kind == DocKind.PPTX) {
                    OutlinedTextField(
                        value = bullets,
                        onValueChange = { bullets = it },
                        label = { Text("Bullets (one per line)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                    )
                } else {
                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it },
                        label = { Text("Body") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 6,
                    )
                }
            }

            Panel {
                SectionLabel("Assistant")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = useModel,
                        onClick = { useModel = !useModel },
                        label = { Text("Draft body with Qwen") },
                    )
                    Button(
                        enabled = useModel && title.isNotBlank(),
                        onClick = {
                            vm.runOnce(
                                prompt = "Write the body of a ${kind.label} document titled " +
                                    "\"$title\". Plain text only, no markdown, no headings markup.",
                            ) { answer -> body = answer }
                        },
                    ) { Text("Generate draft") }
                }
                MonoLine("Drafting uses the loaded model. Leave off to write it yourself.")
            }

            Panel {
                Button(
                    onClick = {
                        val safeTitle = title.ifBlank { "Untitled" }
                        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                        val fileName = "${safeTitle.replace(Regex("[^A-Za-z0-9._-]"), "_")}-$stamp.${kind.extension}"
                        val target = File(vm.workspace.documents, fileName)
                        scope.launch {
                            log = "Writing $fileName…"
                            val result = withContext(Dispatchers.IO) {
                                runCatching {
                                    when (kind) {
                                        DocKind.PDF -> DocWriter.writePdf(target, safeTitle, body, vm.workspace.label(target))
                                        DocKind.DOCX -> DocWriter.writeDocx(target, safeTitle, body, vm.workspace.label(target))
                                        DocKind.TXT -> DocWriter.writeTxt(target, body)
                                        DocKind.XLSX -> DocWriter.writeXlsx(
                                            target,
                                            safeTitle,
                                            rows.lines().filter { it.isNotBlank() }.map { it.split(",").map(String::trim) },
                                        )
                                        DocKind.PPTX -> DocWriter.writePptx(
                                            target,
                                            safeTitle,
                                            bullets.lines().map { it.trim() }.filter { it.isNotBlank() },
                                        )
                                    }
                                    target
                                }
                            }
                            log = result.fold(
                                { "Saved ${vm.workspace.label(it)} (${FileTools.humanSize(it.length())})" },
                                { "Failed: ${it.message}" },
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Create ${kind.label}") }

                Text(
                    log.ifBlank { "Files are saved to JordanMind/Documents." },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
