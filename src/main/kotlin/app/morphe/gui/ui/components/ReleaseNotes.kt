/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.morphe.gui.ui.theme.MorpheTheme

@Composable
fun FormattedReleaseNotes(markdown: String, modifier: Modifier = Modifier) {
    val lines = parseMarkdown(markdown)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        lines.forEachIndexed { index, line ->
            when (line) {
                is MdLine.Header -> {
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                            thickness = 1.dp
                        )
                    }
                    Text(
                        text = line.text,
                        style = MorpheTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                is MdLine.SubHeader -> Text(
                    text = line.text,
                    style = MorpheTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                is MdLine.Bullet -> {
                    Row {
                        Text(
                            text = "•",
                            modifier = Modifier.alignByBaseline(),
                            style = MorpheTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = line.text,
                            modifier = Modifier.weight(1f).alignByBaseline(),
                            style = MorpheTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                is MdLine.Plain -> Text(
                    text = line.text,
                    style = MorpheTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private sealed class MdLine {
    data class Header(val text: String) : MdLine()
    data class SubHeader(val text: String) : MdLine()
    data class Bullet(val text: String) : MdLine()
    data class Plain(val text: String) : MdLine()
}

private fun parseMarkdown(markdown: String): List<MdLine> {
    return markdown.lines()
        .filter { it.isNotBlank() }
        .map { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("# ") -> MdLine.Header(cleanMarkdown(trimmed.removePrefix("# ")))
                trimmed.startsWith("## ") -> MdLine.Header(cleanMarkdown(trimmed.removePrefix("## ")))
                trimmed.startsWith("### ") -> MdLine.SubHeader(cleanMarkdown(trimmed.removePrefix("### ")))
                trimmed.startsWith("* ") -> MdLine.Bullet(cleanMarkdown(trimmed.removePrefix("* ")))
                trimmed.startsWith("- ") -> MdLine.Bullet(cleanMarkdown(trimmed.removePrefix("- ")))
                else -> MdLine.Plain(cleanMarkdown(trimmed))
            }
        }
}

/** Strips common markdown formatting and commit links to plain text. */
private fun cleanMarkdown(text: String): String {
    var result = text
    result = result.replace(Regex("""\(\[[\da-f]{7,}]\([^)]*\)\)"""), "")
    result = result.replace(Regex("""\[([^]]*?)]\([^)]*\)"""), "$1")
    result = result.replace(Regex("""\*\*(.+?)\*\*"""), "$1")
    result = result.replace(Regex("""\s+"""), " ").trim()
    return result
}
