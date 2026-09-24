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
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import app.morphe.gui.ui.components.handCursor
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont
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
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    var showCustomColorDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val configRepo: ConfigRepository = koinInject()
    val languageRepository: LanguageRepository = remember { LanguageRepository() }
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
            font = font,
            languageRepository = languageRepository
        )
    }

    SectionLabel(stringResource(Res.string.settings_section_language), font, icon = MorpheIcons.Language)
    Spacer(Modifier.height(8.dp))

    val isSystem = currentLanguageOption.code == LanguageRepository.SYSTEM_CODE
    AboutRow(
        title = stringResource(Res.string.settings_language_current_title),
        subtitle = if (isSystem) stringResource(Res.string.settings_theme_system) else currentLanguageOption.displayName,
        font = font,
        onClick = { showLanguageDialog = true },
    ) {
        Text(
            text = currentLanguageOption.flag,
            fontSize = 20.sp,
        )
    }

    Spacer(Modifier.height(8.dp))

    val linkText = "morphe.software/translate"
    val linkUrl = "https://morphe.software/translate"
    val rawTranslationHint = stringResource(Res.string.settings_language_community_translation_hint, linkText)
    val linkIndex = rawTranslationHint.indexOf(linkText)
    val annotatedString = buildAnnotatedString {
        if (linkIndex != -1) {
            append(rawTranslationHint.substring(0, linkIndex))
            withLink(
                LinkAnnotation.Url(
                    linkUrl,
                    TextLinkStyles(
                        style = SpanStyle(
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline
                        )
                    )
                )
            ) {
                append(linkText)
            }
            append(rawTranslationHint.substring(linkIndex + linkText.length))
        } else {
            append(rawTranslationHint)
        }
    }

    Text(
        text = annotatedString,
        fontSize = 11.sp,
        fontWeight = FontWeight.Normal,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
        fontFamily = font,
        lineHeight = 14.sp
    )

    SettingsDivider(borderColor)

    SectionLabel(stringResource(Res.string.settings_section_theme), font, icon = MorpheIcons.Palette)
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
                    text = stringResource(theme.displayNameRes),
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

    val sharpCornersState = LocalSharpCorners.current
    SettingToggleRow(
        label = stringResource(Res.string.settings_toggle_sharp_corners_label),
        description = stringResource(Res.string.settings_toggle_sharp_corners_desc),
        checked = sharpCornersState.value,
        onCheckedChange = { enabled ->
            sharpCornersState.value = enabled
            scope.launch { configRepo.setUseSharpCorners(enabled) }
        },
        accentColor = accents.primary,
        font = font,
        icon = MorpheIcons.RoundedCorner
    )

    SettingsDivider(borderColor)

    SectionLabel(stringResource(Res.string.settings_section_accent_color), font, icon = MorpheIcons.Palette)
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

    SectionLabel(stringResource(Res.string.settings_section_app_cards), font, icon = MorpheIcons.Gradient)
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
                text = stringResource(Res.string.settings_app_cards_color_all),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = font,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(Res.string.settings_app_cards_color_all_desc),
                fontSize = 11.sp,
                fontFamily = font,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MorpheChoiceChip(
            text = stringResource(Res.string.customize),
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
                text = pluralStringResource(Res.plurals.settings_app_cards_override_count, cardFills.fills.size, cardFills.fills.size),
                fontSize = 11.sp,
                fontFamily = font,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            MorpheChoiceChip(
                text = stringResource(Res.string.settings_dialog_reset_button),
                active = false,
                font = font,
                onClick = { cardFills.onClearAll() },
            )
        }
    }

    SettingsDivider(borderColor)

    SectionLabel(stringResource(Res.string.settings_section_patch_list), font, icon = MorpheIcons.Extension)
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
        accentColor = accents.primary,
        font = font,
        icon = MorpheIcons.Category
    )

    SettingsDivider(borderColor)

    SectionLabel(stringResource(Res.string.settings_section_background_animation), font, icon = MorpheIcons.Wallpaper)
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
                    text = stringResource(bgType.displayNameRes),
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
        label = stringResource(Res.string.settings_toggle_parallax_label),
        description = stringResource(Res.string.settings_toggle_parallax_desc),
        checked = parallaxState.value,
        onCheckedChange = {
            parallaxState.value = it
            scope.launch { configRepo.setEnableParallax(it) }
        },
        accentColor = accents.primary,
        font = font,
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
