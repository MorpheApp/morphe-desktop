/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.result.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.data.model.SupportedApp
import app.morphe.gui.ui.components.MorpheActionButton
import app.morphe.gui.ui.components.handCursor
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.ui.theme.MorpheCornerStyle
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * Route the patched app's web links to it (and optionally stop the stock app
 * from grabbing them). Shown only once the patched app is installed on a ready
 * device. The stock-disable checkbox appears only when a rename patch was used
 * (a distinct [stockPackage]). On-device, [app.morphe.gui.util.AdbManager.setLinkHandling] still
 * verifies the stock app is actually installed before touching it.
 */
@Composable
internal fun LinkHandlingSection(
    patchedPackage: String,
    stockPackage: String?,
    disableStockLinks: Boolean,
    onToggleDisableStock: (Boolean) -> Unit,
    isApplying: Boolean,
    progress: String,
    error: String?,
    success: Boolean,
    selectedDeviceName: String?,
    corners: MorpheCornerStyle = LocalMorpheCorners.current,
    font: FontFamily = LocalMorpheFont.current,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    onApply: () -> Unit,
    onRestore: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accents = LocalMorpheAccents.current
    Box(
        modifier = modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(corners.medium))
            .border(1.dp, borderColor, RoundedCornerShape(corners.medium))
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = stringResource(Res.string.result_link_section_title),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = font,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.result_link_section_subtitle),
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = font,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (stockPackage != null) {
                Spacer(Modifier.height(12.dp))
                val stockName = SupportedApp.getDisplayName(stockPackage)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(corners.small))
                        .handCursor(!isApplying)
                        .clickable(enabled = !isApplying) { onToggleDisableStock(!disableStockLinks) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = disableStockLinks,
                        onCheckedChange = { onToggleDisableStock(it) },
                        enabled = !isApplying,
                        colors = CheckboxDefaults.colors(checkedColor = accents.secondary),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(Res.string.result_link_stop_stock, stockName),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        fontFamily = font,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            when {
                error != null -> {
                    Text(
                        text = error,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        fontFamily = font,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    SecondaryActionChip(text = stringResource(Res.string.dismiss), corners = corners, font = font, onClick = onDismissError)
                }

                isApplying -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = accents.primary
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = progress.ifEmpty { stringResource(Res.string.result_link_applying) },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = font,
                            color = accents.primary
                        )
                    }
                }

                success -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = MorpheIcons.CheckCircle,
                            contentDescription = null,
                            tint = accents.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = progress.ifEmpty { stringResource(Res.string.result_screen_links_routed_label) },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = font,
                            color = accents.secondary,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        SecondaryActionChip(text = stringResource(Res.string.result_link_restore), corners = corners, font = font, onClick = onRestore)
                    }
                }

                else -> {
                    MorpheActionButton(
                        label = stringResource(Res.string.result_link_open_with_patched),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onApply,
                    )
                }
            }
        }
    }
}

/** Small bordered text button used for secondary actions (Dismiss/Restore). */
@Composable
private fun SecondaryActionChip(
    text: String,
    corners: MorpheCornerStyle,
    font: FontFamily,
    onClick: () -> Unit,
) {
    val hover = remember { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .hoverable(hover)
            .clip(RoundedCornerShape(corners.small))
            .border(
                1.dp,
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isHovered) 0.3f else 0.12f),
                RoundedCornerShape(corners.small)
            )
            .handCursor()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = font,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
