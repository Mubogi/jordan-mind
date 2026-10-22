package com.jordanmind.agent.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

enum class Tab(val label: String, val icon: ImageVector) {
    CHAT("Chat", Icons.Filled.Chat),
    BUILD("Build", Icons.Filled.Build),
    DOCS("Docs", Icons.Filled.Description),
    FILES("Files", Icons.Filled.Folder),
    RESEARCH("Research", Icons.Filled.TravelExplore),
    EXAM("Exam", Icons.Filled.School),
}

@Composable
fun JordanBottomBar(selected: Tab, onSelect: (Tab) -> Unit) {
    NavigationBar {
        Tab.entries.forEach { entry ->
            NavigationBarItem(
                selected = selected == entry,
                onClick = { onSelect(entry) },
                icon = { Icon(entry.icon, contentDescription = entry.label) },
                label = { Text(entry.label) },
                colors = NavigationBarItemDefaults.colors(),
            )
        }
    }
}
