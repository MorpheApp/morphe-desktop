/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** Supported target architectures for library stripping. */
private val STRIP_LIBS_ARCHS = listOf(
    "arm64-v8a" to Res.string.settings_strip_libs_arm64,
    "armeabi-v7a" to Res.string.settings_strip_libs_armeabi,
    "x86_64" to Res.string.settings_strip_libs_x86_64,
    "x86" to Res.string.settings_strip_libs_x86
)

@Composable
internal fun StripLibsSection(
    keepArchitectures: Set<String>,
    onChange: (Set<String>) -> Unit,
    enabled: Boolean = true,
    expanded: Boolean = false,
    icon: ImageVector? = null,
    onExpandedChange: (Boolean) -> Unit = {}
) {
    CollapsibleSection(
        title = stringResource(Res.string.settings_section_strip_libs),
        expanded = expanded,
        icon = icon,
        onExpandedChange = onExpandedChange
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(Res.string.settings_strip_libs_desc),
                style = MorpheTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            STRIP_LIBS_ARCHS.forEach { (arch, descRes) ->
                val checked = arch in keepArchitectures
                SettingToggleRow(
                    label = arch,
                    description = stringResource(descRes),
                    checked = checked,
                    onCheckedChange = { keepIt ->
                        val updated = if (keepIt) keepArchitectures + arch
                                      else keepArchitectures - arch
                        onChange(updated)
                    },
                    enabled = enabled
                )
            }
        }
    }
}
