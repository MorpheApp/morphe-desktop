/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import app.morphe.gui.LocalBackgroundType
import app.morphe.gui.LocalEnableParallax
import app.morphe.gui.LocalGroupPatchesByCategory
import app.morphe.gui.LocalSharpCorners
import app.morphe.gui.data.repository.ConfigRepository
import app.morphe.gui.data.repository.LanguageRepository
import app.morphe.gui.ui.components.AppCard
import app.morphe.gui.ui.components.LanguageDialog
import app.morphe.gui.ui.components.LocalCardFills
import app.morphe.gui.ui.components.MorpheChoiceChip
import app.morphe.gui.ui.components.MorpheColorPickerCard
import app.morphe.gui.ui.components.color.CustomSwatches
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.ui.theme.THEME_PRESET_COLORS
import app.morphe.gui.ui.theme.ThemePreference
import app.morphe.gui.ui.theme.backgrounds.BackgroundType
import app.morphe.morphe_desktop.generated.resources.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
internal fun AppearanceTab(
    currentTheme: ThemePreference,
    onThemeChange: (ThemePreference) -> Unit,
    customAccentColorArgb: Int?,
    onCustomAccentColorChange: (Int?) -> Unit,
    borderColor: Color,
    currentLanguage: String = LanguageRepository.SYSTEM_CODE,
    onLanguageChange: (String) -> Unit = {},
) {
    var showCustomColorDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val configRepo: ConfigRepository = koinInject()
    val languageRepository: LanguageRepository = koinInject()
    val currentLanguageOption = remember(currentLanguage) {
        languageRepository.getLanguageByCode(currentLanguage, currentLanguage)
    }

    if (showLanguageDialog) {
        LanguageDialog(
            currentLanguageCode = currentLanguage,
            onLanguageSelected = { newCode ->
                onLanguageChange(newCode)
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false },
            languageRepository = languageRepository
        )
    }

    SectionLabel(stringResource(Res.string.settings_section_language), icon = MorpheIcons.Language)
    Spacer(Modifier.height(8.dp))

    val isSystem = currentLanguageOption.code == LanguageRepository.SYSTEM_CODE
    AboutRow(
        title = stringResource(Res.string.settings_language_current_title),
        subtitle = if (isSystem) stringResource(Res.string.settings_theme_system) else currentLanguageOption.displayName,
        onClick = { showLanguageDialog = true },
    ) {
        Text(
            text = currentLanguageOption.flag,
            style = MorpheTheme.typography.headlineMedium,
        )
    }

    Spacer(Modifier.height(8.dp))

    val linkText = "morphe.software/translate"
    val linkUrl = "https://morphe.software/translate"
    val rawTranslationHint = stringResource(Res.string.settings_language_community_translation_hint, linkText)
    val annotatedString = buildLinkedText(
        template = rawTranslationHint,
        linkText = linkText,
        url = linkUrl,
    )

    Text(
        text = annotatedString,
        style = MorpheTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
    )

    SettingsDivider(borderColor)

    SectionLabel(stringResource(Res.string.settings_section_theme), icon = MorpheIcons.Palette)
    Spacer(Modifier.height(8.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ThemePreference.entries.forEach { theme ->
            val isSelected = currentTheme == theme
            MorpheChoiceChip(
                text = stringResource(theme.displayNameRes),
                active = isSelected,
                icon = theme.icon(),
                onClick = { onThemeChange(theme) },
            )
        }
    }

    Spacer(Modifier.height(14.dp))

    val sharpCornersState = LocalSharpCorners.current
    SettingToggleRow(
        label = stringResource(Res.string.settings_toggle_sharp_corners_label),
        description = stringResource(Res.string.settings_toggle_sharp_corners_desc),
        checked = sharpCornersState.value,
        onCheckedChange = { enabled ->
            sharpCornersState.value = enabled
            scope.launch { configRepo.setUseSharpCorners(enabled) }
        },
        icon = MorpheIcons.RoundedCorner
    )

    SettingsDivider(borderColor)

    SectionLabel(stringResource(Res.string.settings_section_accent_color), icon = MorpheIcons.Palette)
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
                contentDescription = stringResource(Res.string.settings_accent_clear_description),
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
                    contentDescription = stringResource(Res.string.settings_accent_custom_description),
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
                        showAlpha = false,
                        showSaved = true,
                        onPick = { onCustomAccentColorChange(it) }
                    )
                }
            }
        }
    }

    SettingsDivider(borderColor)

    SectionLabel(stringResource(Res.string.settings_section_app_cards), icon = MorpheIcons.Gradient)
    Spacer(Modifier.height(8.dp))

    val cardFills = LocalCardFills.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AppCard(
            modifier = Modifier.width(72.dp).height(MorpheTheme.dimens.chipHeight),
            cornerRadius = MorpheTheme.corners.small,
            fill = cardFills.globalFill,
            interactive = false,
        ) {}
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(Res.string.settings_app_cards_color_all),
                style = MorpheTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(Res.string.settings_app_cards_color_all_desc),
                style = MorpheTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MorpheChoiceChip(
            text = stringResource(Res.string.customize),
            active = false,
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
                text = pluralStringResource(Res.plurals.settings_app_cards_override_count, cardFills.fills.size, cardFills.fills.size),
                style = MorpheTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            MorpheChoiceChip(
                text = stringResource(Res.string.settings_dialog_reset_button),
                active = false,
                onClick = { cardFills.onClearAll() },
            )
        }
    }

    SettingsDivider(borderColor)

    SectionLabel(stringResource(Res.string.settings_section_patch_list), icon = MorpheIcons.Extension)
    Spacer(Modifier.height(8.dp))

    val groupPatchesByCategoryState = LocalGroupPatchesByCategory.current
    SettingToggleRow(
        label = stringResource(Res.string.settings_toggle_patch_categories_label),
        description = stringResource(Res.string.settings_toggle_patch_categories_desc),
        checked = groupPatchesByCategoryState.value,
        onCheckedChange = { enabled ->
            groupPatchesByCategoryState.value = enabled
            scope.launch { configRepo.setGroupPatchesByCategory(enabled) }
        },
        icon = MorpheIcons.Category
    )

    SettingsDivider(borderColor)

    SectionLabel(stringResource(Res.string.settings_section_background_animation), icon = MorpheIcons.Wallpaper)
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
            MorpheChoiceChip(
                text = stringResource(bgType.displayNameRes),
                active = isSelected,
                icon = bgType.icon,
                onClick = { onBgChange(bgType) },
            )
        }
    }

    Spacer(Modifier.height(14.dp))

    SettingToggleRow(
        label = stringResource(Res.string.settings_toggle_parallax_label),
        description = stringResource(Res.string.settings_toggle_parallax_desc),
        checked = parallaxState.value,
        onCheckedChange = {
            parallaxState.value = it
            scope.launch { configRepo.setEnableParallax(it) }
        },
        icon = MorpheIcons.Mouse
    )

}

