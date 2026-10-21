/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.patches.components.patches

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.morphe.engine.model.Release
import app.morphe.gui.ui.components.FormattedReleaseNotes
import app.morphe.gui.ui.components.MorpheBadge
import app.morphe.gui.ui.components.MorpheBadgeTone
import app.morphe.gui.ui.components.MorpheChoiceChip
import app.morphe.gui.ui.components.handCursor
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.MorpheMotion
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.util.FormatUtils
import app.morphe.gui.util.currentLocale
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ReleaseCard(
    release: Release,
    isSelected: Boolean,
    isDownloaded: Boolean,
    isOffline: Boolean = false,
    isLatest: Boolean = false,
    onClick: () -> Unit
) {
    val selectedColor = MorpheTheme.colors.primary
    val accentColor = if (isSelected) selectedColor else MaterialTheme.colorScheme.onSurfaceVariant

    var isExpanded by remember { mutableStateOf(false) }
    val hasNotes = !release.body.isNullOrBlank()

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val borderColor by animateColorAsState(
        when {
            isSelected -> selectedColor
            isDownloaded -> selectedColor.copy(alpha = if (isHovered) 0.7f else 0.45f)
            isHovered -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        },
        animationSpec = MorpheMotion.fastTween()
    )

    val borderWidth by animateDpAsState(
        targetValue = if (isSelected) 2.dp else 1.dp,
        animationSpec = MorpheMotion.fastTween()
    )

    val baseBg = if (isSelected) MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
    else MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp).copy(alpha = 0.5f)

    val tintColor = if (isSelected) selectedColor.copy(alpha = 0.07f) else Color.Transparent

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MorpheTheme.shapes.medium)
            .border(borderWidth, borderColor, MorpheTheme.shapes.medium)
            .background(baseBg)
            .background(tintColor)
            .hoverable(interactionSource)
            .handCursor()
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = release.tagName,
                            style = MorpheTheme.typography.titleSmall,
                            color = if (isSelected) selectedColor
                            else MaterialTheme.colorScheme.onSurface
                        )
                        if (isLatest) {
                            MorpheBadge(text = stringResource(Res.string.patches_latest_badge), tone = MorpheBadgeTone.Primary)
                        }
                        if (release.isDevRelease()) {
                            MorpheBadge(text = stringResource(Res.string.version_label_experimental), tone = MorpheBadgeTone.Warning)
                        }
                        if (isDownloaded) {
                            MorpheBadge(text = stringResource(Res.string.patches_cached_badge))
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    release.assets.find { it.isPatchFile() }?.let { patchAsset ->
                        Text(
                            text = "${patchAsset.name} (${FormatUtils.formatFileSize(patchAsset.size, currentLocale())})",
                            style = MorpheTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    val formattedDate = release.publishedAt?.let { formatDate(it) } ?: ""
                    if (formattedDate.isNotEmpty()) {
                        Text(
                            text = if (isOffline) stringResource(Res.string.patches_date_cached, formattedDate)
                                   else stringResource(Res.string.patches_date_published, formattedDate),
                            style = MorpheTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (hasNotes) {
                        Spacer(modifier = Modifier.height(6.dp))
                        MorpheChoiceChip(
                            text = if (isExpanded) stringResource(Res.string.patches_hide_notes)
                                   else stringResource(Res.string.patches_patch_notes),
                            active = isExpanded,
                            accent = accentColor,
                            icon = if (isExpanded) MorpheIcons.ArrowDropUp else MorpheIcons.ArrowDropDown,
                            dense = true,
                            onClick = { isExpanded = !isExpanded },
                        )
                    }
                }
            }

            if (isExpanded && hasNotes) {
                val notesDividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.06f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(1.dp)
                        .background(notesDividerColor)
                )
                FormattedReleaseNotes(
                    markdown = release.body,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun formatDate(isoDate: String): String =
    FormatUtils.formatIsoDateTime(isoDate, currentLocale())
