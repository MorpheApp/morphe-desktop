/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.patches.components.patches

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.engine.model.Release
import app.morphe.gui.ui.components.FormattedReleaseNotes
import app.morphe.gui.ui.components.MorpheBadge
import app.morphe.gui.ui.components.MorpheBadgeTone
import app.morphe.gui.ui.components.handCursor
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.util.FormatUtils
import app.morphe.gui.util.currentLocale
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

// ════════════════════════════════════════════════════════════════════
//  RELEASE CARD
// ════════════════════════════════════════════════════════════════════

@Composable
internal fun ReleaseCard(
    release: Release,
    isSelected: Boolean,
    isDownloaded: Boolean,
    isOffline: Boolean = false,
    isLatest: Boolean = false,
    onClick: () -> Unit
) {
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val selectedColor = accents.primary
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
        animationSpec = tween(150)
    )

    val borderWidth by animateDpAsState(
        targetValue = if (isSelected) 2.dp else 1.dp,
        animationSpec = tween(150)
    )

    val baseBg = if (isSelected) MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
    else MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp).copy(alpha = 0.5f)

    val tintColor = if (isSelected) selectedColor.copy(alpha = 0.07f) else Color.Transparent

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corners.medium))
            .border(borderWidth, borderColor, RoundedCornerShape(corners.medium))
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
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = font,
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

                    // Patch file info
                    release.assets.find { it.isPatchFile() }?.let { patchAsset ->
                        Text(
                            text = "${patchAsset.name} (${FormatUtils.formatFileSize(patchAsset.size, currentLocale())})",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = font,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    val formattedDate = release.publishedAt?.let { formatDate(it) } ?: ""
                    if (formattedDate.isNotEmpty()) {
                        Text(
                            text = if (isOffline) stringResource(Res.string.patches_date_cached, formattedDate)
                                   else stringResource(Res.string.patches_date_published, formattedDate),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = font,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (hasNotes) {
                        Spacer(modifier = Modifier.height(6.dp))
                        val noteHover = remember { MutableInteractionSource() }
                        val isNoteHovered by noteHover.collectIsHoveredAsState()
                        val noteBorder by animateColorAsState(
                            if (isNoteHovered) accentColor.copy(alpha = 0.3f)
                            else accentColor.copy(alpha = 0.15f),
                            animationSpec = tween(150)
                        )

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(corners.small))
                                .border(1.dp, noteBorder, RoundedCornerShape(corners.small))
                                .hoverable(noteHover)
                                .handCursor()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { isExpanded = !isExpanded }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (isExpanded) stringResource(Res.string.patches_hide_notes)
                                       else stringResource(Res.string.patches_patch_notes),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal,
                                fontFamily = font,
                                color = accentColor,
                            )
                            Icon(
                                imageVector = if (isExpanded) MorpheIcons.ArrowDropUp else MorpheIcons.ArrowDropDown,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // Expandable release notes
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
