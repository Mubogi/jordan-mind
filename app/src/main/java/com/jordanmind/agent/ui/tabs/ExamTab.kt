package com.jordanmind.agent.ui.tabs

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jordanmind.agent.engine.LlmState
import com.jordanmind.agent.tools.DocWriter
import com.jordanmind.agent.tools.FileTools
import com.jordanmind.agent.tools.Ocr
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

@Composable
fun ExamTab(vm: AppViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by vm.llmState.collectAsStateWithLifecycle()

    var question by remember { mutableStateOf("") }
    var solution by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("Mathematics") }
    var working by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            status = "Reading image…"
            withContext(Dispatchers.IO) { runCatching { Ocr.recognize(context, uri) } }
                .onSuccess {
                    question = it
                    status = "Extracted ${it.length} characters."
                }
                .onFailure { status = "OCR failed: ${it.message}" }
        }
    }

    fun solve() {
        if (question.isBlank() || state !is LlmState.Ready) return
        scope.launch {
            working = true
            solution = ""
            status = "Solving…"
            val prompt = buildString {
                append("Solve this $subject problem. Show every step, then give the final answer on ")
                append("its own line prefixed with \"Answer:\". If the problem statement is unclear, ")
                append("state the assumption you are making.\n\n").append(question)
            }
            vm.runOnce(
                prompt = prompt,
                system = "You are a patient tutor. Work step by step, keep arithmetic explicit, and " +
                    "never skip a step. If you are unsure, say so rather than guessing.",
            ) { result ->
                solution = result
                working = false
                status = "Done."
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TabHeader(
            title = "Exam",
            subtitle = "Photograph or type a question and get a worked solution",
        )

        Column(
            Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Panel {
                SectionLabel("Question")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { Text("Photo / image") }
                    listOf("Mathematics", "Physics", "Chemistry", "Biology", "Other").forEach { option ->
                        FilterChip(
                            selected = subject == option,
                            onClick = { subject = option },
                            label = { Text(option) },
                        )
                    }
                }
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text("Question text") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                )
                Button(
                    enabled = question.isNotBlank() && state is LlmState.Ready && !working,
                    onClick = { solve() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (working) "Solving…" else "Solve step by step") }
                if (status.isNotBlank()) MonoLine(status)
            }

            if (solution.isNotBlank()) {
                Panel {
                    SectionLabel("Worked solution")
                    Text(solution, style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            scope.launch {
                                val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                                val pdf = File(vm.workspace.documents, "solution-$stamp.pdf")
                                val docx = File(vm.workspace.documents, "solution-$stamp.docx")
                                withContext(Dispatchers.IO) {
                                    DocWriter.writePdf(
                                        pdf,
                                        "Worked Solution — $subject",
                                        "Question:\n$question\n\nSolution:\n$solution",
                                        "Jordan Mind · offline",
                                    )
                                    DocWriter.writeDocx(
                                        docx,
                                        "Worked Solution — $subject",
                                        "Question:\n$question\n\nSolution:\n$solution",
                                        "Jordan Mind · offline",
                                    )
                                }
                                status = "Saved ${pdf.name} and ${docx.name} " +
                                    "(${FileTools.humanSize(pdf.length())})"
                            }
                        }) { Text("Save as PDF") }
                        Button(onClick = {
                            scope.launch {
                                val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                                val docx = File(vm.workspace.documents, "solution-$stamp.docx")
                                withContext(Dispatchers.IO) {
                                    DocWriter.writeDocx(
                                        docx,
                                        "Worked Solution — $subject",
                                        "Question:\n$question\n\nSolution:\n$solution",
                                        "Jordan Mind · offline",
                                    )
                                }
                                status = "Saved ${docx.name} (${FileTools.humanSize(docx.length())})"
                            }
                        }) { Text("Save as Word") }
                    }
                }
            }

            Panel {
                SectionLabel("What this can and cannot do")
                MonoLine(
                    "OCR is on-device ML Kit. The solver is the loaded Qwen model. A 0.5B model handles " +
                        "arithmetic, algebra and short physics well; long multi-part proofs will be shaky. " +
                        "Load the 1.5B model for harder papers."
                )
            }
        }
    }
}
