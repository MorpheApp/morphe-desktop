/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.morphe.engine.PatchEngine.Config.Companion.DEFAULT_KEYSTORE_ALIAS
import app.morphe.engine.PatchEngine.Config.Companion.DEFAULT_KEYSTORE_PASSWORD
import app.morphe.gui.data.model.UpdateChannelPreference
import app.morphe.gui.ui.components.settings.AdvancedTab
import app.morphe.gui.ui.components.settings.AppearanceTab
import app.morphe.gui.ui.components.settings.SystemTab
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.ui.theme.ThemePreference

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
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
    var selectedCategory by remember { mutableStateOf("Appearance") }
    val contentScroll = remember(selectedCategory) { ScrollState(0) }
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
                            "Appearance" -> AppearanceTab(
                                currentTheme = currentTheme,
                                onThemeChange = onThemeChange,
                                customAccentColorArgb = customAccentColorArgb,
                                onCustomAccentColorChange = onCustomAccentColorChange,
                                borderColor = borderColor,
                            )
                            "Advanced" -> AdvancedTab(
                                useExpertMode = useExpertMode,
                                onExpertModeChange = onExpertModeChange,
                                autoRouteLinksAfterInstall = autoRouteLinksAfterInstall,
                                onAutoRouteLinksChange = onAutoRouteLinksChange,
                                disableStockLinksAfterInstall = disableStockLinksAfterInstall,
                                onDisableStockLinksChange = onDisableStockLinksChange,
                                keystorePath = keystorePath,
                                keystorePassword = keystorePassword,
                                keystoreAlias = keystoreAlias,
                                keystoreEntryPassword = keystoreEntryPassword,
                                onKeystorePathChange = onKeystorePathChange,
                                onKeystoreCredentialsChange = onKeystoreCredentialsChange,
                                keepArchitectures = keepArchitectures,
                                onKeepArchitecturesChange = onKeepArchitecturesChange,
                                developerOptions = developerOptions,
                                onDeveloperOptionsChange = onDeveloperOptionsChange,
                                collapsibleSectionStates = collapsibleSectionStates,
                                onCollapsibleSectionToggle = onCollapsibleSectionToggle,
                                isPatching = isPatching,
                                borderColor = borderColor,
                            )
                            "System" -> SystemTab(
                                autoCleanupTempFiles = autoCleanupTempFiles,
                                onAutoCleanupChange = onAutoCleanupChange,
                                updateChannelPreference = updateChannelPreference,
                                onUpdateChannelChange = onUpdateChannelChange,
                                defaultOutputDirectory = defaultOutputDirectory,
                                onDefaultOutputDirectoryChange = onDefaultOutputDirectoryChange,
                                autoStartAdb = autoStartAdb,
                                onAutoStartAdbChange = onAutoStartAdbChange,
                                isPatching = isPatching,
                                borderColor = borderColor,
                                onShowAppInfo = { showAppInfoDialog = true },
                                onShowChangelog = { showChangelogDialog = true },
                            )
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
