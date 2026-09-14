/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import app.morphe.gui.LocalBackgroundType
import app.morphe.gui.LocalEnableParallax
import app.morphe.gui.LocalSharpCorners
import app.morphe.gui.data.repository.ConfigRepository
import app.morphe.gui.ui.components.AppCard
import app.morphe.gui.ui.components.LocalCardFills
import app.morphe.gui.ui.components.MorpheChoiceChip
import app.morphe.gui.ui.components.MorpheColorPickerCard
import app.morphe.gui.ui.components.color.CustomSwatches
import app.morphe.gui.ui.components.handCursor
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.ui.theme.THEME_PRESET_COLORS
import app.morphe.gui.ui.theme.ThemePreference
import app.morphe.gui.ui.theme.backgrounds.BackgroundType
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
internal fun AppearanceTab(
    currentTheme: ThemePreference,
    onThemeChange: (ThemePreference) -> Unit,
    customAccentColorArgb: Int?,
    onCustomAccentColorChange: (Int?) -> Unit,
    borderColor: Color,
) {
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    var showCustomColorDialog by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val configRepo: ConfigRepository = koinInject()

    SectionLabel("Theme", font, icon = MorpheIcons.Palette)
    Spacer(Modifier.height(8.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ThemePreference.entries.forEach { theme ->
            val isSelected = currentTheme == theme
            val hoverInteraction = remember { MutableInteractionSource() }
            val isHovered by hoverInteraction.collectIsHoveredAsState()
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(corners.small))
                    .border(
                        1.dp,
                        when {
                            isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            isHovered -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            else -> borderColor
                        },
                        RoundedCornerShape(corners.small)
                    )
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        else Color.Transparent
                    )
                    .hoverable(hoverInteraction)
                    .handCursor()
                    .clickable { onThemeChange(theme) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = theme.icon(),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = theme.toDisplayName(),
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    fontFamily = font,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    SettingsDivider(borderColor)

    SectionLabel("Accent color", font, icon = MorpheIcons.Palette)
    Spacer(Modifier.height(8.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        AccentSwatch(
            selected = customAccentColorArgb == null,
            fill = Color.Transparent,
            onClick = { onCustomAccentColorChange(null) },
        ) {
            Icon(
                imageVector = MorpheIcons.Close,
                contentDescription = "Clear accent color",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        THEME_PRESET_COLORS.forEach { preset ->
            AccentSwatch(
                selected = customAccentColorArgb == preset.toArgb(),
                fill = preset,
                onClick = { onCustomAccentColorChange(preset.toArgb()) },
            )
        }

        CustomSwatches.colors.forEach { saved ->
            AccentSwatch(
                selected = customAccentColorArgb == saved,
                fill = Color(saved),
                onClick = { onCustomAccentColorChange(saved) },
                onDelete = { CustomSwatches.remove(saved) },
            )
        }

        val isCustomNonPreset = customAccentColorArgb != null &&
            THEME_PRESET_COLORS.none { it.toArgb() == customAccentColorArgb } &&
            CustomSwatches.colors.none { it == customAccentColorArgb }
        Box {
            val yOff = with(LocalDensity.current) { 46.dp.roundToPx() }
            AccentSwatch(
                selected = isCustomNonPreset,
                fill = if (isCustomNonPreset) Color(customAccentColorArgb)
                    else MaterialTheme.colorScheme.surfaceVariant,
                onClick = { showCustomColorDialog = true },
            ) {
                Icon(
                    imageVector = MorpheIcons.Edit,
                    contentDescription = "Custom Accent Color",
                    tint = if (isCustomNonPreset) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (showCustomColorDialog) {
                Popup(
                    alignment = Alignment.TopStart,
                    offset = IntOffset(0, yOff),
                    onDismissRequest = { showCustomColorDialog = false },
                    properties = PopupProperties(focusable = true)
                ) {
                    MorpheColorPickerCard(
                        argb = customAccentColorArgb ?: 0xFFF44336.toInt(),
                        accents = accents,
                        font = font,
                        showAlpha = false,
                        onPick = { onCustomAccentColorChange(it) }
                    )
                }
            }
        }
    }

    SettingsDivider(borderColor)

    SectionLabel("App cards", font, icon = MorpheIcons.Gradient)
    Spacer(Modifier.height(8.dp))

    val cardFills = LocalCardFills.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppCard(
            modifier = Modifier.width(72.dp).height(34.dp),
            cornerRadius = corners.small,
            fill = cardFills.globalFill,
            interactive = false,
        ) {}
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Colour for every card",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = font,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Cards you have customised individually keep their own colour",
                fontSize = 11.sp,
                fontFamily = font,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MorpheChoiceChip(
            text = "Customise",
            active = false,
            font = font,
            onClick = { cardFills.requestEditGlobal() },
        )
    }

    if (cardFills.fills.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "${cardFills.fills.size} card" +
                    (if (cardFills.fills.size == 1) "" else "s") +
                    " override this",
                fontSize = 11.sp,
                fontFamily = font,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            MorpheChoiceChip(
                text = "Reset",
                active = false,
                font = font,
                onClick = { cardFills.onClearAll() },
            )
        }
    }

    SettingsDivider(borderColor)

    SectionLabel("Background animation", font, icon = MorpheIcons.Wallpaper)
    Spacer(Modifier.height(8.dp))

    val bgState = LocalBackgroundType.current
    val parallaxState = LocalEnableParallax.current

    val onBgChange: (BackgroundType) -> Unit = { newBg ->
        bgState.value = newBg
        scope.launch { configRepo.setBackgroundType(newBg.name) }
    }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        BackgroundType.entries.forEach { bgType ->
            val isSelected = bgState.value == bgType
            val hoverInteraction = remember { MutableInteractionSource() }
            val isHovered by hoverInteraction.collectIsHoveredAsState()
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(corners.small))
                    .border(
                        1.dp,
                        when {
                            isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            isHovered -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            else -> borderColor
                        },
                        RoundedCornerShape(corners.small)
                    )
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        else Color.Transparent
                    )
                    .hoverable(hoverInteraction)
                    .handCursor()
                    .clickable { onBgChange(bgType) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = bgType.icon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = bgType.displayName,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    fontFamily = font,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    Spacer(Modifier.height(14.dp))

    SettingToggleRow(
        label = "Parallax effect",
        description = "Smooth background shifting when moving the mouse",
        checked = parallaxState.value,
        onCheckedChange = {
            parallaxState.value = it
            scope.launch { configRepo.setEnableParallax(it) }
        },
        accentColor = accents.primary,
        font = font,
        icon = MorpheIcons.Mouse
    )

    Spacer(Modifier.height(14.dp))

    val sharpCornersState = LocalSharpCorners.current
    SettingToggleRow(
        label = "Sharp corners",
        description = "Square off cards, dialogs and buttons",
        checked = sharpCornersState.value,
        onCheckedChange = { enabled ->
            sharpCornersState.value = enabled
            scope.launch { configRepo.setUseSharpCorners(enabled) }
        },
        accentColor = accents.primary,
        font = font,
        icon = MorpheIcons.RoundedCorner
    )
}

private fun ThemePreference.toDisplayName(): String {
    return when (this) {
        ThemePreference.LIGHT -> "Light"
        ThemePreference.DARK -> "Dark"
        ThemePreference.AMOLED -> "Amoled"
        ThemePreference.SYSTEM -> "System"
    }
}

private fun ThemePreference.icon(): ImageVector {
    return when (this) {
        ThemePreference.LIGHT -> MorpheIcons.LightMode
        ThemePreference.DARK -> MorpheIcons.DarkMode
        ThemePreference.AMOLED -> MorpheIcons.Contrast
        ThemePreference.SYSTEM -> MorpheIcons.Settings
    }
}
