/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** Supported target architectures for library stripping. */
internal val STRIP_LIBS_ARCHS = listOf(
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
    searchQuery: String = "",
    forceShowAll: Boolean = false,
) {
    val descText = stringResource(Res.string.settings_strip_libs_desc)
    val archDescriptions = STRIP_LIBS_ARCHS.map { (arch, descRes) -> arch to stringResource(descRes) }
    val showAll = forceShowAll || searchQuery.isBlank()

    SettingsGroup {
        blockItem(visible = showAll || matchesSettingQuery(searchQuery, descText)) {
            Text(
                text = descText,
                style = MorpheTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        archDescriptions.forEach { (arch, desc) ->
            val checked = arch in keepArchitectures
            toggleItem(
                key = arch,
                visible = showAll || matchesSettingQuery(searchQuery, arch, desc),
                title = arch,
                description = desc,
                checked = checked,
                enabled = enabled,
                onCheckedChange = { keepIt ->
                    val updated = if (keepIt) keepArchitectures + arch else keepArchitectures - arch
                    onChange(updated)
                },
            )
        }
    }
}
