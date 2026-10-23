package com.jordanmind.agent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

enum class Tab(val label: String, val icon: ImageVector) {
    CHAT("Chat", Icons.Filled.Chat),
    AGENT("Agent", Icons.Filled.Bolt),
    BUILD("Build", Icons.Filled.Build),
    DOCS("Docs", Icons.Filled.Description),
    FILES("Files", Icons.Filled.Folder),
    RESEARCH("Research", Icons.Filled.TravelExplore),
    EXAM("Exam", Icons.Filled.School),
}

/**
 * Seven destinations is too many for a bottom bar, where Material caps comfortable use at five.
 * A scrollable chip rail keeps every tab one tap away and leaves the full height to content.
 */
@Composable
fun JordanTabRail(selected: Tab, onSelect: (Tab) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(Tab.entries) { entry ->
            val active = entry == selected
            FilterChip(
                selected = active,
                onClick = { onSelect(entry) },
                label = { Text(entry.label) },
                leadingIcon = { Icon(entry.icon, contentDescription = null) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}
