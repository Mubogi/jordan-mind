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
import androidx.compose.material3.OutlinedButton
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
import com.jordanmind.agent.build.AppProjectBuilder
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

@Composable
fun BuildTab(vm: AppViewModel) {
    val scope = rememberCoroutineScope()
    val templates = AppProjectBuilder.templates

    var selected by remember { mutableStateOf(templates.first()) }
    var appName by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf("") }
    var features by remember { mutableStateOf("") }
    var log by remember { mutableStateOf("") }
    var lastProject by remember { mutableStateOf<File?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TabHeader(
            title = "Build Apps",
            subtitle = "Generates a complete Android project you can compile",
        )

        Column(
            Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Panel {
                SectionLabel("Template")
                templates.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { template ->
                            FilterChip(
                                selected = selected.id == template.id,
                                onClick = { selected = template },
                                label = { Text(template.label) },
                            )
                        }
                    }
                }
                MonoLine(selected.description)
            }

            Panel {
                SectionLabel("Describe your app")
                OutlinedTextField(
                    value = appName,
                    onValueChange = { appName = it },
                    label = { Text("App name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = summary,
                    onValueChange = { summary = it },
                    label = { Text("One-line description") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = features,
                    onValueChange = { features = it },
                    label = { Text("Features (one per line)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            scope.launch {
                                val name = appName.ifBlank { selected.defaultName }
                                log = "Generating $name…"
                                val featureList = features.lines().map { it.trim() }.filter { it.isNotBlank() }
                                val result = withContext(Dispatchers.IO) {
                                    AppProjectBuilder.generate(
                                        parentDir = vm.workspace.projects,
                                        template = selected,
                                        appName = name,
                                        summary = summary.ifBlank { selected.description },
                                        features = featureList,
                                    )
                                }
                                result
                                    .onSuccess {
                                        lastProject = it
                                        log = "Created ${vm.workspace.label(it)}\n" +
                                            it.walkTopDown().filter { f -> f.isFile }.count() +
                                            " files written."
                                    }
                                    .onFailure { log = "Failed: ${it.message}" }
                            }
                        },
                    ) { Text("Generate project") }

                    Button(
                        enabled = lastProject != null,
                        onClick = {
                            val project = lastProject ?: return@Button
                            scope.launch {
                                log = "Zipping…"
                                val zip = File(vm.workspace.builtApks, "${project.name}.zip")
                                val result = withContext(Dispatchers.IO) {
                                    FileTools.zip(project, zip)
                                }
                                log = result.fold(
                                    { "Exported ${vm.workspace.label(zip)} (${FileTools.humanSize(zip.length())})" },
                                    { "Zip failed: ${it.message}" },
                                )
                            }
                        },
                    ) { Text("Export ZIP") }
                }
            }

            Panel {
                SectionLabel("Log")
                Text(
                    log.ifBlank { "Nothing generated yet." },
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Panel {
                SectionLabel("How these get built")
                MonoLine(
                    "Jordan Mind writes real Gradle projects — source, manifest, resources, README. " +
                        "It does not compile APKs on the phone: that needs AGP, AAPT2 and a full JVM " +
                        "class library, which do not fit alongside the model. Open the exported folder " +
                        "in Android Studio, or run ./gradlew assembleDebug."
                )
            }
        }
    }
}
