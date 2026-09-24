/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.patching.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.ui.screens.patching.LogEntry
import app.morphe.gui.ui.screens.patching.LogLevel
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheMono

@Composable
internal fun LogEntryRow(
    entry: LogEntry,
    font: FontFamily
) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheMono.current
    val accents = LocalMorpheAccents.current
    
    val bg = when (entry.level) {
        LogLevel.ERROR -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
        LogLevel.WARNING -> accents.warning.copy(alpha = 0.12f)
        else -> Color.Transparent
    }
    
    val badgeBg = when (entry.level) {
        LogLevel.ERROR -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
        LogLevel.WARNING -> accents.warning.copy(alpha = 0.3f)
        LogLevel.INFO -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
    }
    
    val text = when (entry.level) {
        LogLevel.ERROR -> MaterialTheme.colorScheme.error
        LogLevel.WARNING -> accents.warning
        LogLevel.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    val badgeTextColor = when (entry.level) {
        LogLevel.ERROR -> MaterialTheme.colorScheme.error
        LogLevel.WARNING -> accents.warning
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
        Surface(shape = RoundedCornerShape(corners.small), color = badgeBg) {
            Text(
                text = badge,
                fontFamily = mono,
                fontWeight = FontWeight.Medium,
                color = badgeTextColor,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                fontSize = 10.sp
            )
        }

        Text(
            text = entry.message,
            fontFamily = mono,
            fontWeight = FontWeight.Normal,
            color = text,
            lineHeight = 17.sp,
            fontSize = 11.sp
        )
    }
}
