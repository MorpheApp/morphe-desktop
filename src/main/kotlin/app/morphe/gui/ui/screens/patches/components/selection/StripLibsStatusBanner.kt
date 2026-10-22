/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.patches.components.selection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import app.morphe.gui.ui.components.MorpheBadge
import app.morphe.gui.ui.components.MorpheBadgeVariant
import app.morphe.gui.ui.screens.patches.StripLibsStatus
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun StripLibsStatusBanner(
    status: StripLibsStatus,
    modifier: Modifier = Modifier
) {
    val display: BannerDisplay = when (status) {
        is StripLibsStatus.NoNativeLibs -> BannerDisplay(
            headline = stringResource(Res.string.patch_selection_strip_no_libs_headline),
            detail = stringResource(Res.string.patch_selection_strip_no_libs_detail)
        )
        is StripLibsStatus.Universal -> BannerDisplay(
            headline = stringResource(Res.string.patch_selection_strip_universal_headline),
            detail = stringResource(Res.string.patch_selection_strip_universal_detail)
        )
        is StripLibsStatus.KeepAll -> BannerDisplay(
            headline = stringResource(Res.string.patch_selection_strip_keep_all_headline),
            detail = stringResource(Res.string.patch_selection_strip_keep_all_detail),
            notInApkChips = status.notInApk
        )
        is StripLibsStatus.Fallback -> BannerDisplay(
            headline = stringResource(Res.string.patch_selection_strip_fallback_headline),
            detail = stringResource(Res.string.patch_selection_strip_fallback_detail),
            keepChips = status.apkArches
        )
        is StripLibsStatus.WillStrip -> BannerDisplay(
            headline = stringResource(Res.string.patch_selection_strip_will_strip_headline),
            detail = stringResource(Res.string.patch_selection_strip_will_strip_detail),
            keepChips = status.keeping,
            stripChips = status.stripping,
            notInApkChips = status.notInApk
        )
    }
    val (headline, detail, keepChips, stripChips, notInApkChips) = display

    Box(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = MorpheTheme.dimens.controlHeight)
            .clip(MorpheTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MorpheTheme.shapes.small)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            itemVerticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = headline,
                style = MorpheTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = "- $detail",
                style = MorpheTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(Res.string.patch_selection_strip_settings_hint),
                style = MorpheTheme.typography.labelExtraSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Spacer(modifier = Modifier.weight(1f))
            val accents = MorpheTheme.colors
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
}

private enum class ArchChipRole { KEEP, STRIP, NOT_IN_APK }

@Composable
private fun ArchChip(
    label: String,
    accent: Color,
    role: ArchChipRole
) {
    val roleLabel = when (role) {
        ArchChipRole.KEEP -> stringResource(Res.string.patch_selection_arch_keep)
        ArchChipRole.STRIP -> stringResource(Res.string.patch_selection_arch_strip)
        ArchChipRole.NOT_IN_APK -> stringResource(Res.string.patch_selection_arch_not_in_apk)
    }
    val variant = if (role == ArchChipRole.KEEP) MorpheBadgeVariant.SUBTLE else MorpheBadgeVariant.OUTLINED
    val borderAlpha = if (role == ArchChipRole.KEEP) 0.4f else 0.3f
    val textAlpha = if (role == ArchChipRole.KEEP) 1f else 0.5f

    MorpheBadge(
        variant = variant,
        containerColor = if (role == ArchChipRole.KEEP) accent.copy(alpha = 0.08f) else Color.Transparent,
        contentColor = accent.copy(alpha = borderAlpha),
    ) {
        Text(
            text = roleLabel,
            style = MorpheTheme.typography.labelExtraSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Medium),
            color = accent.copy(alpha = textAlpha * 0.7f)
        )
        Text(
            text = label,
            style = MorpheTheme.typography.labelSmall,
            color = if (role == ArchChipRole.KEEP) accent.copy(alpha = textAlpha) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = textAlpha)
        )
    }
}

/** Display content for the native library stripping status banner. */
private data class BannerDisplay(
    val headline: String,
    val detail: String,
    val keepChips: List<String> = emptyList(),
    val stripChips: List<String> = emptyList(),
    val notInApkChips: List<String> = emptyList()
)