private val ThemePreference.displayNameRes: StringResource
    get() = when (this) {
        ThemePreference.LIGHT -> Res.string.settings_theme_light
        ThemePreference.DARK -> Res.string.settings_theme_dark
        ThemePreference.AMOLED -> Res.string.settings_theme_amoled
        ThemePreference.SYSTEM -> Res.string.settings_theme_system
    }

private val BackgroundType.displayNameRes: StringResource
    get() = when (this) {
        BackgroundType.CIRCLES -> Res.string.settings_background_circles
        BackgroundType.RINGS -> Res.string.settings_background_rings
        BackgroundType.MESH -> Res.string.settings_background_mesh
        BackgroundType.SPACE -> Res.string.settings_background_space
        BackgroundType.SHAPES -> Res.string.settings_background_shapes
        BackgroundType.SNOW -> Res.string.settings_background_snow
        BackgroundType.GRID -> Res.string.settings_background_grid
        BackgroundType.PARTICLES -> Res.string.settings_background_particles
        BackgroundType.LAVA -> Res.string.settings_background_lava
        BackgroundType.MATRIX -> Res.string.settings_background_matrix
        BackgroundType.NONE -> Res.string.none
    }

private fun ThemePreference.icon(): ImageVector {
    return when (this) {
        ThemePreference.LIGHT -> MorpheIcons.LightMode
        ThemePreference.DARK -> MorpheIcons.DarkMode
        ThemePreference.AMOLED -> MorpheIcons.Contrast
        ThemePreference.SYSTEM -> MorpheIcons.Settings
    }
}
