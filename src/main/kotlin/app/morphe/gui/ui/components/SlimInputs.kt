/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.gui.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.ui.theme.LocalMorpheDimens
import app.morphe.gui.ui.theme.MorpheAccentColors
import app.morphe.gui.ui.theme.MorpheCornerStyle

/**
 * Label-and-input group rendered as a tight Column. Use inside a parent Column
 * that has its own `verticalArrangement = spacedBy(...)` for between-group
 * spacing — this composable's internal label↔field gap stays a fixed 4dp.
 */
@Composable
internal fun LabeledField(
    label: String,
    mono: FontFamily,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            fontFamily = mono,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            letterSpacing = 1.2.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
        content()
    }
}

/**
 * Slim text input matching the cyberdeck aesthetic across the app — pinned to
 * [LocalMorpheDimens.controlHeight] so it lines up with the project's standard
 * button height. Uses [BasicTextField] with a custom decoration so we get full
 * control of the height (Material 3's [androidx.compose.material3.OutlinedTextField]
 * has a 56dp minimum that's too chunky for this app).
 *
 * Optional [trailing] slot for things like password-visibility toggles.
 */
@Composable
internal fun SlimTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    mono: FontFamily,
    accents: MorpheAccentColors,
    corners: MorpheCornerStyle,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
) {
    val dimens = LocalMorpheDimens.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderColor by animateColorAsState(
        if (isFocused) accents.primary.copy(alpha = 0.5f)
        else MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
        animationSpec = tween(150),
        label = "slimFieldBorder",
    )

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        readOnly = readOnly,
        enabled = enabled,
        visualTransformation = visualTransformation,
        interactionSource = interactionSource,
        textStyle = MaterialTheme.typography.bodySmall.copy(
            fontFamily = mono,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
        ),
        cursorBrush = SolidColor(accents.primary),
        modifier = modifier
            .height(dimens.controlHeight)
            .clip(RoundedCornerShape(corners.small))
            .border(1.dp, borderColor, RoundedCornerShape(corners.small)),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 10.dp, end = if (trailing != null) 4.dp else 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(
                            text = placeholder,
                            fontSize = 11.sp,
                            fontFamily = mono,
                            color = muted.copy(alpha = 0.4f),
                        )
                    }
                    innerTextField()
                }
                if (trailing != null) trailing()
            }
        },
    )
}

/**
 * Compact OutlinedButton pinned to [LocalMorpheDimens.controlHeight]. Used for
 * BROWSE / RESET / similar inline action buttons next to a [SlimTextField].
 */
@Composable
internal fun DialogActionButton(
    label: String,
    mono: FontFamily,
    corners: MorpheCornerStyle,
    onClick: () -> Unit,
) {
    val dimens = LocalMorpheDimens.current
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(corners.small),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        modifier = Modifier.height(dimens.controlHeight),
    ) {
        Text(
            label,
            fontFamily = mono,
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp,
            letterSpacing = 0.5.sp,
        )
    }
}
