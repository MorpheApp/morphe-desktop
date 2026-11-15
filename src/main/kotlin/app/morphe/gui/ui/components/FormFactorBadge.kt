/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.LocalThemeState
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.ui.theme.ThemeStyle
import app.morphe.morphe_desktop.generated.resources.Res
import app.morphe.morphe_desktop.generated.resources.form_factor_automotive
import app.morphe.morphe_desktop.generated.resources.form_factor_tv
import app.morphe.morphe_desktop.generated.resources.form_factor_wearable
import app.morphe.patcher.patch.FormFactor
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private data class FormFactorMark(
    val icon: ImageVector,
    val labelRes: StringResource,
)

private fun FormFactor?.toMark(): FormFactorMark? = when (this) {
    FormFactor.TV -> FormFactorMark(MorpheIcons.Tv, Res.string.form_factor_tv)
    FormFactor.WEARABLE -> FormFactorMark(MorpheIcons.Watch, Res.string.form_factor_wearable)
    FormFactor.AUTOMOTIVE -> FormFactorMark(MorpheIcons.DirectionsCar, Res.string.form_factor_automotive)
    FormFactor.MOBILE, null -> null
}

@Composable
fun FormFactorBadge(
    formFactor: FormFactor?,
    modifier: Modifier = Modifier,
    onGradient: Boolean = false,
) {
    val mark = formFactor.toMark() ?: return
    val label = stringResource(mark.labelRes)
    val useWhiteOnGradient = onGradient && LocalThemeState.current.style != ThemeStyle.MONOCHROME

    MorpheTooltip(
        text = label,
        modifier = modifier.semantics { contentDescription = label },
    ) {
        MorpheBadge(
            tone = MorpheBadgeTone.Neutral,
            containerColor = if (useWhiteOnGradient) Color.White.copy(alpha = 0.20f) else null,
            contentColor = if (useWhiteOnGradient) Color.White else null,
            onGradient = false,
        ) {
            Icon(
                imageVector = mark.icon,
                contentDescription = null,
                tint = LocalContentColor.current,
                modifier = Modifier.size(MorpheTheme.dimens.iconMedium),
            )
        }
    }
}
