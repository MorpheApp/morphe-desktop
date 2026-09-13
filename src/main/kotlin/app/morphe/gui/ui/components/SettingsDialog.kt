/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import app.morphe.engine.MorpheData
import app.morphe.engine.PatchEngine.Config.Companion.DEFAULT_KEYSTORE_ALIAS
import app.morphe.engine.PatchEngine.Config.Companion.DEFAULT_KEYSTORE_PASSWORD
import app.morphe.engine.util.KeystoreImporter
import app.morphe.engine.util.PortablePaths
import app.morphe.gui.LocalBackgroundType
import app.morphe.gui.LocalEnableParallax
import app.morphe.gui.data.constants.AppConstants
import app.morphe.gui.data.model.PatchSource
import app.morphe.gui.data.model.PatchSourceType
import app.morphe.gui.data.model.UpdateChannelPreference
import app.morphe.gui.data.repository.ConfigRepository
import app.morphe.gui.data.repository.LanguageRepository
import app.morphe.gui.ui.components.ChangelogDialog
import app.morphe.gui.ui.components.MorpheColorPickerCard
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.icons.autoMirrored
import app.morphe.gui.ui.theme.THEME_PRESET_COLORS
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheDimens
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.ui.theme.MorpheColors
import app.morphe.gui.ui.theme.ThemePreference
import app.morphe.gui.ui.theme.backgrounds.BackgroundType
import app.morphe.gui.util.AdbManager
import app.morphe.gui.util.DeviceMonitor
import app.morphe.gui.util.FileUtils
import app.morphe.gui.util.FormatUtils
import app.morphe.gui.util.Logger
import app.morphe.gui.util.MorpheFilePicker
import app.morphe.gui.util.currentLocale
import app.morphe.morphe_desktop.generated.resources.*
import app.morphe.patcher.apk.ApkSigner
import java.awt.Desktop
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Provider
import java.security.Security
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
fun SettingsDialog(
    currentTheme: ThemePreference,
    onThemeChange: (ThemePreference) -> Unit,
    autoCleanupTempFiles: Boolean,
    onAutoCleanupChange: (Boolean) -> Unit,
    defaultOutputDirectory: String?,
    onDefaultOutputDirectoryChange: (String?) -> Unit,
    useExpertMode: Boolean,
    onExpertModeChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    isPatching: Boolean = false,
    keystorePath: String? = null,
    keystorePassword: String? = null,
    keystoreAlias: String = DEFAULT_KEYSTORE_ALIAS,
    keystoreEntryPassword: String = DEFAULT_KEYSTORE_PASSWORD,
    onKeystorePathChange: (String?) -> Unit = {},
    onKeystoreCredentialsChange: (password: String?, alias: String, entryPassword: String) -> Unit = { _, _, _ -> },
    keepArchitectures: Set<String> = emptySet(),
    onKeepArchitecturesChange: (Set<String>) -> Unit = {},
    updateChannelPreference: UpdateChannelPreference = UpdateChannelPreference.STABLE,
    onUpdateChannelChange: (UpdateChannelPreference) -> Unit = {},
    autoStartAdb: Boolean = false,
    onAutoStartAdbChange: (Boolean) -> Unit = {},
    developerOptions: Boolean = false,
    onDeveloperOptionsChange: (Boolean) -> Unit = {},
    autoRouteLinksAfterInstall: Boolean = false,
    onAutoRouteLinksChange: (Boolean) -> Unit = {},
    disableStockLinksAfterInstall: Boolean = false,
    onDisableStockLinksChange: (Boolean) -> Unit = {},
    collapsibleSectionStates: Map<String, Boolean> = emptyMap(),
    onCollapsibleSectionToggle: (id: String, expanded: Boolean) -> Unit = { _, _ -> },
    customAccentColorArgb: Int? = null,
    onCustomAccentColorChange: (Int?) -> Unit = {},
    currentLanguage: String = LanguageRepository.SYSTEM_CODE,
    onLanguageChange: (String) -> Unit = {}
) {
    val uriHandler = LocalUriHandler.current
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
    var selectedCategory by remember { mutableStateOf("Appearance") }
    var showCustomColorDialog by remember { mutableStateOf(false) }
    var showChangelogDialog by remember { mutableStateOf(false) }
    var showAppInfoDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }

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

    if (showChangelogDialog) {
        ChangelogDialog(
            onDismiss = { showChangelogDialog = false }
        )
    }

    if (showAppInfoDialog) {
        AppInfoDialog(
            onDismiss = { showAppInfoDialog = false }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(corners.medium),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = stringResource(Res.string.settings_dialog_title),
                fontWeight = FontWeight.SemiBold,
                fontFamily = font,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Row(
                modifier = Modifier.width(640.dp).height(480.dp)
            ) {
                Column(
                    modifier = Modifier
                        .width(160.dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val navItems = listOf(
                        Triple("Appearance", Res.string.settings_nav_appearance, MorpheIcons.Palette),
                        Triple("Advanced", Res.string.settings_nav_advanced, MorpheIcons.Tune),
                        Triple("System", Res.string.settings_nav_system, MorpheIcons.Monitor)
                    )
                    navItems.forEach { (categoryKey, labelRes, icon) ->
                        val isSelected = selectedCategory == categoryKey
                        val hoverInteraction = remember { MutableInteractionSource() }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(corners.small))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                    else Color.Transparent
                                )
                                .hoverable(hoverInteraction)
                                .clickable { selectedCategory = categoryKey }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = stringResource(labelRes),
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                fontFamily = font,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))
                VerticalDivider(color = borderColor, modifier = Modifier.fillMaxHeight())
                Spacer(modifier = Modifier.width(16.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    when (selectedCategory) {
                        "Appearance" -> {
                            SectionLabel(stringResource(Res.string.settings_section_language), font, icon = MorpheIcons.Language)
                            Spacer(Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = currentLanguageOption.flag,
                                    fontSize = 20.sp,
                                    modifier = Modifier.padding(end = 12.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(Res.string.settings_language_current_title),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontFamily = font,
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    val isSystem = currentLanguageOption.code == LanguageRepository.SYSTEM_CODE
                                    Text(
                                        text = if (isSystem) stringResource(Res.string.settings_theme_system) else currentLanguageOption.displayName,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontFamily = font,
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        showLanguageDialog = true
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = MorpheIcons.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .autoMirrored()
                                    )
                                }
                            }

                            Spacer(Modifier.height(8.dp))

                            val linkText = "morphe.software/translate"
                            val linkUrl = "https://morphe.software/translate"
                            val rawTranslationHint = stringResource(Res.string.settings_language_community_translation_hint, linkText)
                            val annotatedTranslationHint = remember(rawTranslationHint, accents.primary) {
                                buildAnnotatedString {
                                    val linkIndex = rawTranslationHint.indexOf(linkText)
                                    if (linkIndex != -1) {
                                        append(rawTranslationHint.substring(0, linkIndex))
                                        withLink(
                                            LinkAnnotation.Url(
                                                url = linkUrl,
                                                styles = TextLinkStyles(
                                                    style = SpanStyle(
                                                        color = accents.primary,
                                                        fontWeight = FontWeight.Medium,
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
                            }

                            Text(
                                text = annotatedTranslationHint,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal,
                                lineHeight = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = font
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
                                    val themeAccent = theme.accentColor()
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

                            SectionLabel(stringResource(Res.string.settings_section_accent_color), font, icon = MorpheIcons.Palette)
                            Spacer(Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(corners.small))
                                        .border(
                                            2.dp,
                                            if (customAccentColorArgb == null) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            RoundedCornerShape(corners.small)
                                        )
                                        .clickable { onCustomAccentColorChange(null) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = MorpheIcons.Close,
                                        contentDescription = stringResource(Res.string.settings_accent_clear_description),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                THEME_PRESET_COLORS.forEach { preset ->
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(corners.small))
                                            .background(preset)
                                            .border(
                                                2.dp,
                                                if (customAccentColorArgb == preset.toArgb()) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                RoundedCornerShape(corners.small)
                                            )
                                            .clickable { onCustomAccentColorChange(preset.toArgb()) }
                                    )
                                }

                                val isCustomNonPreset = customAccentColorArgb != null && THEME_PRESET_COLORS.none { it.toArgb() == customAccentColorArgb }
                                Box {
                                    val yOff = with(LocalDensity.current) { 46.dp.roundToPx() }
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(corners.small))
                                            .background(
                                                if (isCustomNonPreset) Color(customAccentColorArgb) else MaterialTheme.colorScheme.surfaceVariant
                                            )
                                            .border(
                                                2.dp,
                                                if (isCustomNonPreset) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                RoundedCornerShape(corners.small)
                                            )
                                            .clickable { showCustomColorDialog = true },
                                        contentAlignment = Alignment.Center
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
                                                showAlphaAndSaved = false,
                                                onPick = { onCustomAccentColorChange(it) }
                                            )
                                        }
                                    }
                                }
                            }

                            SettingsDivider(borderColor)

                            SectionLabel(stringResource(Res.string.settings_section_background_animation), font, icon = MorpheIcons.Wallpaper)
                            Spacer(Modifier.height(8.dp))

                            val bgState = LocalBackgroundType.current
                            val parallaxState = LocalEnableParallax.current
                            val scope = rememberCoroutineScope()
                            val configRepo: ConfigRepository = koinInject()

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
                                            text = bgType.toDisplayName(),
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
                        "Advanced" -> {
                            SettingToggleRow(
                                label = stringResource(Res.string.settings_toggle_expert_mode_label),
                                description = stringResource(Res.string.settings_toggle_expert_mode_desc),
                                checked = useExpertMode,
                                onCheckedChange = onExpertModeChange,
                                accentColor = accents.primary,
                                font = font,
                                enabled = !isPatching,
                                icon = MorpheIcons.Psychology
                            )

                            SettingsDivider(borderColor)

                            SettingToggleRow(
                                label = stringResource(Res.string.settings_toggle_route_links_label),
                                description = stringResource(Res.string.settings_toggle_route_links_desc),
                                checked = autoRouteLinksAfterInstall,
                                onCheckedChange = onAutoRouteLinksChange,
                                accentColor = accents.primary,
                                font = font,
                                enabled = !isPatching,
                                icon = MorpheIcons.Route
                            )
                            AnimatedVisibility(visible = autoRouteLinksAfterInstall) {
                                Column {
                                    Spacer(Modifier.height(12.dp))
                                    SettingToggleRow(
                                        label = stringResource(Res.string.settings_toggle_disable_stock_links_label),
                                        description = stringResource(Res.string.settings_toggle_disable_stock_links_desc),
                                        checked = disableStockLinksAfterInstall,
                                        onCheckedChange = onDisableStockLinksChange,
                                        accentColor = accents.primary,
                                        font = font,
                                        enabled = !isPatching
                                    )
                                }
                            }

                            SettingsDivider(borderColor)

                            SigningSection(
                                keystorePath = keystorePath,
                                keystorePassword = keystorePassword,
                                keystoreAlias = keystoreAlias,
                                keystoreEntryPassword = keystoreEntryPassword,
                                onKeystorePathChange = onKeystorePathChange,
                                onCredentialsChange = onKeystoreCredentialsChange,
                                font = font,
                                accentColor = accents.primary,
                                borderColor = borderColor,
                                enabled = !isPatching,
                                expanded = collapsibleSectionStates["Signing"] == true,
                                icon = MorpheIcons.Key,
                                onExpandedChange = { onCollapsibleSectionToggle("Signing", it) }
                            )

                            SettingsDivider(borderColor)

                            StripLibsSection(
                                keepArchitectures = keepArchitectures,
                                onChange = onKeepArchitecturesChange,
                                font = font,
                                accentColor = accents.primary,
                                enabled = !isPatching,
                                expanded = collapsibleSectionStates["Strip libs"] == true,
                                icon = MorpheIcons.LayersClear,
                                onExpandedChange = { onCollapsibleSectionToggle("Strip libs", it) }
                            )

                            SettingsDivider(borderColor)

                            SettingToggleRow(
                                label = stringResource(Res.string.settings_toggle_developer_options_label),
                                description = stringResource(Res.string.settings_toggle_developer_options_desc),
                                checked = developerOptions,
                                onCheckedChange = onDeveloperOptionsChange,
                                accentColor = accents.primary,
                                font = font,
                                enabled = !isPatching,
                                icon = MorpheIcons.CodeXml
                            )

                            SettingsDivider(borderColor)

                            PatchedAppRuntimeLogsSection(
                                font = font,
                                accentColor = accents.primary,
                                borderColor = borderColor,
                                enabled = !isPatching,
                                expanded = collapsibleSectionStates["Runtime logs"] == true,
                                icon = MorpheIcons.DeployedCode,
                                onExpandedChange = { onCollapsibleSectionToggle("Runtime logs", it) }
                            )
                        }
                        "System" -> {
                            SettingToggleRow(
                                label = stringResource(Res.string.settings_toggle_auto_cleanup_label),
                                description = stringResource(Res.string.settings_toggle_auto_cleanup_desc),
                                checked = autoCleanupTempFiles,
                                onCheckedChange = onAutoCleanupChange,
                                accentColor = accents.primary,
                                font = font,
                                enabled = !isPatching,
                                icon = MorpheIcons.DeleteSweep
                            )

                            SettingsDivider(borderColor)

                            UpdateChannelRow(
                                selected = updateChannelPreference,
                                onChange = onUpdateChannelChange,
                                accentColor = accents.primary,
                                font = font,
                                borderColor = borderColor,
                                enabled = !isPatching,
                                icon = MorpheIcons.Update
                            )

                            SettingsDivider(borderColor)

                            OutputFolderSection(
                                defaultOutputDirectory = defaultOutputDirectory,
                                onDefaultOutputDirectoryChange = onDefaultOutputDirectoryChange,
                                font = font,
                                borderColor = borderColor,
                                enabled = !isPatching,
                                icon = MorpheIcons.FolderOpen
                            )

                            SettingsDivider(borderColor)

                            SettingToggleRow(
                                label = stringResource(Res.string.settings_toggle_auto_start_adb_label),
                                description = stringResource(Res.string.settings_toggle_auto_start_adb_desc),
                                checked = autoStartAdb,
                                onCheckedChange = onAutoStartAdbChange,
                                accentColor = accents.primary,
                                font = font,
                                enabled = !isPatching,
                                icon = MorpheIcons.ADB
                            )

                            SettingsDivider(borderColor)

                            SectionLabel(stringResource(Res.string.settings_section_about), font, icon = MorpheIcons.Info)
                            Spacer(Modifier.height(16.dp))

                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Image(
                                        painter = painterResource(Res.drawable.morphe_logo),
                                        contentDescription = stringResource(Res.string.morphe_logo_content_description),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Morphe",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontFamily = font,
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = stringResource(Res.string.settings_about_version, AppConstants.APP_VERSION),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontFamily = font,
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            showAppInfoDialog = true
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = MorpheIcons.KeyboardArrowRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .size(16.dp)
                                                .autoMirrored()
                                        )
                                    }
                                }
                                
                                SettingsDivider(borderColor)
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = MorpheIcons.Article,
                                        contentDescription = null,
                                        tint = accents.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(Res.string.settings_about_changelogs_title),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontFamily = font,
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = stringResource(Res.string.settings_about_changelogs_desc),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontFamily = font,
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            showChangelogDialog = true
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = MorpheIcons.KeyboardArrowRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .size(16.dp)
                                                .autoMirrored()
                                        )
                                    }
                                }

                                SettingsDivider(borderColor)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = MorpheIcons.Public,
                                        contentDescription = null,
                                        tint = accents.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(Res.string.settings_about_website_title),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontFamily = font,
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = stringResource(Res.string.settings_about_website_desc),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontFamily = font,
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            uriHandler.openUri("https://morphe.software")
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = MorpheIcons.KeyboardArrowRight,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .size(16.dp)
                                                .autoMirrored()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(corners.small),
                border = BorderStroke(1.dp, borderColor)
            ) {
                Text(
                    stringResource(Res.string.close),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = font
                )
            }
        }
    )
}

