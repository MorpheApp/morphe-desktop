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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Architectures exposed in the strip libs settings. Each entry has the
 * patcher-facing value (matching CpuArchitecture.arch) and a short display name.
 * Only modern arches are listed. Legacy mips and armeabi are intentionally omitted.
 */
private val STRIP_LIBS_ARCHS = listOf(
    "arm64-v8a" to "ARM 64-bit (most modern phones)",
    "armeabi-v7a" to "ARM 32-bit (older phones)",
    "x86_64" to "Intel 64-bit (emulators / Chromebooks)",
    "x86" to "Intel 32-bit (legacy emulators)"
)

@Composable
internal fun StripLibsSection(
    keepArchitectures: Set<String>,
    onChange: (Set<String>) -> Unit,
    font: FontFamily,
    accentColor: Color,
    enabled: Boolean = true,
    expanded: Boolean = false,
    icon: ImageVector? = null,
    onExpandedChange: (Boolean) -> Unit = {}
) {
    CollapsibleSection(
        title = "Strip libs",
        font = font,
        expanded = expanded,
        icon = icon,
        onExpandedChange = onExpandedChange
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Uncheck architectures you don't need. When patching, the output APK will keep only the architectures present in the APK AND in this list. If none overlap, nothing is stripped to avoid broken APKs",
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = font
            )
            STRIP_LIBS_ARCHS.forEach { (arch, description) ->
                val checked = arch in keepArchitectures
                SettingToggleRow(
                    label = arch,
                    description = description,
                    checked = checked,
                    onCheckedChange = { keepIt ->
                        val updated = if (keepIt) keepArchitectures + arch
                                      else keepArchitectures - arch
                        onChange(updated)
                    },
                    accentColor = accentColor,
                    font = font,
                    enabled = enabled
                )
            }
        }
    }
}
