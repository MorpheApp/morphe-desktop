/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.patches.components.selection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.ui.screens.patches.StripLibsStatus
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheDimens
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

// ── Architecture Selector ──

@Composable
internal fun StripLibsStatusBanner(
    status: StripLibsStatus,
    modifier: Modifier = Modifier
) {
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current

    // Each status variant maps to a BannerDisplay that tells the banner what color,
    // headline, description, and arch chips to render.
    // accents.secondary is the app's "informational" accent. MaterialTheme tertiary is
    // used for warning/fallback states.
    val display: BannerDisplay = when (status) {
        is StripLibsStatus.NoNativeLibs -> BannerDisplay(
            dotColor = accents.primary.copy(alpha = 0.4f),
            headline = stringResource(Res.string.patch_selection_strip_no_libs_headline),
            detail = stringResource(Res.string.patch_selection_strip_no_libs_detail)
        )
        is StripLibsStatus.Universal -> BannerDisplay(
            dotColor = accents.primary.copy(alpha = 0.4f),
            headline = stringResource(Res.string.patch_selection_strip_universal_headline),
            detail = stringResource(Res.string.patch_selection_strip_universal_detail)
        )
        is StripLibsStatus.KeepAll -> BannerDisplay(
            dotColor = accents.primary.copy(alpha = 0.4f),
            headline = stringResource(Res.string.patch_selection_strip_keep_all_headline),
            detail = stringResource(Res.string.patch_selection_strip_keep_all_detail),
            notInApkChips = status.notInApk
        )
        is StripLibsStatus.Fallback -> BannerDisplay(
            dotColor = MaterialTheme.colorScheme.tertiary,
            headline = stringResource(Res.string.patch_selection_strip_fallback_headline),
            detail = stringResource(Res.string.patch_selection_strip_fallback_detail),
            keepChips = status.apkArches
        )
        is StripLibsStatus.WillStrip -> BannerDisplay(
            dotColor = accents.primary,
            headline = stringResource(Res.string.patch_selection_strip_will_strip_headline),
            detail = stringResource(Res.string.patch_selection_strip_will_strip_detail),
            keepChips = status.keeping,
            stripChips = status.stripping,
            notInApkChips = status.notInApk
        )
    }
    val (dotColor, headline, detail, keepChips, stripChips, notInApkChips) = display

    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corners.small))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(corners.small))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = headline,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = font,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
        Text(
            text = "- $detail",
            fontSize = 11.sp,
            fontFamily = font,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = stringResource(Res.string.patch_selection_strip_settings_hint),
            fontSize = 9.sp,
            fontFamily = font,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Spacer(modifier = Modifier.weight(1f))
        keepChips.forEach { arch ->
            ArchChip(label = arch, accent = accents.secondary, role = ArchChipRole.KEEP)
        }
        stripChips.forEach { arch ->
            ArchChip(label = arch, accent = MaterialTheme.colorScheme.error, role = ArchChipRole.STRIP)
        }
        notInApkChips.forEach { arch ->
            ArchChip(label = arch, accent = accents.primary, role = ArchChipRole.NOT_IN_APK)
        }
    }
}

private enum class ArchChipRole { KEEP, STRIP, NOT_IN_APK }

@Composable
private fun ArchChip(
    label: String,
    accent: Color,
    role: ArchChipRole
) {
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current

    // Chip visual treatment per role:
    //  - KEEP       : filled accent background, strong border, full-opacity text
    //  - STRIP      : outlined only, dim border, dimmed text
    //  - NOT_IN_APK : outlined only, very dim border, dimmed italicized text
    //                 signals "this preference has no effect on this APK"
    val borderAlpha = when (role) {
        ArchChipRole.KEEP -> 0.4f
        ArchChipRole.STRIP -> 0.3f
        ArchChipRole.NOT_IN_APK -> 0.3f
    }
    val textAlpha = when (role) {
        ArchChipRole.KEEP -> 1f
        ArchChipRole.STRIP -> 0.45f
        ArchChipRole.NOT_IN_APK -> 0.5f
    }
    val roleLabel = when (role) {
        ArchChipRole.KEEP -> stringResource(Res.string.patch_selection_arch_keep)
        ArchChipRole.STRIP -> stringResource(Res.string.patch_selection_arch_strip)
        ArchChipRole.NOT_IN_APK -> stringResource(Res.string.patch_selection_arch_not_in_apk)
    }
    val labelColor = when (role) {
        ArchChipRole.KEEP -> accent.copy(alpha = textAlpha)
        ArchChipRole.STRIP -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = textAlpha)
        ArchChipRole.NOT_IN_APK -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = textAlpha)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(corners.small))
            .border(1.dp, accent.copy(alpha = borderAlpha), RoundedCornerShape(corners.small))
            .then(
                if (role == ArchChipRole.KEEP) {
                    Modifier.background(accent.copy(alpha = 0.08f), RoundedCornerShape(corners.small))
                } else Modifier
            )
            .defaultMinSize(minHeight = LocalMorpheDimens.current.chipHeight)
            .padding(horizontal = 9.dp, vertical = 3.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = roleLabel,
                fontSize = 9.sp,
                fontFamily = font,
                fontWeight = FontWeight.Medium,
                color = accent.copy(alpha = textAlpha * 0.7f)
            )
            Text(
                text = label,
                fontSize = 11.sp,
                fontFamily = font,
                fontWeight = FontWeight.Normal,
                color = labelColor
            )
        }
    }
}

/**
 * Per-status display data for the strip-libs banner. Lets the `when(status)` branch
 * stay terse (each variant just fills in what's relevant) and the rendering code
 * below stay uniform.
 */
private data class BannerDisplay(
    val dotColor: Color,
    val headline: String,
    val detail: String,
    val keepChips: List<String> = emptyList(),
    val stripChips: List<String> = emptyList(),
    val notInApkChips: List<String> = emptyList()
)
