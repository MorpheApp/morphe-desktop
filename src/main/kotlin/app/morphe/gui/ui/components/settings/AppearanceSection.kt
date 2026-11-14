/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import app.morphe.gui.data.repository.LanguageRepository
import app.morphe.gui.ui.components.AppCard
import app.morphe.gui.ui.components.LocalCardFills
import app.morphe.gui.ui.components.MorpheButton
import app.morphe.gui.ui.components.MorpheButtonSize
import app.morphe.gui.ui.components.MorpheButtonVariant
import app.morphe.gui.ui.components.MorpheColorPickerCard
import app.morphe.gui.ui.components.MorpheDropdownItem
import app.morphe.gui.ui.components.SettingsStateHolder
import app.morphe.gui.ui.components.color.CustomSwatches
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.ui.theme.THEME_PRESET_COLORS
import app.morphe.gui.ui.theme.ThemePreference
import app.morphe.gui.ui.theme.ThemeStyle
import app.morphe.gui.ui.theme.backgrounds.BackgroundType
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Immutable
internal data class AppearanceSearchVisibility(
    val language: Boolean,
    val themeMode: Boolean,
    val colorStyle: Boolean,
    val sharpCorners: Boolean,
    val accentColor: Boolean,
    val appCards: Boolean,
    val appIcons: Boolean,
    val moreAppsCard: Boolean,
    val backgroundAnimation: Boolean,
    val parallax: Boolean,
) {
    val hasLanguageSection: Boolean get() = language
    val hasThemeSection: Boolean get() = themeMode || colorStyle
    val hasAccentSection: Boolean get() = accentColor
    val hasAppCardsSection: Boolean get() = appCards || appIcons || moreAppsCard
    val hasEffectsSection: Boolean get() = sharpCorners || backgroundAnimation || parallax
    val any: Boolean
        get() = hasLanguageSection || hasThemeSection || hasAccentSection || hasAppCardsSection || hasEffectsSection
}

@Composable
internal fun rememberAppearanceSearchVisibility(
    query: String,
    isMonochrome: Boolean = false,
): AppearanceSearchVisibility {
    if (query.isBlank()) {
        return AppearanceSearchVisibility(
            language = true,
            themeMode = true,
            colorStyle = true,
            sharpCorners = true,
            accentColor = true,
            appCards = true,
            backgroundAnimation = true,
            parallax = true,
            appIcons = true,
            moreAppsCard = true,
        )
    }

    val langSection = stringResource(Res.string.settings_section_language)
    val langTitle = stringResource(Res.string.settings_language_current_title)
    val themeSection = stringResource(Res.string.settings_section_theme)
    val themeModeLabel = stringResource(Res.string.settings_appearance_theme_mode)
    val themeModeDesc = stringResource(Res.string.settings_appearance_theme_mode_description)
    val colorStyleLabel = stringResource(Res.string.settings_appearance_color_style)
    val colorStyleDesc = stringResource(Res.string.settings_appearance_color_style_description)
    val sharpLabel = stringResource(Res.string.settings_toggle_sharp_corners_label)
    val sharpDesc = stringResource(Res.string.settings_toggle_sharp_corners_desc)
    val accentSection = stringResource(Res.string.settings_section_accent_color)
    val cardsSection = stringResource(Res.string.settings_section_app_cards)
    val cardsSub = stringResource(Res.string.settings_app_cards_color_all)
    val cardsDesc = stringResource(Res.string.settings_app_cards_color_all_desc)
    val appIconsLabel = stringResource(Res.string.settings_toggle_app_icons_label)
    val appIconsDesc = stringResource(Res.string.settings_toggle_app_icons_desc)
    val moreAppsLabel = stringResource(Res.string.settings_appearance_more_apps_card)
    val moreAppsDesc = stringResource(Res.string.settings_appearance_more_apps_card_description)
    val effectsSection = stringResource(Res.string.settings_section_effects)
    val bgLabel = stringResource(Res.string.settings_appearance_background_animation)
    val bgDesc = stringResource(Res.string.settings_appearance_background_animation_description)
    val parallaxLabel = stringResource(Res.string.settings_toggle_parallax_label)
    val parallaxDesc = stringResource(Res.string.settings_toggle_parallax_desc)

    return AppearanceSearchVisibility(
        language = matchesSettingQuery(query, langSection, langTitle, "language", "locale", "translation"),
        themeMode = matchesSettingQuery(query, themeSection, themeModeLabel, themeModeDesc, "light", "dark", "amoled", "system", "theme"),
        colorStyle = matchesSettingQuery(query, themeSection, colorStyleLabel, colorStyleDesc, "morphe", "monochrome", "style"),
        sharpCorners = matchesSettingQuery(query, effectsSection, sharpLabel, sharpDesc, "effects", "sharp", "corners", "square", "radius"),
        accentColor = matchesSettingQuery(query, accentSection, "accent", "color", "swatch", "custom"),
        appCards = !isMonochrome && matchesSettingQuery(query, cardsSection, cardsSub, cardsDesc, "card", "fill", "gradient"),
        appIcons = matchesSettingQuery(query, cardsSection, appIconsLabel, appIconsDesc, "icons", "app icons"),
        moreAppsCard = matchesSettingQuery(query, cardsSection, moreAppsLabel, moreAppsDesc, "more apps", "community"),
        backgroundAnimation = matchesSettingQuery(query, effectsSection, bgLabel, bgDesc, "effects", "background", "animation", "circles", "rings", "mesh", "matrix", "lava"),
        parallax = matchesSettingQuery(query, effectsSection, bgLabel, parallaxLabel, parallaxDesc, "effects", "parallax", "mouse", "motion"),
    )
}

