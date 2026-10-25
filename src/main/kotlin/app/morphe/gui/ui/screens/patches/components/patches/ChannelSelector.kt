/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.patches.components.patches

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.morphe.engine.ReleaseChannel
import app.morphe.gui.ui.components.MorpheChoiceChip
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ChannelSelector(
    selectedChannel: ReleaseChannel,
    onChannelSelected: (ReleaseChannel) -> Unit,
    stableCount: Int,
    devCount: Int,
    modifier: Modifier = Modifier
) {
    val accentColor = MorpheTheme.colors.primary

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ChannelChip(
            label = stringResource(Res.string.version_label_stable),
            count = stableCount,
            isSelected = selectedChannel == ReleaseChannel.STABLE,
            onClick = { onChannelSelected(ReleaseChannel.STABLE) },
            accentColor = accentColor,
            modifier = Modifier.weight(1f)
        )
        ChannelChip(
            label = stringResource(Res.string.version_label_prerelease),
            count = devCount,
            isSelected = selectedChannel == ReleaseChannel.DEV,
            onClick = { onChannelSelected(ReleaseChannel.DEV) },
            accentColor = accentColor,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ChannelChip(
    label: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    MorpheChoiceChip(
        text = label,
        active = isSelected,
        accent = accentColor,
        modifier = modifier,
        leadingContent = if (isSelected) {
            {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(accentColor, CircleShape)
                )
            }
        } else null,
        trailingContent = if (count > 0) {
            {
                Text(
                    text = "$count",
                    style = MorpheTheme.typography.labelExtraSmall.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = if (isSelected) accentColor
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        } else null,
        onClick = onClick,
    )
}
