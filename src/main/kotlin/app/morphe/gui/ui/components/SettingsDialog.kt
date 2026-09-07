/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.rememberScrollbarAdapter
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import app.morphe.engine.MorpheData
import app.morphe.engine.PatchEngine.Config.Companion.DEFAULT_KEYSTORE_ALIAS
import app.morphe.engine.PatchEngine.Config.Companion.DEFAULT_KEYSTORE_PASSWORD
import app.morphe.engine.util.KeystoreImporter
import app.morphe.engine.util.PortablePaths
import app.morphe.gui.LocalBackgroundType
import app.morphe.gui.LocalSharpCorners
import app.morphe.gui.LocalEnableParallax
import app.morphe.gui.data.constants.AppConstants
import app.morphe.gui.data.model.UpdateChannelPreference
import app.morphe.gui.data.repository.ConfigRepository
import app.morphe.gui.ui.components.ChangelogDialog
import app.morphe.gui.ui.components.color.CustomSwatches
import androidx.compose.foundation.shape.CircleShape
import app.morphe.gui.ui.components.LocalCardFills
import app.morphe.gui.ui.components.MorpheColorPickerCard
import app.morphe.gui.ui.icons.MorpheIcons
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
import app.morphe.gui.util.Logger
import app.morphe.gui.util.MorpheFilePicker
import app.morphe.morphe_desktop.generated.resources.Res
import app.morphe.morphe_desktop.generated.resources.morphe_logo
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
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
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
    onCustomAccentColorChange: (Int?) -> Unit = {}
) {
    val uriHandler = LocalUriHandler.current
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
    var selectedCategory by remember { mutableStateOf("Appearance") }
    val contentScroll = remember(selectedCategory) { ScrollState(0) }
    var showCustomColorDialog by remember { mutableStateOf(false) }
    var showChangelogDialog by remember { mutableStateOf(false) }
    var showAppInfoDialog by remember { mutableStateOf(false) }

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

    val windowSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val dialogWidth = with(density) { (windowSize.width * 0.86f).toDp() }
        .coerceIn(720.dp, 1280.dp)
    val dialogHeight = with(density) { (windowSize.height * 0.62f).toDp() }
        .coerceIn(480.dp, 620.dp)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        MorpheDialogSurface(
            modifier = Modifier.width(dialogWidth),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = "Settings",
                fontWeight = FontWeight.SemiBold,
                fontFamily = font,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                modifier = Modifier.fillMaxWidth().height(dialogHeight)
            ) {
                Column(
                    modifier = Modifier
                        .width(160.dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val navItems = listOf(
                        "Appearance" to MorpheIcons.Palette,
                        "Advanced" to MorpheIcons.Tune,
                        "System" to MorpheIcons.Monitor
                    )
                    navItems.forEach { (category, icon) ->
                        val isSelected = selectedCategory == category
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
                                .handCursor()
                                .clickable { selectedCategory = category }
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
                                text = category,
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

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(contentScroll)
                            .padding(end = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        when (selectedCategory) {
                            "Appearance" -> {
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
                                        dense = true,
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
                                            dense = true,
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
                            "Advanced" -> {
                                SettingToggleRow(
                                    label = "Expert mode",
                                    description = "Full control over patch selection and configuration",
                                    checked = useExpertMode,
                                    onCheckedChange = onExpertModeChange,
                                    accentColor = accents.primary,
                                    font = font,
                                    enabled = !isPatching,
                                    icon = MorpheIcons.Psychology
                                )

                                SettingsDivider(borderColor)

                                SettingToggleRow(
                                    label = "Route links to patched app",
                                    description = "After installing via ADB, make the patched app open its supported web links instead of the browser or the stock/default app",
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
                                            label = "Disable stock app's links",
                                            description = "Also stop the original app from opening these links (only when a " +
                                                "rename patch was used and the stock app is installed). Reversible.",
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
                                    label = "Developer options",
                                    description = "For patch developers. Unlocks a suite of workflow options for building and testing patches (see the documentation for the full list). For now, that's pointing a local source at a folder so Morphe always loads its newest .mpp",
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
                                    label = "Auto-cleanup temp files",
                                    description = "Delete temporary files after patching",
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
                                    label = "Auto-start ADB",
                                    description = "Spawn the ADB daemon on launch so connected devices are monitored. " +
                                        "When off, Morphe never starts the server, and install/push features are disabled",
                                    checked = autoStartAdb,
                                    onCheckedChange = onAutoStartAdbChange,
                                    accentColor = accents.primary,
                                    font = font,
                                    enabled = !isPatching,
                                    icon = MorpheIcons.ADB
                                )

                                SettingsDivider(borderColor)

                                SectionLabel("About", font, icon = MorpheIcons.Info)
                                Spacer(Modifier.height(16.dp))

                                Column(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    AboutRow(
                                        title = "Morphe",
                                        subtitle = "Version ${AppConstants.APP_VERSION}",
                                        font = font,
                                        onClick = { showAppInfoDialog = true },
                                    ) {
                                        Image(
                                            painter = painterResource(Res.drawable.morphe_logo),
                                            contentDescription = "Morphe Logo",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    SettingsDivider(borderColor)

                                    AboutRow(
                                        title = "View changelogs",
                                        subtitle = "Check out the latest changes in this update",
                                        font = font,
                                        onClick = { showChangelogDialog = true },
                                    ) {
                                        Icon(
                                            imageVector = MorpheIcons.Article,
                                            contentDescription = null,
                                            tint = accents.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    SettingsDivider(borderColor)

                                    AboutRow(
                                        title = "Documentation",
                                        subtitle = "Guides for patching, sources and troubleshooting",
                                        font = font,
                                        onClick = { uriHandler.openUri(AppConstants.DOCUMENTATION_URL) },
                                    ) {
                                        Icon(
                                            imageVector = MorpheIcons.MenuBook,
                                            contentDescription = null,
                                            tint = accents.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    SettingsDivider(borderColor)

                                    AboutRow(
                                        title = "Visit website",
                                        subtitle = "Visit the official Morphe website",
                                        font = font,
                                        onClick = { uriHandler.openUri(AppConstants.WEBSITE_URL) },
                                    ) {
                                        Icon(
                                            imageVector = MorpheIcons.Public,
                                            contentDescription = null,
                                            tint = accents.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    VerticalScrollbar(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .offset(x = 8.dp)
                            .fillMaxHeight(),
                        adapter = rememberScrollbarAdapter(contentScroll),
                        style = morpheScrollbarStyle(),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(
                    modifier = Modifier.handCursor(),
                    onClick = onDismiss,
                    shape = RoundedCornerShape(corners.small),
                    border = BorderStroke(1.dp, borderColor)
                ) {
                    Text(
                        "Close",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = font
                    )
                }
            }
        }
    }
}

// ── Shared building blocks ──

@Composable
private fun AccentSwatch(
    selected: Boolean,
    fill: Color,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
    content: @Composable () -> Unit = {},
) {
    val corners = LocalMorpheCorners.current
    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()
    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.08f else 1f,
        label = "accentSwatchScale",
    )
    val ring = when {
        selected -> MaterialTheme.colorScheme.onSurface
        isHovered -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
        else -> Color.Transparent
    }
    val inset by animateDpAsState(
        targetValue = if (selected) 5.dp else 0.dp,
        label = "accentSwatchInset",
    )
    Box(
        modifier = Modifier
            .size(42.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .hoverable(hoverInteraction),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(corners.small))
                .border(2.dp, ring, RoundedCornerShape(corners.small))
                .handCursor()
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(inset)
                    .clip(RoundedCornerShape((corners.small - inset).coerceAtLeast(0.dp)))
                    .background(fill)
            )
            content()
        }

        if (onDelete != null && isHovered) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .handCursor()
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MorpheIcons.Close,
                    contentDescription = "Remove saved color",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(10.dp),
                )
            }
        }
    }
}

@Composable
private fun AboutRow(
    title: String,
    subtitle: String,
    font: FontFamily,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
) {
    val corners = LocalMorpheCorners.current
    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corners.small))
            .background(
                if (isHovered) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                else Color.Transparent
            )
            .hoverable(hoverInteraction)
            .handCursor()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading()
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = font,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = font,
            )
        }
        Icon(
            imageVector = MorpheIcons.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }
}

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
    val rotationAngle by animateFloatAsState(
        targetValue = if (expanded) 270f else 180f,
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
            .handCursor()
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
            imageVector = MorpheIcons.KeyboardArrowLeft,
            contentDescription = if (expanded) "Collapse" else "Expand",
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
 * follow. Mirrors [SettingToggleRow]'s layout, label plus dynamic description
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
    val alpha = if (enabled) 1f else 0.5f

    val description = when {
        !enabled -> "Disabled while patching"
        selected == UpdateChannelPreference.STABLE ->
            "You'll see a banner when a new stable release is available"
        selected == UpdateChannelPreference.DEV ->
            "You'll see a banner when a new dev or stable release is available"
        else -> "Update checks are off. Re-enable here anytime"
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
                    text = "Check for updates",
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
        MorpheDropdown(
            label = selected.name.lowercase().replaceFirstChar { it.uppercase() },
            items = UpdateChannelPreference.entries.map { option ->
                MorpheDropdownItem(
                    label = option.name.lowercase().replaceFirstChar { it.uppercase() },
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
                    text = if (!enabled) "Disabled while patching" else description,
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
        SectionLabel("Output folder", font, icon = icon)
        Spacer(Modifier.height(6.dp))

        Text(
            text = if (!enabled) "Disabled while patching"
                   else "Where patched APKs are saved. A per-app subfolder is created inside",
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
                    text = outputDir?.name ?: "APK's folder (default)",
                    fontSize = 11.sp,
                    fontFamily = font,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            OutlinedButton(
                onClick = {
                    scope.launch {
                        MorpheFilePicker.pickDirectory(
                            title = "Select output folder",
                            startDir = outputDir?.takeIf { it.isDirectory },
                        )?.let { onDefaultOutputDirectoryChange(it.absolutePath) }
                    }
                },
                enabled = enabled,
                shape = RoundedCornerShape(corners.small),
                border = BorderStroke(1.dp, borderColor),
                contentPadding = PaddingValues(horizontal = 10.dp),
                modifier = Modifier.fillMaxHeight().handCursor()
            ) {
                Text(
                    "Browse",
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
                    modifier = Modifier.fillMaxHeight().handCursor()
                ) {
                    Text(
                        "Reset",
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
                text = "Folder not found - will be created on next patch",
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
                    text = "Resolves to: $defaultOutputDirectory",
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
 * Only modern arches are listed. Legacy mips and armeabi are intentionally omitted.
 */
private val STRIP_LIBS_ARCHS = listOf(
    "arm64-v8a" to "ARM 64-bit (most modern phones)",
    "armeabi-v7a" to "ARM 32-bit (older phones)",
    "x86_64" to "Intel 64-bit (emulators / Chromebooks)",
    "x86" to "Intel 32-bit (legacy emulators)"
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
        title = "Strip libs",
        font = font,
        expanded = expanded,
        icon = icon,
        onExpandedChange = onExpandedChange
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Uncheck architectures you don't need. When patching, the output APK will keep only the architectures present in the APK AND in this list. If none overlap, nothing is stripped to avoid broken APKs",
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = font
            )
            STRIP_LIBS_ARCHS.forEach { (arch, description) ->
                val checked = arch in keepArchitectures
                SettingToggleRow(
                    label = arch,
                    description = description,
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
            title = "Signing",
            font = font,
            expanded = expanded,
            icon = icon,
            onExpandedChange = onExpandedChange
        ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = if (!enabled) "Disabled while patching"
                   else "Keystore used to sign patched APKs",
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
                    } else "Default (auto-generated)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = font,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            OutlinedButton(
                onClick = {
                    scope.launch {
                        val selected = MorpheFilePicker.pickFile(
                            title = "Select keystore",
                            extensions = listOf("keystore", "jks", "bks", "p12", "pfx"),
                        ) ?: return@launch
                        val validExtensions = listOf(".keystore", ".jks", ".bks", ".p12", ".pfx")
                        if (validExtensions.any { selected.name.lowercase().endsWith(it) }) {
                            // Route the picked file through KeystoreImporter:
                            // BKS files pass through unchanged. PKCS12 and JKS get
                            // converted to BKS and saved as MorpheData.importedKeystoreFile
                            // (original user file is never mutated). The config
                            // then points at whichever file is BKS. The patcher
                            // only speaks BKS, so this is the only safe input.
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
                                    // Most common failure: wrong password. The
                                    // user's configured entry password didn't
                                    // match the source file. Surface inline so
                                    // they can update it and re-import.
                                    keystoreError = result.reason
                                    result.cause?.let {
                                        Logger.error("Keystore import failed for ${selected.name}", it)
                                    }
                                }
                            }
                        } else {
                            keystoreError = "Invalid file type. Expected: ${validExtensions.joinToString(", ")}"
                        }
                    }
                },
                enabled = enabled,
                shape = RoundedCornerShape(corners.small),
                border = BorderStroke(1.dp, borderColor),
                contentPadding = PaddingValues(horizontal = 10.dp),
                modifier = Modifier.fillMaxHeight().handCursor()
            ) {
                Text(
                    "Browse",
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
                    modifier = Modifier.fillMaxHeight().handCursor()
                ) {
                    Text(
                        "Reset",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = font
                    )
                }
            }
        }

        // Warning if keystore path set but file doesn't exist. Patching will
        // refuse to start with this configured (see PatchingViewModel). The user
        // must restore the file, pick another, or reset to use Morphe's default.
        if (keystorePath != null && !keystoreExists) {
            Text(
                text = "Keystore not found - patching will fail until you restore it, pick another, or reset",
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = font,
                color = Color(0xFFE0A030)
            )
        }

        // Error for invalid file type selection
        keystoreError?.let {
            Text(
                text = it,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = font,
                color = MaterialTheme.colorScheme.error
            )
        }

        // Either: stored form (relative when inside the bundle, absolute otherwise)
        // with a "Resolves to: ..." subtitle when relative. Mirrors config.json
        // so users can see which paths follow the bundle vs which are pinned.
        // Or: "using default" hint when no user-configured path is set.
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
                    text = "Resolves to: $keystorePath",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = font,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            // Mirror the storage form treatment used for user-configured paths above.
            // The default keystore lives in the bundle (`morphe-data/`) in the happy case,
            // so the storable form will be relative.
            // Verb is conditional on file existence. Patcher creates the file on first sign,
            // so on a fresh install the hint accurately says "Will create..."
            // instead of making up claims like "Using..." an absent file.
            val defaultAbs = MorpheData.defaultKeystoreFile.absolutePath
            val defaultStored = PortablePaths.storableForm(defaultAbs)
            val isBundleRelative = defaultStored != defaultAbs
            val verb = if (MorpheData.defaultKeystoreFile.exists()) "Using"
                       else "Will create"
            Text(
                text = "$verb Morphe's default keystore at $defaultStored",
                fontSize = 11.sp,
                fontFamily = font,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (isBundleRelative) {
                Text(
                    text = "Resolves to: $defaultAbs",
                    fontSize = 9.sp,
                    fontFamily = font,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LabeledField(label = "Keystore password", font = font) {
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
                            modifier = Modifier.size(24.dp).handCursor(),
                        ) {
                            Icon(
                                imageVector = if (showPassword) MorpheIcons.VisibilityOff else MorpheIcons.Visibility,
                                contentDescription = if (showPassword) "Hide" else "Show",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                        }
                    },
                )
            }

            LabeledField(label = "Key alias", font = font) {
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

            LabeledField(label = "Key password", font = font) {
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
                            modifier = Modifier.size(24.dp).handCursor(),
                        ) {
                            Icon(
                                imageVector = if (showEntryPassword) MorpheIcons.VisibilityOff else MorpheIcons.Visibility,
                                contentDescription = if (showEntryPassword) "Hide" else "Show",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                        }
                    },
                )
            }
        }

        // Verify credentials button
        var verifyResult by remember { mutableStateOf<String?>(null) }
        var verifySuccess by remember { mutableStateOf(false) }

        if (keystoreExists) {
            Spacer(Modifier.height(6.dp))
            OutlinedButton(
                onClick = {
                    verifyResult = null
                    verifySuccess = false
                    val result = readKeystoreInfo(
                        keystorePath,
                        localPassword.ifEmpty { null },
                        localAlias.ifEmpty { DEFAULT_KEYSTORE_ALIAS },
                        localEntryPassword.ifEmpty { DEFAULT_KEYSTORE_PASSWORD }
                    )
                    if (result == null) {
                        verifyResult = "Could not open keystore — check keystore password"
                        verifySuccess = false
                    } else if (result.warnings.isNotEmpty()) {
                        verifyResult = result.warnings.first()
                        verifySuccess = false
                    } else {
                        verifyResult = "Credentials valid"
                        verifySuccess = true
                    }
                },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().height(dimens.controlHeight).handCursor(),
                shape = RoundedCornerShape(corners.small),
                border = BorderStroke(
                    1.dp,
                    when {
                        verifySuccess -> MorpheColors.Teal.copy(alpha = 0.4f)
                        verifyResult != null -> Color(0xFFE0A030).copy(alpha = 0.4f)
                        else -> borderColor
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
                    "Verify credentials",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = font
                )
            }

            verifyResult?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = it,
                    fontSize = 11.sp,
                    fontFamily = font,
                    fontWeight = FontWeight.Normal,
                    color = if (verifySuccess) MorpheColors.Teal else Color(0xFFE0A030),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Generate button (only when no keystore exists yet)
        var generateError by remember { mutableStateOf<String?>(null) }
        var generateSuccess by remember { mutableStateOf(false) }

        if (!keystoreExists) {
            OutlinedButton(
                onClick = {
                    generateError = null
                    generateSuccess = false
                    scope.launch {
                        // If no path set, ask the user where to save
                        val path = keystorePath ?: run {
                            val chosen = MorpheFilePicker.saveFile(
                                title = "Save keystore",
                                baseName = "morphe",
                                extension = "keystore",
                            ) ?: return@launch // user cancelled
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
                            // Save credentials to config
                            onCredentialsChange(
                                localPassword.ifEmpty { null },
                                localAlias.ifEmpty { DEFAULT_KEYSTORE_ALIAS },
                                localEntryPassword.ifEmpty { DEFAULT_KEYSTORE_PASSWORD }
                            )
                            generateSuccess = true
                        } catch (e: Exception) {
                            generateError = "Failed to generate: ${e.message}"
                            Logger.error("Failed to generate keystore", e)
                        }
                    }
                },
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().height(dimens.controlHeight).handCursor(),
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
                    if (generateSuccess) "Keystore generated" else "Generate keystore",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    color = if (generateSuccess) MorpheColors.Teal else accentColor,
                    fontFamily = font
                )
            }

            generateError?.let {
                Text(
                    text = it,
                    fontSize = 11.sp,
                    fontFamily = font,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (!generateSuccess) {
                Text(
                    text = "Uses the credentials entered above",
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
                modifier = Modifier.weight(1f).handCursor()
            ) {
                Icon(
                    imageVector = MorpheIcons.Info,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Certificate",
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
                            title = "Export keystore",
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
                modifier = Modifier.weight(1f).handCursor()
            ) {
                Icon(
                    imageVector = MorpheIcons.Share,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Export",
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

    MorpheAlertDialog(
        onDismiss = onDismiss,
        title = {
            Text(
                "Certificate info",
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
                            Text(
                                text = warning,
                                fontSize = 11.sp,
                                fontFamily = font,
                                fontWeight = FontWeight.Normal,
                                color = Color(0xFFE0A030),
                                lineHeight = 14.sp
                            )
                        }
                        // If no cert data (alias not found), stop here
                        if (info.sha256Fingerprint.isEmpty()) return@Column
                        HorizontalDivider(color = borderColor)
                    }

                    CertInfoRow("Alias", info.alias, font)
                    CertInfoRow("Issuer", info.issuer, font)
                    CertInfoRow("Valid from", info.validFrom, font)
                    CertInfoRow("Valid until", info.validTo, font)

                    HorizontalDivider(color = borderColor)

                    Text(
                        "SHA-256 fingerprint",
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
                        "SHA-1 fingerprint",
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
                    text = "Could not read keystore. Check the password and alias",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = font,
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        confirmButton = {
            OutlinedButton(
                modifier = Modifier.handCursor(),
                onClick = onDismiss,
                shape = RoundedCornerShape(corners.small),
                border = BorderStroke(1.dp, borderColor)
            ) {
                Text(
                    "Close",
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

private data class KeystoreInfoResult(
    val alias: String,
    val issuer: String,
    val validFrom: String,
    val validTo: String,
    val sha256Fingerprint: String,
    val sha1Fingerprint: String,
    val warnings: List<String> = emptyList()
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
    val dateFormat = SimpleDateFormat("yyyy-MM-dd")

    // Ensure BouncyCastle provider is registered (needed for BKS keystores)
    try {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(
                Class.forName("org.bouncycastle.jce.provider.BouncyCastleProvider")
                    .getDeclaredConstructor().newInstance() as Provider
            )
        }
    } catch (_: Exception) {
    }

    // Try multiple keystore types: BKS (what Morphe generates), then JKS, then PKCS12
    // BKS requires the BouncyCastle provider. Try with the name, fall back without
    val types = listOf("BKS" to "BC", "BKS" to null, "JKS" to null, "PKCS12" to null)
    for ((type, provider) in types) {
        try {
            val ks = if (provider != null) {
                KeyStore.getInstance(type, provider)
            } else {
                KeyStore.getInstance(type)
            }

            file.inputStream().use { ks.load(it, passwordChars) }

            val warnings = mutableListOf<String>()

            // Alias must match exactly
            if (!ks.containsAlias(alias)) {
                return KeystoreInfoResult(
                    alias = alias,
                    issuer = "",
                    validFrom = "",
                    validTo = "",
                    sha256Fingerprint = "",
                    sha1Fingerprint = "",
                    warnings = listOf("Alias \"$alias\" not found in keystore")
                )
            }

            val cert = ks.getCertificate(alias) as? X509Certificate ?: continue

            // Verify the entry password actually works
            try {
                ks.getKey(alias, entryPassword?.toCharArray() ?: charArrayOf())
            } catch (_: Exception) {
                return KeystoreInfoResult(
                    alias = alias,
                    issuer = "",
                    validFrom = "",
                    validTo = "",
                    sha256Fingerprint = "",
                    sha1Fingerprint = "",
                    warnings = listOf("Key password is incorrect for alias \"$alias\"")
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
                validFrom = dateFormat.format(cert.notBefore),
                validTo = dateFormat.format(cert.notAfter),
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

    CollapsibleSection(
        title = "Patched app runtime logs",
        font = font,
        expanded = expanded,
        icon = icon,
        onExpandedChange = onExpandedChange
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Capture logs from your phone after a patched app crashes or misbehaves. Clear before reproducing the bug, then save the filtered output to attach to a bug report",
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = font
            )

            // Device row
            if (deviceReady) {
                Text(
                    text = "Device: ${selectedDevice.displayName}${selectedDevice.architecture?.let { " ($it)" } ?: ""}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = font,
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else {
                Text(
                    text = "No device connected. Plug in your phone with USB debugging enabled",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = font,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            ActionButton(
                label = if (status is RuntimeLogsStatus.Clearing) "Clearing…" else "Clear device logs",
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
                            onFailure = { RuntimeLogsStatus.Error(it.message ?: "Failed to clear logs") }
                        )
                    }
                }
            )

            ActionButton(
                label = if (status is RuntimeLogsStatus.Saving) "Saving…" else "Save device logs",
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
                            onFailure = { RuntimeLogsStatus.Error(it.message ?: "Failed to save logs") }
                        )
                    }
                }
            )

            // Status line
            when (val s = status) {
                RuntimeLogsStatus.Idle, RuntimeLogsStatus.Clearing, RuntimeLogsStatus.Saving -> Unit
                RuntimeLogsStatus.Cleared -> Text(
                    text = "Logs cleared on device",
                    fontSize = 11.sp,
                    fontFamily = font,
                    fontWeight = FontWeight.Normal,
                    color = accentColor
                )
                is RuntimeLogsStatus.Saved -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (s.lineCount == 0)
                            "Nothing captured yet. Run the patched app on your phone, then save again"
                        else
                            "Saved ${s.lineCount} line(s) to ${s.file.name}",
                        fontSize = 11.sp,
                        fontFamily = font,
                        fontWeight = FontWeight.Normal,
                        color = if (s.lineCount == 0) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                else accentColor
                    )
                    if (s.lineCount > 0) {
                        val cornersLocal = LocalMorpheCorners.current
                        Text(
                            text = "Open logs",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = font,
                            color = accentColor,
                            modifier = Modifier
                                .clip(RoundedCornerShape(cornersLocal.small))
                                .handCursor()
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