@Composable
internal fun AppearanceSection(
    settings: SettingsStateHolder,
    onOpenLanguage: () -> Unit = {},
    searchQuery: String = "",
    visibility: AppearanceSearchVisibility = rememberAppearanceSearchVisibility(
        query = searchQuery,
        isMonochrome = settings.themeState.style == ThemeStyle.MONOCHROME,
    ),
    languageRepository: LanguageRepository = koinInject(),
) {
    if (!visibility.any) return

    var showAccentPicker by remember { mutableStateOf(false) }
    val cardFills = LocalCardFills.current
    val currentLanguageOption = remember(settings.language) {
        languageRepository.getLanguageByCode(settings.language, settings.language)
    }

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (visibility.hasLanguageSection) {
            SettingsSection(
                title = stringResource(Res.string.settings_section_language),
                icon = MorpheIcons.Language,
            ) {
                val isSystem = currentLanguageOption.code == LanguageRepository.SYSTEM_CODE
                val languageDisplay = if (isSystem) {
                    stringResource(Res.string.settings_theme_system)
                } else {
                    currentLanguageOption.displayName
                }

                SettingsGroup {
                    actionItem(
                        key = "language",
                        visible = visibility.language,
                        title = stringResource(Res.string.settings_language_current_title),
                        description = languageDisplay,
                        leadingContent = {
                            Box(
                                modifier = Modifier.defaultMinSize(minWidth = 20.dp, minHeight = 20.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = currentLanguageOption.flag,
                                    fontSize = 18.sp,
                                    lineHeight = 18.sp,
                                )
                            }
                        },
                        onClick = onOpenLanguage,
                    )
                }
            }
        }

        if (visibility.hasThemeSection) {
            SettingsSection(
                title = stringResource(Res.string.settings_section_theme),
                icon = MorpheIcons.Palette,
            ) {
                SettingsGroup {
                    dropdownItem(
                        key = "theme_mode",
                        visible = visibility.themeMode,
                        icon = MorpheIcons.DarkMode,
                        title = stringResource(Res.string.settings_appearance_theme_mode),
                        description = stringResource(Res.string.settings_appearance_theme_mode_description),
                        selectedLabel = settings.themeState.current.label(),
                        selectedIcon = settings.themeState.current.icon(),
                        items = ThemePreference.entries.map { pref ->
                            MorpheDropdownItem(
                                label = pref.label(),
                                icon = pref.icon(),
                                selected = settings.themeState.current == pref,
                                onClick = { settings.setThemePreference(pref) },
                            )
                        },
                    )

                    val selectedStyle = settings.themeState.style
                    dropdownItem(
                        key = "color_style",
                        visible = visibility.colorStyle,
                        icon = MorpheIcons.ColorLens,
                        title = stringResource(Res.string.settings_appearance_color_style),
                        description = stringResource(Res.string.settings_appearance_color_style_description),
                        selectedLabel = selectedStyle.label(),
                        selectedIcon = selectedStyle.icon(),
                        items = ThemeStyle.entries.map { style ->
                            MorpheDropdownItem(
                                label = style.label(),
                                icon = style.icon(),
                                selected = selectedStyle == style,
                                onClick = { settings.setThemeStyle(style) },
                            )
                        },
                    )
                }
            }
        }

        if (visibility.hasAccentSection) {
            SettingsSection(
                title = stringResource(Res.string.settings_section_accent_color),
                icon = MorpheIcons.Palette,
            ) {
                SettingsGroup {
                    blockItem(key = "accent_color", visible = visibility.accentColor) {
                        val customArgb = settings.customAccentColorArgb
                        val isCustomNonPreset = customArgb != null &&
                            THEME_PRESET_COLORS.none { it.toArgb() == customArgb } &&
                            CustomSwatches.colors.none { it == customArgb }
                        val yOff = with(LocalDensity.current) { 46.dp.roundToPx() }
                        val customIconTint = if (isCustomNonPreset) {
                            if (Color(customArgb).luminance() > 0.6f) Color.Black else Color.White
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            AccentSwatch(
                                selected = customArgb == null,
                                fill = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                selectedBorderColor = MaterialTheme.colorScheme.primary,
                                onClick = { settings.setCustomAccentColor(null) },
                            ) {
                                Icon(
                                    imageVector = MorpheIcons.Close,
                                    contentDescription = stringResource(Res.string.settings_accent_clear_description),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                            }

                            THEME_PRESET_COLORS.forEach { preset ->
                                val argb = preset.toArgb()
                                AccentSwatch(
                                    selected = customArgb == argb,
                                    fill = preset,
                                    semanticsLabel = "#%06X".format(argb and 0xFFFFFF),
                                    onClick = { settings.setCustomAccentColor(argb) },
                                )
                            }

                            CustomSwatches.colors.forEach { savedArgb ->
                                AccentSwatch(
                                    selected = customArgb == savedArgb,
                                    fill = Color(savedArgb),
                                    semanticsLabel = "#%06X".format(savedArgb and 0xFFFFFF),
                                    onClick = { settings.setCustomAccentColor(savedArgb) },
                                    onDelete = {
                                        if (customArgb == savedArgb) {
                                            settings.setCustomAccentColor(null)
                                        }
                                        CustomSwatches.remove(savedArgb)
                                    },
                                )
                            }

                            Box {
                                AccentSwatch(
                                    selected = isCustomNonPreset,
                                    fill = if (isCustomNonPreset) Color(customArgb) else MaterialTheme.colorScheme.surfaceVariant,
                                    onClick = { showAccentPicker = true },
                                ) {
                                    Icon(
                                        imageVector = MorpheIcons.Edit,
                                        contentDescription = stringResource(Res.string.settings_accent_custom_description),
                                        tint = customIconTint,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }

                                if (showAccentPicker) {
                                    val initialArgb = customArgb ?: MorpheTheme.colors.primary.toArgb()
                                    Popup(
                                        alignment = Alignment.TopStart,
                                        offset = IntOffset(0, yOff),
                                        onDismissRequest = { showAccentPicker = false },
                                        properties = PopupProperties(focusable = true),
                                    ) {
                                        MorpheColorPickerCard(
                                            argb = initialArgb,
                                            showAlpha = false,
                                            showSaved = true,
                                            onPick = { picked -> settings.setCustomAccentColor(picked) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        val showCardFillRow = visibility.appCards && settings.themeState.style != ThemeStyle.MONOCHROME
        if (showCardFillRow || visibility.appIcons || visibility.moreAppsCard) {
            SettingsSection(
                title = stringResource(Res.string.settings_section_app_cards),
                icon = MorpheIcons.Gradient,
            ) {
                SettingsGroup {
                    blockItem(key = "app_cards", visible = showCardFillRow) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                AppCard(
                                    modifier = Modifier.width(72.dp).height(MorpheTheme.dimens.chipHeight),
                                    cornerRadius = MorpheTheme.shapes.smallDp,
                                    fill = cardFills.globalFill,
                                    interactive = false,
                                ) {}
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    Text(
                                        text = stringResource(Res.string.settings_app_cards_color_all),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = stringResource(Res.string.settings_app_cards_color_all_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    MorpheButton(
                                        label = stringResource(Res.string.customize),
                                        variant = MorpheButtonVariant.OUTLINED,
                                        size = MorpheButtonSize.COMPACT,
                                        onClick = { cardFills.requestEditGlobal() },
                                    )
                                    if (cardFills.globalFill != null || cardFills.fills.isNotEmpty()) {
                                        MorpheButton(
                                            label = stringResource(Res.string.settings_dialog_reset_button),
                                            variant = MorpheButtonVariant.GHOST,
                                            size = MorpheButtonSize.COMPACT,
                                            onClick = { cardFills.onClearAll() },
                                        )
                                    }
                                }
                            }

                            if (cardFills.fills.isNotEmpty()) {
                                Text(
                                    text = pluralStringResource(
                                        Res.plurals.settings_app_cards_override_count,
                                        cardFills.fills.size,
                                        cardFills.fills.size,
                                    ),
                                    style = MorpheTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    toggleItem(
                        key = "app_icons",
                        visible = visibility.appIcons,
                        icon = MorpheIcons.Image,
                        title = stringResource(Res.string.settings_toggle_app_icons_label),
                        description = stringResource(Res.string.settings_toggle_app_icons_desc),
                        checked = settings.showAppIcons,
                        onCheckedChange = settings::setShowAppIcons,
                    )

                    toggleItem(
                        key = "more_apps",
                        visible = visibility.moreAppsCard,
                        icon = MorpheIcons.Apps,
                        title = stringResource(Res.string.settings_appearance_more_apps_card),
                        description = stringResource(Res.string.settings_appearance_more_apps_card_description),
                        checked = settings.showMoreAppsCard,
                        onCheckedChange = settings::setShowMoreAppsCard,
                    )
                }
            }
        }

        if (visibility.hasEffectsSection) {
            SettingsSection(
                title = stringResource(Res.string.settings_section_effects),
                icon = MorpheIcons.AutoAwesome,
            ) {
                SettingsGroup {
                    toggleItem(
                        key = "sharp_corners",
                        visible = visibility.sharpCorners,
                        icon = MorpheIcons.RoundedCorner,
                        title = stringResource(Res.string.settings_toggle_sharp_corners_label),
                        description = stringResource(Res.string.settings_toggle_sharp_corners_desc),
                        checked = settings.sharpCorners,
                        onCheckedChange = settings::setSharpCorners,
                    )

                    dropdownItem(
                        key = "bg_type",
                        visible = visibility.backgroundAnimation,
                        icon = MorpheIcons.Wallpaper,
                        title = stringResource(Res.string.settings_appearance_background_animation),
                        description = stringResource(Res.string.settings_appearance_background_animation_description),
                        selectedLabel = settings.backgroundType.localizedLabel(),
                        selectedIcon = settings.backgroundType.icon,
                        maxHeight = 340.dp,
                        items = BackgroundType.entries.map { bg ->
                            MorpheDropdownItem(
                                label = bg.localizedLabel(),
                                icon = bg.icon,
                                selected = settings.backgroundType == bg,
                                onClick = { settings.setBackgroundType(bg) },
                            )
                        },
                    )

                    toggleItem(
                        key = "parallax",
                        visible = visibility.parallax && settings.backgroundType != BackgroundType.NONE,
                        icon = MorpheIcons.Mouse,
                        title = stringResource(Res.string.settings_toggle_parallax_label),
                        description = stringResource(Res.string.settings_toggle_parallax_desc),
                        checked = settings.enableParallax,
                        onCheckedChange = settings::setEnableParallax,
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemePreference.label(): String = when (this) {
    ThemePreference.LIGHT -> stringResource(Res.string.settings_theme_light)
    ThemePreference.DARK -> stringResource(Res.string.settings_theme_dark)
    ThemePreference.AMOLED -> stringResource(Res.string.settings_theme_amoled)
    ThemePreference.SYSTEM -> stringResource(Res.string.settings_theme_system)
}

private fun ThemePreference.icon(): ImageVector = when (this) {
    ThemePreference.LIGHT -> MorpheIcons.LightMode
    ThemePreference.DARK -> MorpheIcons.DarkMode
    ThemePreference.AMOLED -> MorpheIcons.Contrast
    ThemePreference.SYSTEM -> MorpheIcons.Settings
}

@Composable
private fun ThemeStyle.label(): String = when (this) {
    ThemeStyle.MORPHE -> stringResource(Res.string.settings_appearance_style_morphe)
    ThemeStyle.MONOCHROME -> stringResource(Res.string.settings_appearance_monochrome)
}

private fun ThemeStyle.icon(): ImageVector = when (this) {
    ThemeStyle.MORPHE -> MorpheIcons.Palette
    ThemeStyle.MONOCHROME -> MorpheIcons.Contrast
}

@Composable
private fun BackgroundType.localizedLabel(): String = when (this) {
    BackgroundType.CIRCLES -> stringResource(Res.string.settings_background_circles)
    BackgroundType.RINGS -> stringResource(Res.string.settings_background_rings)
    BackgroundType.MESH -> stringResource(Res.string.settings_background_mesh)
    BackgroundType.SPACE -> stringResource(Res.string.settings_background_space)
    BackgroundType.SHAPES -> stringResource(Res.string.settings_background_shapes)
    BackgroundType.SNOW -> stringResource(Res.string.settings_background_snow)
    BackgroundType.GRID -> stringResource(Res.string.settings_background_grid)
    BackgroundType.PARTICLES -> stringResource(Res.string.settings_background_particles)
    BackgroundType.LAVA -> stringResource(Res.string.settings_background_lava)
    BackgroundType.MATRIX -> stringResource(Res.string.settings_background_matrix)
    BackgroundType.NONE -> stringResource(Res.string.none)
}
