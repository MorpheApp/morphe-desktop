/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.patching.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.morphe.gui.ui.screens.patching.LogEntry
import app.morphe.gui.ui.screens.patching.LogLevel
import app.morphe.gui.ui.theme.MorpheTheme

@Composable
internal fun LogEntryRow(
    entry: LogEntry
) {
    val bg = when (entry.level) {
        LogLevel.ERROR -> MorpheTheme.colors.danger.copy(alpha = 0.15f)
        LogLevel.WARNING -> MorpheTheme.colors.warning.copy(alpha = 0.12f)
        else -> Color.Transparent
    }
    
    val badgeBg = when (entry.level) {
        LogLevel.ERROR -> MorpheTheme.colors.danger.copy(alpha = 0.55f)
        LogLevel.WARNING -> MorpheTheme.colors.warning.copy(alpha = 0.3f)
        LogLevel.INFO -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    }
    
    val text = when (entry.level) {
        LogLevel.ERROR -> MorpheTheme.colors.danger
        LogLevel.WARNING -> MorpheTheme.colors.warning
        LogLevel.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    val badgeTextColor = when (entry.level) {
        LogLevel.ERROR -> MorpheTheme.colors.danger
        LogLevel.WARNING -> MorpheTheme.colors.warning
        LogLevel.INFO -> MaterialTheme.colorScheme.onSurface
    }
    
    val badge = when (entry.level) {
        LogLevel.ERROR -> "E"
        LogLevel.WARNING -> "W"
        LogLevel.INFO -> "I"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (bg != Color.Transparent) Modifier.background(bg) else Modifier)
            .padding(horizontal = 14.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(shape = MorpheTheme.shapes.extraSmall, color = badgeBg) {
            Text(
                text = badge,
                style = MorpheTheme.typography.codeSmall.copy(fontWeight = FontWeight.Medium),
                color = badgeTextColor,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        Text(
            text = entry.message,
            style = MorpheTheme.typography.codeSmall,
            color = text
        )
    }
}
