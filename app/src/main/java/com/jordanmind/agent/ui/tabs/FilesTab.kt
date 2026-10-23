package com.jordanmind.agent.ui.tabs


import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jordanmind.agent.tools.FileTools
import com.jordanmind.agent.ui.AppViewModel
import com.jordanmind.agent.ui.TabHeader
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
@Composable
fun FilesTab(vm: AppViewModel) {
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf(vm.workspace.root) }
    var query by remember { mutableStateOf("") }
    var dialog by remember { mutableStateOf<Dialog?>(null) }
    var refresh by remember { mutableStateOf(0) }

    val entries = remember(current, query, refresh) {
        if (query.isBlank()) FileTools.list(current)
        else FileTools.search(vm.workspace.root, query)
    }

    Column(Modifier.fillMaxSize()) {
        TabHeader(
            title = "Files",
            subtitle = vm.workspace.label(current),
            action = "New" to { dialog = Dialog.NewFolder },
        )

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (current != vm.workspace.root) {
                IconButton(onClick = { current = current.parentFile ?: vm.workspace.root; query = "" }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Up")
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search workspace") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(entries) { entry ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (entry.isDirectory) {
                                current = entry.file
                                query = ""
                            } else {
                                dialog = Dialog.Info(entry.file)
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (entry.isDirectory) Icons.Filled.Folder else Icons.Filled.InsertDriveFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(entry.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            entry.sizeLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { dialog = Dialog.Rename(entry.file) }) {
                        Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = "Rename")
                    }
                    IconButton(onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { FileTools.delete(entry.file) }
                            vm.notify("Deleted ${entry.name}")
                            refresh++
                        }
                    }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                }
            }
            if (entries.isEmpty()) {
                item {
                    Text(
                        if (query.isBlank()) "This folder is empty." else "No matches.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }

    dialog?.let { active ->
        when (active) {
            is Dialog.NewFolder -> NameDialog(
                titleText = "New folder",
                initial = "",
                onDismiss = { dialog = null },
                onConfirm = { name ->
                    FileTools.createFolder(current, name)
                        .onSuccess { vm.notify("Created $name") }
                        .onFailure { vm.notify(it.message ?: "Failed") }
                    dialog = null
                    refresh++
                },
            )

            is Dialog.Rename -> NameDialog(
                titleText = "Rename",
                initial = active.file.name,
                onDismiss = { dialog = null },
                onConfirm = { name ->
                    FileTools.rename(active.file, name)
                        .onSuccess { vm.notify("Renamed to $name") }
                        .onFailure { vm.notify(it.message ?: "Failed") }
                    dialog = null
                    refresh++
                },
            )

            is Dialog.Info -> AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text(active.file.name) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Size: ${FileTools.humanSize(active.file.length())}")
                        Text("Path: ${vm.workspace.label(active.file)}")
                        if (active.file.extension.lowercase() in setOf("txt", "md", "json", "csv")) {
                            Text(
                                active.file.readText().take(600),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            val zip = File(vm.workspace.builtApks, "${active.file.name}.zip")
                            withContext(Dispatchers.IO) { FileTools.zip(active.file, zip) }
                            vm.notify("Zipped to ${vm.workspace.label(zip)}")
                            refresh++
                        }
                        dialog = null
                    }) { Text("Zip") }
                },
                dismissButton = { TextButton(onClick = { dialog = null }) { Text("Close") } },
            )
        }
    }
}

private sealed interface Dialog {
    data object NewFolder : Dialog
    data class Rename(val file: File) : Dialog
    data class Info(val file: File) : Dialog
}

@Composable
private fun NameDialog(
    titleText: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(titleText) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                label = { Text("Name") },
            )
        },
        confirmButton = {
            TextButton(
                enabled = value.isNotBlank(),
                onClick = { onConfirm(value.trim()) },
            ) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