// ── Shared building blocks ──

@Composable
private fun SectionLabel(
    text: String,
    font: FontFamily,
    icon: ImageVector? = null
) {
    if (icon != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = text,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = font
            )
        }
    } else {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            fontFamily = font
        )
    }
}

@Composable
private fun CollapsibleSection(
    title: String,
    font: FontFamily,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    icon: ImageVector? = null,
    content: @Composable () -> Unit
) {
    val corners = LocalMorpheCorners.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val rotationAngle by animateFloatAsState(
        targetValue = if (expanded) 90f else (if (isRtl) 180f else 0f),
        animationSpec = tween(200)
    )
    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corners.small))
            .hoverable(hoverInteraction)
            .background(
                if (isHovered) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
                else Color.Transparent
            )
            .clickable { onExpandedChange(!expanded) }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = font
            )
        }
        Icon(
            imageVector = MorpheIcons.KeyboardArrowRight,
            contentDescription = if (expanded) stringResource(Res.string.collapse) else stringResource(Res.string.expand),
            modifier = Modifier
                .size(16.dp)
                .graphicsLayer { rotationZ = rotationAngle },
            tint = MaterialTheme.colorScheme.primary.copy(alpha = if (isHovered) 1f else 0.8f)
        )
    }

    AnimatedVisibility(
        visible = expanded,
        enter = expandVertically(
            expandFrom = Alignment.Top,
            animationSpec = tween(200)
        ) + fadeIn(animationSpec = tween(200)),
        exit = shrinkVertically(
            shrinkTowards = Alignment.Top,
            animationSpec = tween(200)
        ) + fadeOut(animationSpec = tween(150))
    ) {
        Column {
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun SettingsDivider(borderColor: Color) {
    Spacer(Modifier.height(14.dp))
    HorizontalDivider(color = borderColor)
    Spacer(Modifier.height(14.dp))
}

/**
 * Inline row letting the user pick which CLI release channel update checks
 * follow. Mirrors [SettingToggleRow]'s layout — label + dynamic description
 * on the left, chip group on the right where the switch would be.
 */
@Composable
private fun UpdateChannelRow(
    selected: UpdateChannelPreference,
    onChange: (UpdateChannelPreference) -> Unit,
    accentColor: Color,
    font: FontFamily,
    borderColor: Color,
    enabled: Boolean,
    icon: ImageVector? = null,
) {
    val corners = LocalMorpheCorners.current
    val alpha = if (enabled) 1f else 0.5f

    val description = when {
        !enabled -> stringResource(Res.string.disabled_while_patching)
        selected == UpdateChannelPreference.STABLE ->
            stringResource(Res.string.settings_channel_desc_stable)
        selected == UpdateChannelPreference.DEV ->
            stringResource(Res.string.settings_channel_desc_dev)
        else -> stringResource(Res.string.settings_channel_desc_off)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor.copy(alpha = alpha),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(12.dp))
            }
            Column {
                Text(
                    text = stringResource(Res.string.settings_channel_label),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                    fontFamily = font,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = description,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                    fontFamily = font,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        val getLabel: @Composable (UpdateChannelPreference) -> String = { option ->
            when (option) {
                UpdateChannelPreference.STABLE -> stringResource(Res.string.version_label_stable)
                UpdateChannelPreference.DEV -> stringResource(Res.string.version_label_experimental)
                UpdateChannelPreference.OFF -> stringResource(Res.string.off)
            }
        }
        
        MorpheDropdown(
            label = getLabel(selected),
            items = UpdateChannelPreference.entries.map { option ->
                MorpheDropdownItem(
                    label = getLabel(option),
                    onClick = { onChange(option) }
                )
            },
            enabled = enabled,
            modifier = Modifier.width(120.dp)
        )
    }
}

@Composable
private fun SettingToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accentColor: Color,
    font: FontFamily,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    val alpha = if (enabled) 1f else 0.5f
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor.copy(alpha = alpha),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(12.dp))
            }
            Column {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                    fontFamily = font
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (!enabled) stringResource(Res.string.disabled_while_patching) else description,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                    fontFamily = font
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        MorpheSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            accentColor = accentColor,
            enabled = enabled
        )
    }
}

