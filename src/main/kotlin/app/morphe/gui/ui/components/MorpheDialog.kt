/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.morphe_desktop.generated.resources.Res
import app.morphe.morphe_desktop.generated.resources.close
import org.jetbrains.compose.resources.stringResource

val MorpheDialogMinWidth = 280.dp
val MorpheDialogMaxWidth = 560.dp
private val DialogWidth = 440.dp

private val DialogPadding = 18.dp
private val HeaderEndPadding = 12.dp
private val HeaderGap = 12.dp
private val TitleGap = 2.dp
private val BodyGap = 12.dp
private val ActionGap = 8.dp
private val FooterHorizontalPadding = 14.dp
private val FooterVerticalPadding = 10.dp
private val IconTileSize = 44.dp
private val HairlineWidth = 1.dp
private val SurfaceGap = 14.dp
private const val TILE_FILL_ALPHA = 0.1f
private const val TILE_BORDER_ALPHA = 0.45f
private const val FOOTER_SHADE_ALPHA = 0.4f
private const val HAIRLINE_ALPHA = 0.6f

@Composable
fun MorpheDialogSurface(
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    contentPadding: PaddingValues = MorpheTheme.dimens.dialogPadding,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(SurfaceGap),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        shape = MorpheTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(HairlineWidth, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier,
    ) {
        Column(
            modifier = contentModifier.padding(contentPadding),
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            content = content,
        )
    }
}

@Composable
fun MorpheDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    icon: ImageVector? = null,
    accent: Color? = null,
    minWidth: Dp = MorpheDialogMinWidth,
    maxWidth: Dp = DialogWidth,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = MorpheTheme.shapes.large
    val tint = accent ?: MorpheTheme.colors.primary
    val hasHeader = title != null || icon != null
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = modifier
                .widthIn(min = minWidth, max = maxWidth)
                .clip(shape)
                .background(scheme.surfaceContainer)
                .border(HairlineWidth, accent?.copy(alpha = MorpheTheme.alphas.borderDefault) ?: scheme.outlineVariant, shape),
        ) {
            if (hasHeader) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = DialogPadding, top = DialogPadding, end = HeaderEndPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(HeaderGap),
                ) {
                    if (icon != null) DialogIconTile(icon, tint)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(TitleGap),
                    ) {
                        if (title != null) {
                            Text(
                                text = title,
                                style = MorpheTheme.typography.titleMedium,
                                color = scheme.onSurface,
                            )
                        }
                        if (subtitle != null) {
                            Text(
                                text = subtitle,
                                style = MorpheTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant.copy(alpha = MorpheTheme.alphas.secondaryText),
                            )
                        }
                    }
                    MorpheIconButton(
                        icon = MorpheIcons.Close,
                        contentDescription = stringResource(Res.string.close),
                        size = MorpheIconButtonSize.COMPACT,
                        variant = MorpheIconButtonVariant.GHOST,
                        onClick = onDismiss,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = DialogPadding,
                        end = DialogPadding,
                        top = if (hasHeader) BodyGap else DialogPadding,
                        bottom = DialogPadding,
                    ),
                horizontalAlignment = horizontalAlignment,
                verticalArrangement = Arrangement.spacedBy(BodyGap),
                content = content,
            )
            if (actions != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(HairlineWidth)
                        .background(scheme.outlineVariant.copy(alpha = HAIRLINE_ALPHA))
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(scheme.surfaceContainerLowest.copy(alpha = FOOTER_SHADE_ALPHA))
                        .padding(horizontal = FooterHorizontalPadding, vertical = FooterVerticalPadding),
                    horizontalArrangement = Arrangement.spacedBy(ActionGap, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions,
                )
            }
        }
    }
}

@Composable
fun MorpheDialogText(text: String) {
    Text(
        text = text,
        style = MorpheTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DialogIconTile(icon: ImageVector, tint: Color) {
    val shape = MorpheTheme.shapes.medium
    Box(
        modifier = Modifier
            .size(IconTileSize)
            .clip(shape)
            .background(tint.copy(alpha = TILE_FILL_ALPHA))
            .border(HairlineWidth, tint.copy(alpha = TILE_BORDER_ALPHA), shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(MorpheTheme.dimens.iconLarge))
    }
}
