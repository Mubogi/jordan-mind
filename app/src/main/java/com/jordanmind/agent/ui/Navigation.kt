package com.jordanmind.agent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.jordanmind.agent.ui.theme.Accent

/** Lets UI tests scroll the rail to chips that are off-screen and therefore not composed yet. */
const val TabRailTestTag = "jordan-tab-rail"

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
        modifier = Modifier.fillMaxWidth().testTag(TabRailTestTag),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(Tab.entries) { entry ->
            val active = entry == selected
            if (active) {
                Row(
                    Modifier
                        .background(
                            brush = Brush.linearGradient(listOf(Accent.Gold, Accent.Amber)),
                            shape = RoundedCornerShape(999.dp),
                        )
                        .clickable { onSelect(entry) }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        entry.icon,
                        contentDescription = null,
                        tint = Color(0xFF241A00),
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        entry.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0xFF241A00),
                    )
                }
            } else {
                Row(
                    Modifier
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(999.dp),
                        )
                        .clickable { onSelect(entry) }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        entry.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        entry.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
