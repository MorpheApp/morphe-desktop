/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.MorpheMotion
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** Group container that pairs an input component with a top label. */
@Composable
internal fun LabeledField(
    label: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MorpheTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

/** Compact single-line text input with custom decoration and optional trailing slot. */
@Composable
internal fun SlimTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
) {
    val dimens = MorpheTheme.dimens
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderColor by animateColorAsState(
        when {
            isError -> MorpheTheme.colors.danger
            isFocused -> MorpheTheme.colors.primary.copy(alpha = MorpheTheme.alphas.borderFocus)
            else -> MaterialTheme.colorScheme.outline.copy(alpha = MorpheTheme.alphas.borderMuted)
        },
        animationSpec = MorpheMotion.fastTween(),
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
        textStyle = MorpheTheme.typography.bodySmall.copy(
            color = MaterialTheme.colorScheme.onSurface,
        ),
        cursorBrush = SolidColor(if (isError) MorpheTheme.colors.danger else MorpheTheme.colors.primary),
        modifier = modifier
            .height(dimens.controlHeight)
            .clip(MorpheTheme.shapes.small)
            .border(1.dp, borderColor, MorpheTheme.shapes.small),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 10.dp, end = if (trailing != null) 4.dp else 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(
                            text = placeholder,
                            style = MorpheTheme.typography.bodySmall,
                            color = muted.copy(alpha = 0.5f),
                        )
                    }
                    innerTextField()
                }
                if (trailing != null) trailing()
            }
        },
    )
}

/** Compact search field with leading search icon, placeholder, and clear button. */
@Composable
internal fun SlimSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = stringResource(Res.string.search),
    modifier: Modifier = Modifier,
    maxWidth: Dp? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val dimens = MorpheTheme.dimens
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderColor by animateColorAsState(
        if (isFocused) MorpheTheme.colors.primary.copy(alpha = MorpheTheme.alphas.borderFocus)
        else MaterialTheme.colorScheme.outline.copy(alpha = MorpheTheme.alphas.borderMuted),
        animationSpec = MorpheMotion.fastTween(),
        label = "slimSearchBorder",
    )

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        interactionSource = interactionSource,
        textStyle = MorpheTheme.typography.bodySmall.copy(
            color = MaterialTheme.colorScheme.onSurface,
        ),
        cursorBrush = SolidColor(MorpheTheme.colors.primary),
        modifier = modifier
            .then(if (maxWidth != null) Modifier.widthIn(max = maxWidth) else Modifier)
            .fillMaxWidth()
            .height(dimens.controlHeight)
            .clip(MorpheTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, borderColor, MorpheTheme.shapes.small),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = MorpheIcons.Search,
                    contentDescription = null,
                    tint = muted.copy(alpha = 0.55f),
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(
                            text = placeholder,
                            style = MorpheTheme.typography.bodySmall,
                            color = muted.copy(alpha = 0.4f),
                        )
                    }
                    innerTextField()
                }
                if (value.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    MorpheIconButton(
                        icon = MorpheIcons.Clear,
                        contentDescription = stringResource(Res.string.clear),
                        size = MorpheIconButtonSize.TINY,
                        variant = MorpheIconButtonVariant.GHOST,
                        onClick = { onValueChange("") },
                    )
                }
                if (trailing != null) {
                    trailing()
                }
            }
        },
    )
}

/** Compact password field with integrated visibility toggle button. */
@Composable
internal fun SlimPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    enabled: Boolean = true,
) {
    var passwordVisible by remember { mutableStateOf(false) }
    SlimTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        enabled = enabled,
        visualTransformation = if (passwordVisible) VisualTransformation.None
                               else PasswordVisualTransformation(),
        modifier = modifier,
        trailing = {
            MorpheIconButton(
                icon = if (passwordVisible) MorpheIcons.VisibilityOff else MorpheIcons.Visibility,
                contentDescription = stringResource(
                    if (passwordVisible) Res.string.settings_signing_password_hide
                    else Res.string.settings_signing_password_show
                ),
                size = MorpheIconButtonSize.COMPACT,
                variant = MorpheIconButtonVariant.GHOST,
                enabled = enabled,
                onClick = { passwordVisible = !passwordVisible },
            )
        },
    )
}