@Composable
private fun OutputFolderSection(
    defaultOutputDirectory: String?,
    onDefaultOutputDirectoryChange: (String?) -> Unit,
    font: FontFamily,
    borderColor: Color,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val corners = LocalMorpheCorners.current
    val dimens = LocalMorpheDimens.current
    val scope = rememberCoroutineScope()
    val alpha = if (enabled) 1f else 0.4f
    val outputDir = defaultOutputDirectory?.let { File(it) }
    val outputDirExists = outputDir?.isDirectory == true

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionLabel(stringResource(Res.string.settings_section_output_folder), font, icon = icon)
        Spacer(Modifier.height(6.dp))

        Text(
            text = if (!enabled) stringResource(Res.string.disabled_while_patching)
                   else stringResource(Res.string.settings_output_folder_desc),
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            fontFamily = font
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth().height(dimens.controlHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(corners.small))
                    .border(1.dp, borderColor, RoundedCornerShape(corners.small))
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = outputDir?.name ?: stringResource(Res.string.settings_output_folder_default),
                    fontSize = 11.sp,
                    fontFamily = font,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            val pickerTitle = stringResource(Res.string.settings_output_folder_picker_title)
            OutlinedButton(
                onClick = {
                    scope.launch {
                        MorpheFilePicker.pickDirectory(
                            title = pickerTitle,
                            startDir = outputDir?.takeIf { it.isDirectory },
                        )?.let { onDefaultOutputDirectoryChange(it.absolutePath) }
                    }
                },
                enabled = enabled,
                shape = RoundedCornerShape(corners.small),
                border = BorderStroke(1.dp, borderColor),
                contentPadding = PaddingValues(horizontal = 10.dp),
                modifier = Modifier.fillMaxHeight()
            ) {
                Text(
                    stringResource(Res.string.browse),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = font
                )
            }

            if (defaultOutputDirectory != null) {
                OutlinedButton(
                    onClick = { onDefaultOutputDirectoryChange(null) },
                    enabled = enabled,
                    shape = RoundedCornerShape(corners.small),
                    border = BorderStroke(1.dp, borderColor),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Text(
                        stringResource(Res.string.settings_dialog_reset_button),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = font
                    )
                }
            }
        }

        if (defaultOutputDirectory != null && !outputDirExists) {
            Text(
                text = stringResource(Res.string.settings_output_folder_not_found),
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = font,
                color = Color(0xFFE0A030)
            )
        }

        // Stored form first (mirrors config.json), absolute resolution second.
        // Hides the second line entirely when storage IS absolute, repeating
        // the same path twice would make no sense now, innit.
        if (defaultOutputDirectory != null) {
            val stored = PortablePaths.storableForm(defaultOutputDirectory)
            val isBundleRelative = stored != defaultOutputDirectory
            Text(
                text = stored,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = font,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isBundleRelative) {
                Text(
                    text = stringResource(Res.string.settings_dialog_resolves_to_message, defaultOutputDirectory),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = font,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}


// ── Strip Libs Section ──

/**
 * Architectures exposed in the strip libs settings. Each entry has the
 * patcher-facing value (matching CpuArchitecture.arch) and a short display name.
 * Only modern arches are listed — legacy mips/armeabi are intentionally omitted.
 */
private data class StripLibsArch(val arch: String, val descriptionRes: StringResource)

private val STRIP_LIBS_ARCHS = listOf(
    StripLibsArch("arm64-v8a", Res.string.settings_strip_libs_arm64),
    StripLibsArch("armeabi-v7a", Res.string.settings_strip_libs_armeabi),
    StripLibsArch("x86_64", Res.string.settings_strip_libs_x86_64),
    StripLibsArch("x86", Res.string.settings_strip_libs_x86)
)

@Composable
private fun StripLibsSection(
    keepArchitectures: Set<String>,
    onChange: (Set<String>) -> Unit,
    font: FontFamily,
    accentColor: Color,
    enabled: Boolean = true,
    expanded: Boolean = false,
    icon: ImageVector? = null,
    onExpandedChange: (Boolean) -> Unit = {}
) {
    CollapsibleSection(
        title = stringResource(Res.string.settings_section_strip_libs),
        font = font,
        expanded = expanded,
        icon = icon,
        onExpandedChange = onExpandedChange
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(Res.string.settings_strip_libs_desc),
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = font
            )
            STRIP_LIBS_ARCHS.forEach { (arch, descriptionRes) ->
                val checked = arch in keepArchitectures
                SettingToggleRow(
                    label = arch,
                    description = stringResource(descriptionRes),
                    checked = checked,
                    onCheckedChange = { keepIt ->
                        val updated = if (keepIt) keepArchitectures + arch
                                      else keepArchitectures - arch
                        onChange(updated)
                    },
                    accentColor = accentColor,
                    font = font,
                    enabled = enabled
                )
            }
        }
    }
}

// ── Signing / Keystore Section ──

@Composable
private fun SigningSection(
    keystorePath: String?,
    keystorePassword: String?,
    keystoreAlias: String,
    keystoreEntryPassword: String,
    onKeystorePathChange: (String?) -> Unit,
    onCredentialsChange: (password: String?, alias: String, entryPassword: String) -> Unit,
    font: FontFamily,
    accentColor: Color,
    borderColor: Color,
    enabled: Boolean = true,
    expanded: Boolean = false,
    icon: ImageVector? = null,
    onExpandedChange: (Boolean) -> Unit = {}
) {
    val corners = LocalMorpheCorners.current
    val dimens = LocalMorpheDimens.current
    val accents = LocalMorpheAccents.current
    val alpha = if (enabled) 1f else 0.4f
    val scope = rememberCoroutineScope()

    var localPassword by remember(keystorePassword) { mutableStateOf(keystorePassword ?: "") }
    var localAlias by remember(keystoreAlias) { mutableStateOf(keystoreAlias) }
    var localEntryPassword by remember(keystoreEntryPassword) { mutableStateOf(keystoreEntryPassword) }
    var showPassword by remember { mutableStateOf(false) }
    var showEntryPassword by remember { mutableStateOf(false) }
    var showKeystoreInfo by remember { mutableStateOf(false) }
    var keystoreError by remember { mutableStateOf<String?>(null) }

    val keystoreFile = keystorePath?.let { File(it) }
    val keystoreExists = keystoreFile?.exists() == true

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CollapsibleSection(
            title = stringResource(Res.string.settings_section_signing),
            font = font,
            expanded = expanded,
            icon = icon,
            onExpandedChange = onExpandedChange
        ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = if (!enabled) stringResource(Res.string.disabled_while_patching)
                   else stringResource(Res.string.settings_signing_desc),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            fontFamily = font,
            fontWeight = FontWeight.Normal
        )

        Spacer(Modifier.height(8.dp))

        // Keystore path row
        Row(
            modifier = Modifier.fillMaxWidth().height(dimens.controlHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(corners.small))
                    .border(1.dp, borderColor, RoundedCornerShape(corners.small))
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = if (keystorePath != null) {
                        keystoreFile?.name ?: keystorePath
                    } else stringResource(Res.string.settings_signing_default_keystore),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = font,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            val pickerTitle = stringResource(Res.string.settings_signing_picker_title)
            val invalidFileTypeMsg = stringResource(Res.string.settings_signing_invalid_file_type, ".keystore, .jks, .bks, .p12, .pfx")
            OutlinedButton(
                onClick = {
                    scope.launch {
                        val selected = MorpheFilePicker.pickFile(
                            title = pickerTitle,
                            extensions = listOf("keystore", "jks", "bks", "p12", "pfx"),
                        ) ?: return@launch
                        val validExtensions = listOf(".keystore", ".jks", ".bks", ".p12", ".pfx")
                        if (validExtensions.any { selected.name.lowercase().endsWith(it) }) {
                            val result = KeystoreImporter.ensureBks(
                                source = selected,
                                convertedOutput = MorpheData.importedKeystoreFile,
                                alias = keystoreAlias,
                                password = keystoreEntryPassword,
                            )
                            when (result) {
                                is KeystoreImporter.Result.AlreadyBks -> {
                                    keystoreError = null
                                    onKeystorePathChange(result.file.absolutePath)
                                }
                                is KeystoreImporter.Result.Converted -> {
                                    keystoreError = null
                                    Logger.info(
                                        "Converted ${result.sourceFormat.displayName} → BKS for ${selected.name}"
                                    )
                                    onKeystorePathChange(result.file.absolutePath)
                                }
                                is KeystoreImporter.Result.Failed -> {
                                    keystoreError = result.reason
                                    result.cause?.let {
                                        Logger.error("Keystore import failed for ${selected.name}", it)
                                    }
                                }
                            }
                        } else {
                            keystoreError = invalidFileTypeMsg
                        }
                    }
                },
                enabled = enabled,
                shape = RoundedCornerShape(corners.small),
                border = BorderStroke(1.dp, borderColor),
                contentPadding = PaddingValues(horizontal = 10.dp),
                modifier = Modifier.fillMaxHeight()
            ) {
                Text(
                    stringResource(Res.string.browse),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = font
                )
            }

            if (keystorePath != null) {
                OutlinedButton(
                    onClick = { onKeystorePathChange(null) },
                    enabled = enabled,
                    shape = RoundedCornerShape(corners.small),
                    border = BorderStroke(1.dp, borderColor),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Text(
                        stringResource(Res.string.settings_dialog_reset_button),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = font
                    )
                }
            }
        }

        if (keystorePath != null && !keystoreExists) {
            Text(
                text = stringResource(Res.string.settings_signing_not_found),
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = font,
                color = Color(0xFFE0A030)
            )
        }

        keystoreError?.let {
            Text(
                text = it,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = font,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (keystorePath != null) {
            val stored = PortablePaths.storableForm(keystorePath)
            val isBundleRelative = stored != keystorePath
            Text(
                text = stored,
                fontSize = 11.sp,
                fontFamily = font,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isBundleRelative) {
                Text(
                    text = stringResource(Res.string.settings_dialog_resolves_to_message, keystorePath),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = font,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            val defaultAbs = MorpheData.defaultKeystoreFile.absolutePath
            val defaultStored = PortablePaths.storableForm(defaultAbs)
            val isBundleRelative = defaultStored != defaultAbs
            val defaultMsg = if (MorpheData.defaultKeystoreFile.exists())
                stringResource(Res.string.settings_signing_using_default, defaultStored)
            else
                stringResource(Res.string.settings_signing_will_create_default, defaultStored)
            Text(
                text = defaultMsg,
                fontSize = 11.sp,
                fontFamily = font,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (isBundleRelative) {
                Text(
                    text = stringResource(Res.string.settings_dialog_resolves_to_message, defaultAbs),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = font,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LabeledField(label = stringResource(Res.string.settings_signing_keystore_password_label), font = font) {
                SlimTextField(
                    value = localPassword,
                    onValueChange = {
                        localPassword = it
                        onCredentialsChange(it.ifEmpty { null }, localAlias, localEntryPassword)
                    },
                    placeholder = "",
                    font = font,
                    accents = accents,
                    corners = corners,
                    enabled = enabled,
                    visualTransformation = if (showPassword) VisualTransformation.None
                                           else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    trailing = {
                        IconButton(
                            onClick = { showPassword = !showPassword },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                imageVector = if (showPassword) MorpheIcons.VisibilityOff else MorpheIcons.Visibility,
                                contentDescription = if (showPassword) stringResource(Res.string.settings_signing_password_hide) else stringResource(Res.string.settings_signing_password_show),
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                        }
                    },
                )
            }

            LabeledField(label = stringResource(Res.string.settings_signing_key_alias_label), font = font) {
                SlimTextField(
                    value = localAlias,
                    onValueChange = {
                        localAlias = it
                        onCredentialsChange(localPassword.ifEmpty { null }, it, localEntryPassword)
                    },
                    placeholder = "",
                    font = font,
                    accents = accents,
                    corners = corners,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            LabeledField(label = stringResource(Res.string.settings_signing_key_password_label), font = font) {
                SlimTextField(
                    value = localEntryPassword,
                    onValueChange = {
                        localEntryPassword = it
                        onCredentialsChange(localPassword.ifEmpty { null }, localAlias, it)
                    },
                    placeholder = "",
                    font = font,
                    accents = accents,
                    corners = corners,
                    enabled = enabled,
                    visualTransformation = if (showEntryPassword) VisualTransformation.None
                                           else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    trailing = {
                        IconButton(
                            onClick = { showEntryPassword = !showEntryPassword },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                imageVector = if (showEntryPassword) MorpheIcons.VisibilityOff else MorpheIcons.Visibility,
                                contentDescription = if (showEntryPassword) stringResource(Res.string.settings_signing_password_hide) else stringResource(Res.string.settings_signing_password_show),
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                        }
                    },
                )
            }
        }

        // Verify credentials button
        var verifyState by remember { mutableStateOf<VerifyKeystoreState?>(null) }

        if (keystoreExists) {
            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = {
                    verifyState = null
                    val result = readKeystoreInfo(
                        keystorePath,
                        localPassword.ifEmpty { null },
                        localAlias.ifEmpty { DEFAULT_KEYSTORE_ALIAS },
                        localEntryPassword.ifEmpty { DEFAULT_KEYSTORE_PASSWORD }
                    )
                    verifyState = when {
                        result == null -> VerifyKeystoreState.CouldNotOpen
                        result.warnings.isNotEmpty() -> VerifyKeystoreState.Warning(result.warnings.first())
                        else -> VerifyKeystoreState.Valid
                    }
                },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().height(dimens.controlHeight),
                shape = RoundedCornerShape(corners.small),
                border = BorderStroke(
                    1.dp,
                    when (verifyState) {
                        is VerifyKeystoreState.Valid -> MorpheColors.Teal.copy(alpha = 0.4f)
                        is VerifyKeystoreState.CouldNotOpen, is VerifyKeystoreState.Warning -> Color(0xFFE0A030).copy(alpha = 0.4f)
                        null -> borderColor
                    }
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            ) {
                Icon(
                    imageVector = MorpheIcons.Check,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(Res.string.settings_signing_verify_button),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = font
                )
            }

            verifyState?.let { state ->
                Spacer(Modifier.height(4.dp))
                val isSuccess = state is VerifyKeystoreState.Valid
                val text = when (state) {
                    VerifyKeystoreState.CouldNotOpen -> stringResource(Res.string.settings_signing_verify_could_not_open)
                    is VerifyKeystoreState.Warning -> when (val w = state.warning) {
                        is KeystoreWarning.AliasNotFound -> stringResource(Res.string.settings_cert_warning_alias_not_found, w.alias)
                        is KeystoreWarning.KeyPasswordIncorrect -> stringResource(Res.string.settings_cert_warning_key_password_incorrect, w.alias)
                    }
                    VerifyKeystoreState.Valid -> stringResource(Res.string.settings_signing_verify_valid)
                }
                Text(
                    text = text,
                    fontSize = 11.sp,
                    fontFamily = font,
                    fontWeight = FontWeight.Normal,
                    color = if (isSuccess) MorpheColors.Teal else Color(0xFFE0A030),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Generate button (only when no keystore exists yet)
        var generateError by remember { mutableStateOf<String?>(null) }
        var generateSuccess by remember { mutableStateOf(false) }

        val saveKeystoreTitle = stringResource(Res.string.settings_signing_picker_save_keystore)

        if (!keystoreExists) {
            OutlinedButton(
                onClick = {
                    generateError = null
                    generateSuccess = false
                    scope.launch {
                        val path = keystorePath ?: run {
                            val chosen = MorpheFilePicker.saveFile(
                                title = saveKeystoreTitle,
                                baseName = "morphe",
                                extension = "keystore",
                            ) ?: return@launch
                            val chosenPath = chosen.absolutePath
                            onKeystorePathChange(chosenPath)
                            chosenPath
                        }

                        try {
                            val file = File(path)
                            file.parentFile?.mkdirs()
                            val keyPair = ApkSigner.newPrivateKeyCertificatePair(
                                "Morphe",
                                Date(System.currentTimeMillis() + 8L * 365 * 24 * 60 * 60 * 1000))
                            val ks = ApkSigner.newKeyStore(setOf(
                                ApkSigner.KeyStoreEntry(
                                    localAlias.ifEmpty { DEFAULT_KEYSTORE_ALIAS },
                                    localEntryPassword.ifEmpty { DEFAULT_KEYSTORE_PASSWORD },
                                    keyPair
                                )
                            ))
                            file.outputStream().use {
                                ks.store(it, localPassword.ifEmpty { null }?.toCharArray())
                            }
                            onCredentialsChange(
                                localPassword.ifEmpty { null },
                                localAlias.ifEmpty { DEFAULT_KEYSTORE_ALIAS },
                                localEntryPassword.ifEmpty { DEFAULT_KEYSTORE_PASSWORD }
                            )
                            generateSuccess = true
                        } catch (e: Exception) {
                            generateError = e.message ?: ""
                            Logger.error("Failed to generate keystore", e)
                        }
                    }
                },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().height(dimens.controlHeight),
                shape = RoundedCornerShape(corners.small),
                border = BorderStroke(
                    1.dp, if (generateSuccess)
                        MorpheColors.Teal.copy(alpha = 0.4f)
                    else accentColor.copy(alpha = 0.3f)
                ),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            ) {
                Icon(
                    imageVector = MorpheIcons.Add,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = if (generateSuccess) MorpheColors.Teal else accentColor
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (generateSuccess) stringResource(Res.string.settings_signing_generated_button) else stringResource(Res.string.settings_signing_generate_button),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    color = if (generateSuccess) MorpheColors.Teal else accentColor,
                    fontFamily = font
                )
            }

            generateError?.let {
                Text(
                    text = stringResource(Res.string.settings_signing_failed_to_generate, it),
                    fontSize = 11.sp,
                    fontFamily = font,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (!generateSuccess) {
                Text(
                    text = stringResource(Res.string.settings_signing_uses_credentials_hint),
                    fontSize = 11.sp,
                    fontFamily = font,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(4.dp))
        }

        val exportKeystoreTitle = stringResource(Res.string.settings_signing_picker_export_title)
        // Action buttons row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Certificate info
            OutlinedButton(
                onClick = { showKeystoreInfo = true },
                enabled = enabled && keystoreExists,
                shape = RoundedCornerShape(corners.small),
                border = BorderStroke(1.dp, borderColor),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = MorpheIcons.Info,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(Res.string.settings_signing_certificate_button),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = font
                )
            }

            // Export
            OutlinedButton(
                onClick = {
                    val sourceFile = keystoreFile ?: return@OutlinedButton
                    if (!sourceFile.exists()) return@OutlinedButton
                    scope.launch {
                        val dest = MorpheFilePicker.saveFile(
                            title = exportKeystoreTitle,
                            baseName = sourceFile.nameWithoutExtension,
                            extension = sourceFile.extension.ifEmpty { "keystore" },
                        ) ?: return@launch
                        try {
                            sourceFile.copyTo(dest, overwrite = true)
                        } catch (e: Exception) {
                            Logger.error("Failed to export keystore", e)
                        }
                    }
                },
                enabled = enabled && keystoreExists,
                shape = RoundedCornerShape(corners.small),
                border = BorderStroke(1.dp, borderColor),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = MorpheIcons.Share,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(Res.string.settings_signing_export_button),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = font
                )
            }
        }
        } // inner Column
        } // CollapsibleSection
    }

    // Certificate info dialog
    if (showKeystoreInfo && keystorePath != null) {
        KeystoreInfoDialog(
            keystorePath = keystorePath,
            password = keystorePassword,
            alias = keystoreAlias,
            entryPassword = keystoreEntryPassword,
            onDismiss = { showKeystoreInfo = false }
        )
    }
}

@Composable
private fun KeystoreInfoDialog(
    keystorePath: String,
    password: String?,
    alias: String,
    entryPassword: String,
    onDismiss: () -> Unit
) {
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)

    val info = remember(keystorePath, password, alias, entryPassword) {
        readKeystoreInfo(keystorePath, password, alias, entryPassword)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(corners.medium),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                stringResource(Res.string.settings_cert_info_title),
                fontFamily = font,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
        },
        text = {
            if (info != null) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.widthIn(min = 300.dp)
                ) {
                    // Show warnings first if there are any
                    if (info.warnings.isNotEmpty()) {
                        info.warnings.forEach { warning ->
                            val warningText = when (warning) {
                                is KeystoreWarning.AliasNotFound -> stringResource(Res.string.settings_cert_warning_alias_not_found, warning.alias)
                                is KeystoreWarning.KeyPasswordIncorrect -> stringResource(Res.string.settings_cert_warning_key_password_incorrect, warning.alias)
                            }
                            Text(
                                text = warningText,
                                fontSize = 11.sp,
                                fontFamily = font,
                                fontWeight = FontWeight.Normal,
                                color = Color(0xFFE0A030),
                                lineHeight = 14.sp
                            )
                        }
                        if (info.sha256Fingerprint.isEmpty()) return@Column
                        HorizontalDivider(color = borderColor)
                    }

                    val locale = currentLocale()
                    CertInfoRow(stringResource(Res.string.settings_cert_info_alias), info.alias, font)
                    CertInfoRow(stringResource(Res.string.settings_cert_info_issuer), info.issuer, font)
                    CertInfoRow(stringResource(Res.string.settings_cert_info_valid_from), FormatUtils.formatDate(info.validFrom, locale), font)
                    CertInfoRow(stringResource(Res.string.settings_cert_info_valid_until), FormatUtils.formatDate(info.validTo, locale), font)

                    HorizontalDivider(color = borderColor)

                    Text(
                        stringResource(Res.string.settings_cert_info_sha256),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = font,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SelectionContainer {
                        Text(
                            text = info.sha256Fingerprint,
                            fontSize = 11.sp,
                            fontFamily = font,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 16.sp
                        )
                    }

                    HorizontalDivider(color = borderColor)

                    Text(
                        stringResource(Res.string.settings_cert_info_sha1),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = font,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SelectionContainer {
                        Text(
                            text = info.sha1Fingerprint,
                            fontSize = 11.sp,
                            fontFamily = font,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 16.sp
                        )
                    }
                }
            } else {
                Text(
                    text = stringResource(Res.string.settings_cert_info_could_not_read),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = font,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(corners.small),
                border = BorderStroke(1.dp, borderColor)
            ) {
                Text(
                    stringResource(Res.string.close),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = font
                )
            }
        }
    )
}

@Composable
private fun CertInfoRow(
    label: String,
    value: String,
    font: FontFamily
) {
    Column {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = font,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = value,
            fontSize = 11.sp,
            fontFamily = font,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private sealed interface VerifyKeystoreState {
    data object CouldNotOpen : VerifyKeystoreState
    data class Warning(val warning: KeystoreWarning) : VerifyKeystoreState
    data object Valid : VerifyKeystoreState
}

private sealed interface KeystoreWarning {
    data class AliasNotFound(val alias: String) : KeystoreWarning
    data class KeyPasswordIncorrect(val alias: String) : KeystoreWarning
}

private data class KeystoreInfoResult(
    val alias: String,
    val issuer: String,
    val validFrom: Date? = null,
    val validTo: Date? = null,
    val sha256Fingerprint: String,
    val sha1Fingerprint: String,
    val warnings: List<KeystoreWarning> = emptyList()
)

private fun readKeystoreInfo(
    keystorePath: String,
    password: String?,
    alias: String,
    entryPassword: String? = null
): KeystoreInfoResult? {
    val file = File(keystorePath)
    if (!file.exists()) return null

    val passwordChars = password?.toCharArray() ?: charArrayOf()

    try {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(
                Class.forName("org.bouncycastle.jce.provider.BouncyCastleProvider")
                    .getDeclaredConstructor().newInstance() as Provider
            )
        }
    } catch (_: Exception) {
    }

    val types = listOf("BKS" to "BC", "BKS" to null, "JKS" to null, "PKCS12" to null)
    for ((type, provider) in types) {
        try {
            val ks = if (provider != null) {
                KeyStore.getInstance(type, provider)
            } else {
                KeyStore.getInstance(type)
            }

            file.inputStream().use { ks.load(it, passwordChars) }

            val warnings = mutableListOf<KeystoreWarning>()

            if (!ks.containsAlias(alias)) {
                return KeystoreInfoResult(
                    alias = alias,
                    issuer = "",
                    validFrom = null,
                    validTo = null,
                    sha256Fingerprint = "",
                    sha1Fingerprint = "",
                    warnings = listOf(KeystoreWarning.AliasNotFound(alias))
                )
            }

            val cert = ks.getCertificate(alias) as? X509Certificate ?: continue

            try {
                ks.getKey(alias, entryPassword?.toCharArray() ?: charArrayOf())
            } catch (_: Exception) {
                return KeystoreInfoResult(
                    alias = alias,
                    issuer = "",
                    validFrom = null,
                    validTo = null,
                    sha256Fingerprint = "",
                    sha1Fingerprint = "",
                    warnings = listOf(KeystoreWarning.KeyPasswordIncorrect(alias))
                )
            }

            val sha256 = MessageDigest.getInstance("SHA-256")
                .digest(cert.encoded)
                .joinToString(":") { "%02X".format(it) }

            val sha1 = MessageDigest.getInstance("SHA-1")
                .digest(cert.encoded)
                .joinToString(":") { "%02X".format(it) }

            return KeystoreInfoResult(
                alias = alias,
                issuer = cert.issuerX500Principal.name,
                validFrom = cert.notBefore,
                validTo = cert.notAfter,
                sha256Fingerprint = sha256,
                sha1Fingerprint = sha1,
                warnings = warnings
            )
        } catch (_: Exception) {
            continue
        }
    }
    return null
}

@Composable
private fun ThemePreference.toDisplayName(): String {
    return when (this) {
        ThemePreference.LIGHT -> stringResource(Res.string.settings_theme_light)
        ThemePreference.DARK -> stringResource(Res.string.settings_theme_dark)
        ThemePreference.PURE_BLACK -> stringResource(Res.string.settings_theme_pure_black)
        ThemePreference.SYSTEM -> stringResource(Res.string.settings_theme_system)
    }
}

@Composable
private fun BackgroundType.toDisplayName(): String {
    return when (this) {
        BackgroundType.CIRCLES -> stringResource(Res.string.settings_background_circles)
        BackgroundType.RINGS -> stringResource(Res.string.settings_background_rings)
        BackgroundType.MESH -> stringResource(Res.string.settings_background_mesh)
        BackgroundType.SPACE -> stringResource(Res.string.settings_background_space)
        BackgroundType.SHAPES -> stringResource(Res.string.settings_background_shapes)
        BackgroundType.SNOW -> stringResource(Res.string.settings_background_snow)
        BackgroundType.GRID -> stringResource(Res.string.settings_background_grid)
        BackgroundType.PARTICLES -> stringResource(Res.string.settings_background_particles)
        BackgroundType.MATRIX -> stringResource(Res.string.settings_background_matrix)
        BackgroundType.NONE -> stringResource(Res.string.none)
    }
}

private fun ThemePreference.icon(): ImageVector {
    return when (this) {
        ThemePreference.LIGHT -> MorpheIcons.LightMode
        ThemePreference.DARK -> MorpheIcons.DarkMode
        ThemePreference.PURE_BLACK -> MorpheIcons.Contrast
        ThemePreference.SYSTEM -> MorpheIcons.Settings
    }
}

private fun ThemePreference.accentColor(): Color {
    return when (this) {
        ThemePreference.LIGHT -> Color(0xFF005FAC)
        ThemePreference.DARK -> Color(0xFFA4C9FF)
        ThemePreference.PURE_BLACK -> Color(0xFFA4C9FF)
        ThemePreference.SYSTEM -> Color(0xFFA4C9FF)
    }
}

// ── Patched App Runtime Logs Section ──

private sealed interface RuntimeLogsStatus {
    data object Idle : RuntimeLogsStatus
    data object Clearing : RuntimeLogsStatus
    data object Saving : RuntimeLogsStatus
    data object Cleared : RuntimeLogsStatus
    data class Saved(val file: File, val lineCount: Int) : RuntimeLogsStatus
    data class Error(val message: String) : RuntimeLogsStatus
}

@Composable
private fun PatchedAppRuntimeLogsSection(
    font: FontFamily,
    accentColor: Color,
    borderColor: Color,
    enabled: Boolean = true,
    expanded: Boolean = false,
    icon: ImageVector? = null,
    onExpandedChange: (Boolean) -> Unit = {}
) {
    val monitorState by DeviceMonitor.state.collectAsState()
    val selectedDevice = monitorState.selectedDevice
    val scope = rememberCoroutineScope()
    val adbManager = remember { AdbManager() }
    var status by remember { mutableStateOf<RuntimeLogsStatus>(RuntimeLogsStatus.Idle) }

    val isWorking = status is RuntimeLogsStatus.Clearing || status is RuntimeLogsStatus.Saving
    val deviceReady = selectedDevice?.isReady == true
    val canAct = enabled && deviceReady && !isWorking

    val failedToClearMsg = stringResource(Res.string.settings_runtime_logs_failed_to_clear)
    val failedToSaveMsg = stringResource(Res.string.settings_runtime_logs_failed_to_save)

    CollapsibleSection(
        title = stringResource(Res.string.settings_runtime_logs_title),
        font = font,
        expanded = expanded,
        icon = icon,
        onExpandedChange = onExpandedChange
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(Res.string.settings_runtime_logs_desc),
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = font
            )

            // Device row
            if (deviceReady) {
                val devInfo = "${selectedDevice.displayName}${selectedDevice.architecture?.let { " ($it)" } ?: ""}"
                Text(
                    text = stringResource(Res.string.settings_runtime_logs_device_info, devInfo),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = font,
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else {
                Text(
                    text = stringResource(Res.string.settings_runtime_logs_no_device),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = font,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            ActionButton(
                label = if (status is RuntimeLogsStatus.Clearing) stringResource(Res.string.settings_runtime_logs_clearing) else stringResource(Res.string.settings_runtime_logs_clear_button),
                icon = MorpheIcons.DeleteSweep,
                font = font,
                borderColor = borderColor,
                enabled = canAct,
                onClick = {
                    val device = selectedDevice ?: return@ActionButton
                    status = RuntimeLogsStatus.Clearing
                    scope.launch {
                        val result = adbManager.clearLogcat(device.id)
                        status = result.fold(
                            onSuccess = { RuntimeLogsStatus.Cleared },
                            onFailure = { RuntimeLogsStatus.Error(it.message ?: failedToClearMsg) }
                        )
                    }
                }
            )

            ActionButton(
                label = if (status is RuntimeLogsStatus.Saving) stringResource(Res.string.status_saving) else stringResource(Res.string.settings_runtime_logs_save_button),
                icon = MorpheIcons.Save,
                font = font,
                borderColor = borderColor,
                contentColor = accentColor,
                enabled = canAct,
                onClick = {
                    val device = selectedDevice ?: return@ActionButton
                    status = RuntimeLogsStatus.Saving
                    scope.launch {
                        val timestamp = SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.US).format(Date())
                        val outFile = File(FileUtils.getLogsDir(), "device-logcat-$timestamp.txt")
                        val result = adbManager.captureLogcat(device.id, outFile)
                        status = result.fold(
                            onSuccess = { count -> RuntimeLogsStatus.Saved(outFile, count) },
                            onFailure = { RuntimeLogsStatus.Error(it.message ?: failedToSaveMsg) }
                        )
                    }
                }
            )

            // Status line
            when (val s = status) {
                RuntimeLogsStatus.Idle, RuntimeLogsStatus.Clearing, RuntimeLogsStatus.Saving -> Unit
                RuntimeLogsStatus.Cleared -> Text(
                    text = stringResource(Res.string.settings_runtime_logs_cleared_success),
                    fontSize = 11.sp,
                    fontFamily = font,
                    fontWeight = FontWeight.Normal,
                    color = accentColor
                )
                is RuntimeLogsStatus.Saved -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (s.lineCount == 0)
                            stringResource(Res.string.settings_runtime_logs_nothing_captured)
                        else
                            pluralStringResource(Res.plurals.settings_runtime_logs_saved_success, s.lineCount, s.lineCount, s.file.name),
                        fontSize = 11.sp,
                        fontFamily = font,
                        fontWeight = FontWeight.Normal,
                        color = if (s.lineCount == 0) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                else accentColor
                    )
                    if (s.lineCount > 0) {
                        val cornersLocal = LocalMorpheCorners.current
                        Text(
                            text = stringResource(Res.string.settings_dialog_open_logs_button),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = font,
                            color = accentColor,
                            modifier = Modifier
                                .clip(RoundedCornerShape(cornersLocal.small))
                                .clickable {
                                    try {
                                        if (Desktop.isDesktopSupported()) {
                                            Desktop.getDesktop().open(s.file.parentFile)
                                        }
                                    } catch (e: Exception) {
                                        Logger.error("Failed to reveal logs folder", e)
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
                is RuntimeLogsStatus.Error -> Text(
                    text = s.message,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = font,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

// (Excluded-patterns editor moved to SourceManagementSheet, under the sources it applies to.)
