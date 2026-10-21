/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.result.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.morphe.engine.model.SupportedApp
import app.morphe.gui.ui.components.*
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.*
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** Section to configure default link handling domains on the connected device. */
@Composable
internal fun LinkHandlingSection(
    stockPackage: String?,
    disableStockLinks: Boolean,
    onToggleDisableStock: (Boolean) -> Unit,
    isApplying: Boolean,
    progress: String,
    error: String?,
    success: Boolean,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
    onApply: () -> Unit,
    onRestore: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .widthIn(max = 520.dp)
            .fillMaxWidth()
            .clip(MorpheTheme.shapes.medium)
            .border(1.dp, borderColor, MorpheTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = stringResource(Res.string.result_link_section_title),
                style = MorpheTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.result_link_section_subtitle),
                style = MorpheTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (stockPackage != null) {
                Spacer(Modifier.height(12.dp))
                val stockName = SupportedApp.getDisplayName(stockPackage)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MorpheTheme.shapes.small)
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
                        colors = CheckboxDefaults.colors(checkedColor = MorpheTheme.colors.secondary),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(Res.string.result_link_stop_stock, stockName),
                        style = MorpheTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            when {
                error != null -> {
                    Text(
                        text = error,
                        style = MorpheTheme.typography.labelSmall,
                        color = MorpheTheme.colors.danger,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    MorpheButton(
                        label = stringResource(Res.string.dismiss),
                        variant = MorpheButtonVariant.OUTLINED,
                        size = MorpheButtonSize.COMPACT,
                        onClick = onDismissError
                    )
                }

                isApplying -> {
                    ResultProgressRow(progress.ifEmpty { stringResource(Res.string.result_link_applying) })
                }

                success -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = MorpheIcons.CheckCircle,
                            contentDescription = null,
                            tint = MorpheTheme.colors.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = progress.ifEmpty { stringResource(Res.string.result_screen_links_routed_label) },
                            style = MorpheTheme.typography.labelSmall,
                            color = MorpheTheme.colors.secondary,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        MorpheButton(
                            label = stringResource(Res.string.result_link_restore),
                            variant = MorpheButtonVariant.OUTLINED,
                            size = MorpheButtonSize.COMPACT,
                            onClick = onRestore
                        )
                    }
                }

                else -> {
                    MorpheButton(
                        label = stringResource(Res.string.result_link_open_with_patched),
                        variant = MorpheButtonVariant.FILLED,
                        size = MorpheButtonSize.LARGE,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onApply,
                    )
                }
            }
        }
    }
}
