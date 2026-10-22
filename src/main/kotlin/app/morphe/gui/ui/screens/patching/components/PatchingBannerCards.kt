/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.patching.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.gui.ui.components.MorpheBadge
import app.morphe.gui.ui.screens.patching.PatchingUiState
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.ui.theme.panelFill
import app.morphe.gui.util.FormatUtils
import app.morphe.gui.util.currentLocale
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

enum class CardVariant { Start, Success }

@Composable
private fun PatcherInfoCard(
    title: String,
    variant: CardVariant,
    badge: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val accentColor = when (variant) {
        CardVariant.Start -> MorpheTheme.colors.primary
        CardVariant.Success -> progressGradient().second
    }
    Surface(
        modifier = Modifier.fillMaxWidth().border(1.dp, accentColor.copy(alpha = 0.55f), MorpheTheme.shapes.medium),
        shape = MorpheTheme.shapes.medium,
        color = panelFill,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MorpheTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = accentColor
                )
                if (badge != null) {
                    MorpheBadge(
                        text = badge,
                        containerColor = accentColor.copy(alpha = 0.18f),
                        contentColor = accentColor,
                    )
                }
            }

            HorizontalDivider(color = accentColor.copy(alpha = 0.15f), thickness = 1.dp)

            content()
        }
    }
}

@Composable
internal fun StartBannerCard(
    uiState: PatchingUiState
) {
    PatcherInfoCard(title = stringResource(Res.string.patching_banner_started_title), variant = CardVariant.Start) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BannerFieldCell(
                label = stringResource(Res.string.version),
                value = uiState.appVersion,
                modifier = Modifier.weight(1f))
            BannerFieldCell(
                label = stringResource(Res.string.patching_banner_apk_size),
                value = uiState.apkSizeMb,
                modifier = Modifier.weight(1f))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BannerFieldCell(
                label = stringResource(Res.string.patching_banner_patches),
                value = uiState.totalPatches.toString(),
                modifier = Modifier.weight(1f))
            BannerFieldCell(
                label = stringResource(Res.string.patching_banner_split_apk),
                value = if (uiState.isSplit) stringResource(Res.string.patching_banner_yes) else stringResource(Res.string.patching_banner_no),
                valueColor = if (uiState.isSplit) MaterialTheme.colorScheme.tertiary else null,
                modifier = Modifier.weight(1f))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val sourceName = uiState.patchesSourceName
            BannerFieldCell(
                label = sourceName.uppercase(),
                value = uiState.patchesVersion,
                modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.weight(1f))
        }

        HorizontalDivider(
            color = MorpheTheme.colors.primary.copy(alpha = 0.1f),
            thickness = 1.dp,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BannerFieldCell(
                label = stringResource(Res.string.patching_banner_desktop),
                value = uiState.desktopVersion,
                modifier = Modifier.weight(1f))
            BannerFieldCell(
                label = stringResource(Res.string.patching_banner_patcher),
                value = uiState.patcherVersion,
                modifier = Modifier.weight(1f))
        }

        val javaVendor = System.getProperty("java.vendor") ?: ""
        val vmName = System.getProperty("java.vm.name") ?: ""
        val javaName = when {
            javaVendor.contains("Adoptium", ignoreCase = true) -> "Temurin"
            javaVendor.contains("Azul", ignoreCase = true) -> "Azul"
            javaVendor.contains("Amazon", ignoreCase = true) -> "Corretto"
            javaVendor.contains("Microsoft", ignoreCase = true) -> "Microsoft"
            javaVendor.contains("Oracle", ignoreCase = true) -> "Oracle"
            javaVendor.contains("BellSoft", ignoreCase = true) -> "Liberica"
            vmName.contains("OpenJDK", ignoreCase = true) -> "OpenJDK"
            else -> javaVendor.takeIf { it.isNotBlank() } ?: "Java"
        }
        val runtimeInfo = "$javaName ${System.getProperty("java.version") ?: "?"}"

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BannerFieldCell(
                label = stringResource(Res.string.patching_banner_runtime),
                value = runtimeInfo,
                modifier = Modifier.weight(1f))
            BannerFieldCell(
                label = stringResource(Res.string.patching_banner_native_libs),
                value = uiState.nativeLibs,
                modifier = Modifier.weight(1f))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BannerFieldCell(
                label = stringResource(Res.string.patching_banner_os),
                value = uiState.androidVersion,
                modifier = Modifier.weight(1f))
            BannerFieldCell(
                label = stringResource(Res.string.patching_banner_arch),
                value = uiState.deviceManufacturer,
                modifier = Modifier.weight(1f))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BannerFieldCell(
                label = stringResource(Res.string.patching_banner_ram_free),
                value = uiState.ramFreeInfo,
                modifier = Modifier.weight(1f))
            BannerFieldCell(
                label = stringResource(Res.string.patching_banner_storage_free),
                value = uiState.storageFreeInfo,
                modifier = Modifier.weight(1f))
        }
    }
}

@Composable
internal fun SuccessSummaryCard(
    uiState: PatchingUiState
) {
    val avgMemory = if (uiState.heapSamples.isNotEmpty()) uiState.heapSamples.average().toInt() else 0
    val maxMemory = if (uiState.heapSamples.isNotEmpty()) uiState.heapSamples.maxOrNull() ?: 0 else 0

    PatcherInfoCard(title = stringResource(Res.string.patching_success_title), variant = CardVariant.Success, badge = "✓") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BannerFieldCell(
                    label = stringResource(Res.string.patching_success_output_size),
                    value = uiState.outputSizeMb ?: "?",
                    modifier = Modifier.weight(1f))
                BannerFieldCell(
                    label = stringResource(Res.string.patching_success_time),
                    value = uiState.elapsedSec ?: "?",
                    modifier = Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BannerFieldCell(
                    label = stringResource(Res.string.patching_success_memory_avg),
                    value = "$avgMemory MB",
                    modifier = Modifier.weight(1f))
                BannerFieldCell(
                    label = stringResource(Res.string.patching_success_memory_max),
                    value = "$maxMemory MB",
                    modifier = Modifier.weight(1f))
            }
            if (uiState.ioPeakKbPerSec > 0) {
                val peakRate = FormatUtils.formatTransferRate(uiState.ioPeakKbPerSec, currentLocale())
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BannerFieldCell(
                        label = stringResource(Res.string.patching_success_storage_io_peak),
                        value = peakRate,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun BannerFieldCell(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    valueColor: Color? = null
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label.uppercase(),
            style = MorpheTheme.typography.codeExtraSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.60f)
        )

        Text(
            text = value,
            style = MorpheTheme.typography.codeSmall,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
