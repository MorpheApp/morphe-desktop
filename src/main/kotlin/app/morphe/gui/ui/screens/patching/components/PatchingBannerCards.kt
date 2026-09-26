/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.patching.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.ui.screens.patching.PatchingUiState
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.ui.theme.LocalMorpheMono
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
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current
    val accentColor = when (variant) {
        CardVariant.Start -> MaterialTheme.colorScheme.primary
        CardVariant.Success -> progressGradient().second
    }
    Surface(
        modifier = Modifier.fillMaxWidth().border(1.dp, accentColor.copy(alpha = 0.55f), RoundedCornerShape(corners.medium)),
        shape = RoundedCornerShape(corners.medium),
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
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = font,
                    color = accentColor
                )
                if (badge != null) {
                    Surface(
                        shape = RoundedCornerShape(corners.small),
                        color = accentColor.copy(alpha = 0.18f)
                    ) {
                        Text(
                            text = badge,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = font,
                            color = accentColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = accentColor.copy(alpha = 0.15f), thickness = 1.dp)

            content()
        }
    }
}

@Composable
internal fun StartBannerCard(
    uiState: PatchingUiState,
    font: FontFamily
) {
    PatcherInfoCard(title = stringResource(Res.string.patching_banner_started_title), variant = CardVariant.Start) {
        // App Section (Top)
        // Row 1: APP VERSION, APK SIZE
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BannerFieldCell(
                label = stringResource(Res.string.patching_banner_app_version),
                value = uiState.appVersion,
                font = font,
                modifier = Modifier.weight(1f))
            BannerFieldCell(
                label = stringResource(Res.string.patching_banner_apk_size),
                value = uiState.apkSizeMb,
                font = font,
                modifier = Modifier.weight(1f))
        }

        // Row 2: PATCHES, SPLIT APK
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BannerFieldCell(
                label = stringResource(Res.string.patching_banner_patches),
                value = uiState.totalPatches.toString(),
                font = font,
                modifier = Modifier.weight(1f))
            BannerFieldCell(
                label = stringResource(Res.string.patching_banner_split_apk),
                value = if (uiState.isSplit) stringResource(Res.string.patching_banner_yes) else stringResource(Res.string.patching_banner_no),
                valueColor = if (uiState.isSplit) MaterialTheme.colorScheme.tertiary else null,
                font = font,
                modifier = Modifier.weight(1f))
        }

        // Row 3: PATCHES SOURCE
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val sourceName = uiState.patchesSourceName
            val labelText = if (sourceName.equals("Morphe", ignoreCase = true)) stringResource(Res.string.patching_banner_morphe_patches) else sourceName
            BannerFieldCell(
                label = labelText,
                value = uiState.patchesVersion,
                font = font,
                modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.weight(1f))
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
            thickness = 1.dp,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        
        // Environment Section (Bottom)
        // Row 4: DESKTOP, PATCHER
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BannerFieldCell(
                label = stringResource(Res.string.patching_banner_desktop),
                value = uiState.desktopVersion,
                font = font,
                modifier = Modifier.weight(1f))
            BannerFieldCell(
                label = stringResource(Res.string.patching_banner_patcher),
                value = uiState.patcherVersion,
                font = font,
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

        // Row 5: RUNTIME, NATIVE LIBS
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BannerFieldCell(
                label = stringResource(Res.string.patching_banner_runtime),
                value = runtimeInfo,
                font = font,
                modifier = Modifier.weight(1f))
            BannerFieldCell(
                label = stringResource(Res.string.patching_banner_native_libs),
                value = uiState.nativeLibs,
                font = font,
                modifier = Modifier.weight(1f))
        }

        // Row 6: OS, ARCH
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BannerFieldCell(
                label = stringResource(Res.string.patching_banner_os),
                value = uiState.androidVersion,
                font = font,
                modifier = Modifier.weight(1f))
            BannerFieldCell(
                label = stringResource(Res.string.patching_banner_arch),
                value = uiState.deviceManufacturer,
                font = font,
                modifier = Modifier.weight(1f))
        }

        // Row 7: RAM FREE, STORAGE FREE
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            BannerFieldCell(
                label = stringResource(Res.string.patching_banner_ram_free),
                value = uiState.ramFreeInfo,
                font = font,
                modifier = Modifier.weight(1f))
            BannerFieldCell(
                label = stringResource(Res.string.patching_banner_storage_free),
                value = uiState.storageFreeInfo,
                font = font,
                modifier = Modifier.weight(1f))
        }
    }
}

@Composable
internal fun SuccessSummaryCard(
    uiState: PatchingUiState,
    font: FontFamily
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
                    font = font,
                    modifier = Modifier.weight(1f))
                BannerFieldCell(
                    label = stringResource(Res.string.patching_success_time),
                    value = uiState.elapsedSec ?: "?",
                    font = font,
                    modifier = Modifier.weight(1f))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BannerFieldCell(
                    label = stringResource(Res.string.patching_success_memory_avg),
                    value = "$avgMemory MB",
                    font = font,
                    modifier = Modifier.weight(1f))
                BannerFieldCell(
                    label = stringResource(Res.string.patching_success_memory_max),
                    value = "$maxMemory MB",
                    font = font,
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
                        font = font,
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
    valueColor: Color? = null,
    font: FontFamily
) {
    val mono = LocalMorpheMono.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label.uppercase(),
            fontFamily = font,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 9.sp
        )

        Text(
            text = value,
            fontFamily = mono,
            fontWeight = FontWeight.Normal,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
