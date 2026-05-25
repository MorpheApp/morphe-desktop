/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.gui.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.engine.MorpheData
import app.morphe.engine.PatchEngine.Config.Companion.DEFAULT_KEYSTORE_ALIAS
import app.morphe.engine.PatchEngine.Config.Companion.DEFAULT_KEYSTORE_PASSWORD
import app.morphe.gui.data.constants.AppConstants
import app.morphe.gui.data.model.PatchSource
import app.morphe.gui.data.model.PatchSourceType
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheDimens
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.MorpheColors
import app.morphe.gui.ui.theme.ThemePreference
import app.morphe.gui.util.AdbManager
import app.morphe.gui.util.DeviceMonitor
import app.morphe.gui.util.FileUtils
import app.morphe.gui.util.Logger
import kotlinx.coroutines.launch
import app.morphe.patcher.apk.ApkSigner
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.UUID
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.entity.License
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.morphe.gui.ui.theme.MorpheAccentColors
import app.morphe.gui.ui.theme.MorpheCornerStyle
import java.net.URI

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
    allowCacheClear: Boolean = true,
    isPatching: Boolean = false,
    onCacheCleared: () -> Unit = {},
    keystorePath: String? = null,
    keystorePassword: String? = null,
    keystoreAlias: String = DEFAULT_KEYSTORE_ALIAS,
    keystoreEntryPassword: String = DEFAULT_KEYSTORE_PASSWORD,
    onKeystorePathChange: (String?) -> Unit = {},
    onKeystoreCredentialsChange: (password: String?, alias: String, entryPassword: String) -> Unit = { _, _, _ -> },
    keepArchitectures: Set<String> = emptySet(),
    onKeepArchitecturesChange: (Set<String>) -> Unit = {},
    updateChannelPreference: app.morphe.gui.data.model.UpdateChannelPreference = app.morphe.gui.data.model.UpdateChannelPreference.STABLE,
    onUpdateChannelChange: (app.morphe.gui.data.model.UpdateChannelPreference) -> Unit = {},
    autoStartAdb: Boolean = false,
    onAutoStartAdbChange: (Boolean) -> Unit = {},
    collapsibleSectionStates: Map<String, Boolean> = emptyMap(),
    onCollapsibleSectionToggle: (id: String, expanded: Boolean) -> Unit = { _, _ -> }
) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)

    var showClearCacheConfirm by remember { mutableStateOf(false) }
    var showLicensesDialog by remember { mutableStateOf(false) }
    var cacheCleared by remember { mutableStateOf(false) }
    var cacheClearFailed by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(corners.medium),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = "SETTINGS",
                fontWeight = FontWeight.Bold,
                fontFamily = mono,
                fontSize = 13.sp,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .widthIn(min = 340.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // ── Theme ──
                SectionLabel("THEME", mono)
                Spacer(Modifier.height(8.dp))
                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ThemePreference.entries.filter { it != ThemePreference.MATCHA }.forEach { theme ->
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
                                        isSelected -> themeAccent.copy(alpha = 0.5f)
                                        isHovered -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                        else -> borderColor
                                    },
                                    RoundedCornerShape(corners.small)
                                )
                                .background(
                                    if (isSelected) themeAccent.copy(alpha = 0.08f)
                                    else Color.Transparent
                                )
                                .hoverable(hoverInteraction)
                                .clickable { onThemeChange(theme) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Themed icon
                            Text(
                                text = theme.iconSymbol(),
                                fontSize = 11.sp,
                                color = themeAccent
                            )
                            Text(
                                text = theme.toDisplayName().uppercase(),
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontFamily = mono,
                                letterSpacing = 0.5.sp,
                                color = if (isSelected) themeAccent
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                SettingsDivider(borderColor)

                // ── Expert Mode ──
                SettingToggleRow(
                    label = "Expert mode",
                    description = "Full control over patch selection and configuration",
                    checked = useExpertMode,
                    onCheckedChange = onExpertModeChange,
                    accentColor = accents.primary,
                    mono = mono,
                    enabled = !isPatching
                )

                SettingsDivider(borderColor)

                // ── Auto Cleanup ──
                SettingToggleRow(
                    label = "Auto-cleanup temp files",
                    description = "Delete temporary files after patching",
                    checked = autoCleanupTempFiles,
                    onCheckedChange = onAutoCleanupChange,
                    accentColor = accents.primary,
                    mono = mono,
                    enabled = !isPatching
                )

                SettingsDivider(borderColor)

                // ── Update Channel ──
                UpdateChannelRow(
                    selected = updateChannelPreference,
                    onChange = onUpdateChannelChange,
                    accentColor = accents.primary,
                    mono = mono,
                    borderColor = borderColor,
                    enabled = !isPatching,
                )

                SettingsDivider(borderColor)

                // ── Output Folder ──
                OutputFolderSection(
                    defaultOutputDirectory = defaultOutputDirectory,
                    onDefaultOutputDirectoryChange = onDefaultOutputDirectoryChange,
                    mono = mono,
                    borderColor = borderColor,
                    enabled = !isPatching
                )

                SettingsDivider(borderColor)

                // ── Signing / Keystore ──
                SigningSection(
                    keystorePath = keystorePath,
                    keystorePassword = keystorePassword,
                    keystoreAlias = keystoreAlias,
                    keystoreEntryPassword = keystoreEntryPassword,
                    onKeystorePathChange = onKeystorePathChange,
                    onCredentialsChange = onKeystoreCredentialsChange,
                    mono = mono,
                    accentColor = accents.primary,
                    borderColor = borderColor,
                    enabled = !isPatching,
                    expanded = collapsibleSectionStates["SIGNING"] == true,
                    onExpandedChange = { onCollapsibleSectionToggle("SIGNING", it) }
                )

                SettingsDivider(borderColor)

                // ── Strip Libs ──
                StripLibsSection(
                    keepArchitectures = keepArchitectures,
                    onChange = onKeepArchitecturesChange,
                    mono = mono,
                    accentColor = accents.primary,
                    enabled = !isPatching,
                    expanded = collapsibleSectionStates["STRIP LIBS"] == true,
                    onExpandedChange = { onCollapsibleSectionToggle("STRIP LIBS", it) }
                )

                SettingsDivider(borderColor)

                // ── Auto-start ADB ──
                SettingToggleRow(
                    label = "Auto-start ADB",
                    description = "Spawn the ADB daemon on launch so connected devices are monitored. " +
                        "When off, Morphe never starts the server, and install/push features are disabled.",
                    checked = autoStartAdb,
                    onCheckedChange = onAutoStartAdbChange,
                    accentColor = accents.primary,
                    mono = mono,
                    enabled = !isPatching
                )

                SettingsDivider(borderColor)

                // ── Patched App Runtime Logs ──
                PatchedAppRuntimeLogsSection(
                    mono = mono,
                    accentColor = accents.primary,
                    borderColor = borderColor,
                    enabled = !isPatching,
                    expanded = collapsibleSectionStates["RUNTIME LOGS"] == true,
                    onExpandedChange = { onCollapsibleSectionToggle("RUNTIME LOGS", it) }
                )

                SettingsDivider(borderColor)

                // ── Actions ──
                SectionLabel("ACTIONS", mono)
                Spacer(Modifier.height(8.dp))

                ActionButton(
                    label = "OPEN LOGS",
                    icon = Icons.Default.BugReport,
                    mono = mono,
                    borderColor = borderColor,
                    onClick = {
                        try {
                            val logsDir = FileUtils.getLogsDir()
                            if (Desktop.isDesktopSupported()) {
                                Desktop.getDesktop().open(logsDir)
                            }
                        } catch (e: Exception) {
                            Logger.error("Failed to open logs folder", e)
                        }
                    }
                )

                Spacer(Modifier.height(6.dp))

                ActionButton(
                    label = "OPEN APP DATA",
                    icon = Icons.Default.FolderOpen,
                    mono = mono,
                    borderColor = borderColor,
                    onClick = {
                        try {
                            val appDataDir = FileUtils.getAppDataDir()
                            if (Desktop.isDesktopSupported()) {
                                Desktop.getDesktop().open(appDataDir)
                            }
                        } catch (e: Exception) {
                            Logger.error("Failed to open app data folder", e)
                        }
                    }
                )

                Spacer(Modifier.height(6.dp))

                ActionButton(
                    label = "VIEW LICENSES",
                    icon = Icons.Default.Description,
                    mono = mono,
                    borderColor = borderColor,
                    onClick = { showLicensesDialog = true }
                )

                Spacer(Modifier.height(6.dp))

                // Clear cache
                val cacheColor = when {
                    cacheCleared -> MorpheColors.Teal
                    cacheClearFailed -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.error
                }
                ActionButton(
                    label = when {
                        !allowCacheClear -> "CLEAR CACHE (DISABLED)"
                        cacheCleared -> "CACHE CLEARED"
                        cacheClearFailed -> "CLEAR FAILED"
                        else -> "CLEAR CACHE"
                    },
                    icon = Icons.Default.Delete,
                    mono = mono,
                    borderColor = if (cacheCleared) MorpheColors.Teal.copy(alpha = 0.3f)
                                  else MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                    contentColor = cacheColor,
                    enabled = allowCacheClear && !cacheCleared,
                    onClick = { showClearCacheConfirm = true }
                )

                Spacer(Modifier.height(4.dp))

                val cacheSize = calculateCacheSize()
                Text(
                    text = "Cache: $cacheSize (patches + logs)",
                    fontSize = 10.sp,
                    fontFamily = mono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )

                SettingsDivider(borderColor)

                // ── About ──
                Text(
                    text = "${AppConstants.APP_NAME} ${AppConstants.APP_VERSION}",
                    fontSize = 10.sp,
                    fontFamily = mono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
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
                    "CLOSE",
                    fontFamily = mono,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )

    // Clear cache confirmation
    if (showClearCacheConfirm) {
        AlertDialog(
            onDismissRequest = { showClearCacheConfirm = false },
            shape = RoundedCornerShape(corners.medium),
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    "CLEAR CACHE?",
                    fontFamily = mono,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 1.sp
                )
            },
            text = {
                Text(
                    "This will delete downloaded patches and log files. Patches will be re-downloaded when needed.",
                    fontFamily = mono,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val success = clearAllCache()
                        cacheCleared = success
                        cacheClearFailed = !success
                        showClearCacheConfirm = false
                        if (success) onCacheCleared()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(corners.small)
                ) {
                    Text(
                        "CLEAR",
                        fontFamily = mono,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheConfirm = false }) {
                    Text(
                        "CANCEL",
                        fontFamily = mono,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        )
    }

    if (showLicensesDialog) {
        LicensesDialog(onDismiss = { showLicensesDialog = false })
    }
}

@Composable
private fun LicensesDialog(onDismiss: () -> Unit) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
    val dividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)

    val libs = remember {
        try {
            val stream = Thread.currentThread().contextClassLoader.getResourceAsStream("aboutlibraries.json")
            val json = stream?.bufferedReader()?.use { it.readText() }
            if (json != null) Libs.Builder().withJson(json).build() else null
        } catch (e: Exception) {
            Logger.error("Failed to load licenses", e)
            null
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var viewingLicense by remember { mutableStateOf<License?>(null) }
    val listState = rememberLazyListState()

    val filtered = remember(libs, searchQuery) {
        val all = libs?.libraries.orEmpty()
        if (searchQuery.isBlank()) all
        else {
            val q = searchQuery.trim().lowercase()
            all.filter { lib ->
                lib.name.lowercase().contains(q) ||
                    lib.uniqueId.lowercase().contains(q) ||
                    (lib.description?.lowercase()?.contains(q) == true) ||
                    lib.licenses.any { it.name.lowercase().contains(q) || (it.spdxId?.lowercase()?.contains(q) == true) }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(min = 640.dp, max = 960.dp)
                .heightIn(min = 520.dp, max = 780.dp)
                .fillMaxWidth(0.88f)
                .fillMaxHeight(0.88f),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(corners.medium),
            border = BorderStroke(1.dp, borderColor)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ── Header ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "OPEN SOURCE LICENSES",
                            fontFamily = mono,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            letterSpacing = 1.8.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "[${libs?.libraries?.size ?: 0}]",
                            fontFamily = mono,
                            fontSize = 11.sp,
                            color = accents.primary,
                            letterSpacing = 0.5.sp
                        )
                    }
                    val closeHover = remember { MutableInteractionSource() }
                    val isCloseHovered by closeHover.collectIsHoveredAsState()
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(corners.small))
                            .hoverable(closeHover)
                            .background(
                                if (isCloseHovered) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                                else Color.Transparent
                            )
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = if (isCloseHovered) 0.85f else 0.55f
                            ),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                HorizontalDivider(color = dividerColor)

                // ── Search bar ──
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp)) {
                    LicenseSearchBar(query = searchQuery, onQueryChange = { searchQuery = it })
                }

                HorizontalDivider(color = dividerColor)

                // ── List ──
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    when {
                        libs == null -> EmptyHint(text = "// failed to load licenses", mono = mono, isError = true)
                        filtered.isEmpty() -> EmptyHint(text = "// no matches", mono = mono, isError = false)
                        else -> {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp)
                            ) {
                                items(items = filtered, key = { it.uniqueId }) { library ->
                                    LibraryRow(
                                        library = library,
                                        mono = mono,
                                        accents = accents,
                                        corners = corners,
                                        borderColor = borderColor,
                                        dividerColor = dividerColor,
                                        onLicenseClick = { viewingLicense = it }
                                    )
                                }
                            }

                            VerticalScrollbar(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .fillMaxHeight()
                                    .padding(vertical = 6.dp),
                                adapter = rememberScrollbarAdapter(listState),
                                style = morpheScrollbarStyle()
                            )
                        }
                    }
                }

                HorizontalDivider(color = dividerColor)

                // ── Footer ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (searchQuery.isBlank()) "${filtered.size} libraries"
                               else "${filtered.size} / ${libs?.libraries?.size ?: 0} matched",
                        fontFamily = mono,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        letterSpacing = 0.8.sp
                    )
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(corners.small),
                        border = BorderStroke(1.dp, borderColor)
                    ) {
                        Text(
                            "CLOSE",
                            fontFamily = mono,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    viewingLicense?.let { license ->
        LicenseTextDialog(license = license, onDismiss = { viewingLicense = null })
    }
}

@Composable
private fun LicenseSearchBar(query: String, onQueryChange: (String) -> Unit) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val searchFocused = remember { mutableStateOf(false) }
    val searchBorderColor by animateColorAsState(
        if (searchFocused.value) accents.primary.copy(alpha = 0.5f)
        else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
        animationSpec = tween(150)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(corners.small))
            .border(1.dp, searchBorderColor, RoundedCornerShape(corners.small))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Search",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(16.dp)
        )

        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = "Search libraries, SPDX id, uniqueId…",
                    fontSize = 11.sp,
                    fontFamily = mono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 12.sp,
                    fontFamily = mono,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(accents.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { searchFocused.value = it.isFocused }
            )
        }

        if (query.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(corners.small))
                    .clickable { onQueryChange("") },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "Clear",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun LibraryRow(
    library: Library,
    mono: androidx.compose.ui.text.font.FontFamily,
    accents: MorpheAccentColors,
    corners: MorpheCornerStyle,
    borderColor: Color,
    dividerColor: Color,
    onLicenseClick: (License) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(180)
    )
    val bgAlpha by animateFloatAsState(
        targetValue = when {
            expanded -> 0.05f
            isHovered -> 0.03f
            else -> 0f
        },
        animationSpec = tween(180)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corners.small))
            .hoverable(hoverInteraction)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = bgAlpha))
            .clickable { expanded = !expanded }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = library.name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = mono,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    library.artifactVersion?.takeIf { it.isNotBlank() }?.let { v ->
                        Text(
                            text = "v$v",
                            fontSize = 10.sp,
                            fontFamily = mono,
                            color = accents.secondary.copy(alpha = 0.9f),
                            letterSpacing = 0.3.sp
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = library.uniqueId,
                    fontSize = 10.sp,
                    fontFamily = mono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (library.licenses.isEmpty()) {
                    LicenseChip(
                        label = "UNKNOWN",
                        mono = mono,
                        corners = corners,
                        accentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        onClick = null
                    )
                } else {
                    library.licenses.forEach { license ->
                        LicenseChip(
                            label = licenseDisplayLabel(license),
                            mono = mono,
                            corners = corners,
                            accentColor = accents.primary,
                            onClick = { onLicenseClick(license) }
                        )
                    }
                }
            }

            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isHovered) 0.7f else 0.4f),
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer { rotationZ = rotation }
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(expandFrom = Alignment.Top, animationSpec = tween(200)) +
                fadeIn(animationSpec = tween(200)),
            exit = shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = tween(180)) +
                fadeOut(animationSpec = tween(140))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = 14.dp, top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                library.description?.trim()?.takeIf { it.isNotEmpty() }?.let { desc ->
                    Text(
                        text = desc,
                        fontSize = 12.sp,
                        fontFamily = mono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        lineHeight = 17.sp
                    )
                }

                val devs = library.developers.mapNotNull { it.name?.takeIf { n -> n.isNotBlank() } }
                val org = library.organization?.name?.takeIf { it.isNotBlank() }
                if (devs.isNotEmpty() || org != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (devs.isNotEmpty()) {
                            MetaLine(label = "AUTHORS", value = devs.joinToString(", "), mono = mono)
                        }
                        org?.let { MetaLine(label = "ORG", value = it, mono = mono) }
                    }
                }

                val website = library.website?.takeIf { it.isNotBlank() }
                val source = library.scm?.url?.takeIf { it.isNotBlank() }
                if (website != null || source != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        website?.let {
                            LinkPill(label = "WEBSITE", url = it, mono = mono, corners = corners, borderColor = borderColor)
                        }
                        source?.let {
                            LinkPill(label = "SOURCE", url = it, mono = mono, corners = corners, borderColor = borderColor)
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = dividerColor)
    }
}

@Composable
private fun MetaLine(
    label: String,
    value: String,
    mono: androidx.compose.ui.text.font.FontFamily,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = mono,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            letterSpacing = 1.sp,
            modifier = Modifier.width(56.dp)
        )
        Text(
            text = value,
            fontSize = 11.sp,
            fontFamily = mono,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LicenseChip(
    label: String,
    mono: androidx.compose.ui.text.font.FontFamily,
    corners: MorpheCornerStyle,
    accentColor: Color,
    onClick: (() -> Unit)?,
) {
    val hover = remember { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()
    val bg by animateColorAsState(
        if (isHovered && onClick != null) accentColor.copy(alpha = 0.18f)
        else accentColor.copy(alpha = 0.08f),
        animationSpec = tween(140)
    )
    Box(
        modifier = Modifier
            .hoverable(hover)
            .clip(RoundedCornerShape(corners.small))
            .background(bg, RoundedCornerShape(corners.small))
            .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(corners.small))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = mono,
            color = accentColor,
            letterSpacing = 0.8.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun LinkPill(
    label: String,
    url: String,
    mono: androidx.compose.ui.text.font.FontFamily,
    corners: MorpheCornerStyle,
    borderColor: Color,
) {
    val hover = remember { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()
    Row(
        modifier = Modifier
            .hoverable(hover)
            .clip(RoundedCornerShape(corners.small))
            .border(
                1.dp,
                if (isHovered) borderColor.copy(alpha = 0.4f) else borderColor,
                RoundedCornerShape(corners.small)
            )
            .clickable { openUrl(url) }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = mono,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isHovered) 0.9f else 0.6f),
            letterSpacing = 1.sp
        )
        @Suppress("DEPRECATION")
        Icon(
            imageVector = Icons.Default.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isHovered) 0.75f else 0.45f),
            modifier = Modifier.size(10.dp)
        )
    }
}

@Composable
private fun EmptyHint(text: String, mono: androidx.compose.ui.text.font.FontFamily, isError: Boolean) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            fontFamily = mono,
            fontSize = 12.sp,
            color = if (isError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            letterSpacing = 0.8.sp
        )
    }
}

@Composable
private fun LicenseTextDialog(license: License, onDismiss: () -> Unit) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
    val content = license.licenseContent?.takeIf { it.isNotBlank() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .widthIn(min = 540.dp, max = 820.dp)
                .heightIn(min = 380.dp, max = 680.dp)
                .fillMaxWidth(0.78f)
                .fillMaxHeight(0.82f),
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(corners.medium),
            border = BorderStroke(1.dp, borderColor)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        val chipLabel = licenseDisplayLabel(license)
                        Text(
                            text = chipLabel.uppercase(),
                            fontFamily = mono,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            letterSpacing = 1.5.sp,
                            color = accents.primary
                        )
                        if (license.name.isNotBlank() && !license.name.equals(chipLabel, ignoreCase = true)) {
                            Text(
                                text = license.name,
                                fontFamily = mono,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(corners.small))
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                HorizontalDivider(color = borderColor)

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (content != null) {
                        val scrollState = rememberScrollState()
                        Text(
                            text = content,
                            fontSize = 11.sp,
                            fontFamily = mono,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(scrollState)
                                .padding(horizontal = 22.dp, vertical = 16.dp)
                        )
                        VerticalScrollbar(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .padding(vertical = 6.dp),
                            adapter = rememberScrollbarAdapter(scrollState),
                            style = morpheScrollbarStyle()
                        )
                    } else {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(22.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "// full license text not bundled",
                                fontFamily = mono,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                letterSpacing = 0.5.sp
                            )
                            license.url?.takeIf { it.isNotBlank() }?.let { url ->
                                Text(
                                    text = "Open the canonical license text:",
                                    fontFamily = mono,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                                LinkPill(
                                    label = "OPEN LICENSE",
                                    url = url,
                                    mono = mono,
                                    corners = corners,
                                    borderColor = borderColor
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = borderColor)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 22.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(corners.small),
                        border = BorderStroke(1.dp, borderColor)
                    ) {
                        Text(
                            "CLOSE",
                            fontFamily = mono,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private val MD5_HASH_REGEX = Regex("^[0-9a-f]{32}$")

private fun licenseDisplayLabel(license: License): String {
    license.spdxId?.takeIf { it.isNotBlank() }?.let { return it }
    val hash = license.hash
    if (hash.isNotBlank() && !MD5_HASH_REGEX.matches(hash)) return hash
    return license.name.ifBlank { "—" }
}

private fun openUrl(url: String) {
    try {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI.create(url))
        }
    } catch (e: Exception) {
        Logger.error("Failed to open url: $url", e)
    }
}

// ── Shared building blocks ──

@Composable
private fun SectionLabel(
    text: String,
    mono: androidx.compose.ui.text.font.FontFamily
) {
    Text(
        text = text,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = mono,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        letterSpacing = 1.5.sp
    )
}

@Composable
private fun CollapsibleSection(
    title: String,
    mono: androidx.compose.ui.text.font.FontFamily,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    val corners = LocalMorpheCorners.current
    val rotationAngle by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (expanded) -90f else 0f,
        animationSpec = androidx.compose.animation.core.tween(200)
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
        Text(
            text = title,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = mono,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isHovered) 0.6f else 0.4f),
            letterSpacing = 1.5.sp
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = if (expanded) "Collapse" else "Expand",
            modifier = Modifier
                .size(16.dp)
                .graphicsLayer { rotationZ = rotationAngle },
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isHovered) 0.5f else 0.3f)
        )
    }

    androidx.compose.animation.AnimatedVisibility(
        visible = expanded,
        enter = androidx.compose.animation.expandVertically(
            expandFrom = Alignment.Top,
            animationSpec = androidx.compose.animation.core.tween(200)
        ) + androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(200)),
        exit = androidx.compose.animation.shrinkVertically(
            shrinkTowards = Alignment.Top,
            animationSpec = androidx.compose.animation.core.tween(200)
        ) + androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(150))
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
    selected: app.morphe.gui.data.model.UpdateChannelPreference,
    onChange: (app.morphe.gui.data.model.UpdateChannelPreference) -> Unit,
    accentColor: Color,
    mono: androidx.compose.ui.text.font.FontFamily,
    borderColor: Color,
    enabled: Boolean,
) {
    val corners = LocalMorpheCorners.current
    val alpha = if (enabled) 1f else 0.4f

    val description = when {
        !enabled -> "Disabled while patching"
        selected == app.morphe.gui.data.model.UpdateChannelPreference.STABLE ->
            "You'll see a banner when a new stable release is available"
        selected == app.morphe.gui.data.model.UpdateChannelPreference.DEV ->
            "You'll see a banner when a new dev or stable release is available"
        else -> "Update checks are off. Re-enable here anytime"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Check for updates",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 11.sp,
                fontFamily = mono,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f * alpha),
            )
        }
        Spacer(Modifier.width(12.dp))
        UpdateChannelSegmentedToggle(
            selected = selected,
            onChange = onChange,
            accentColor = accentColor,
            mono = mono,
            enabled = enabled,
        )
    }
}

/**
 * Three-segment switch styled to match [MorpheSwitch]'s sharp variant — single
 * rectangular border, a sliding accent block beneath the active label, mono
 * labels that flip between muted and on-accent. Soft themes get the same shape
 * with rounded corners (matching how [MorpheSwitch] rounds in soft themes).
 */
@Composable
private fun UpdateChannelSegmentedToggle(
    selected: app.morphe.gui.data.model.UpdateChannelPreference,
    onChange: (app.morphe.gui.data.model.UpdateChannelPreference) -> Unit,
    accentColor: Color,
    mono: androidx.compose.ui.text.font.FontFamily,
    enabled: Boolean,
) {
    val isSoft = LocalMorpheCorners.current.medium >= 10.dp
    val height = if (isSoft) 26.dp else 24.dp
    // Segments sized so the longest label (STABLE) gets ~10dp horizontal
    // breathing room on each side, matching the visual padding the OFF/ON
    // labels have inside MorpheSwitch's 28dp halves.
    val segWidth = 48.dp
    val totalWidth = segWidth * 3
    val pillShape = if (isSoft) RoundedCornerShape(height / 2) else RoundedCornerShape(0.dp)

    val entries = app.morphe.gui.data.model.UpdateChannelPreference.entries
    val activeIndex = entries.indexOf(selected)

    val blockOffset by androidx.compose.animation.core.animateDpAsState(
        targetValue = segWidth * activeIndex,
        animationSpec = androidx.compose.animation.core.tween(180),
    )
    val borderColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (enabled) accentColor.copy(alpha = 0.45f)
                      else MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
        animationSpec = androidx.compose.animation.core.tween(180),
    )

    val onBlockLabel = MaterialTheme.colorScheme.onPrimary
    val mutedLabel = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val disabledAlpha = if (enabled) 1f else 0.4f

    Box(
        modifier = Modifier
            .size(totalWidth, height)
            .clip(pillShape)
            .border(1.dp, borderColor, pillShape)
            .graphicsLayer { this.alpha = disabledAlpha },
    ) {
        // Sliding accent block — sits behind the labels and animates between
        // segments when the user changes selection.
        Box(
            modifier = Modifier
                .offset(x = blockOffset)
                .size(segWidth, height)
                .background(accentColor),
        )
        // Labels — each tappable, color flips depending on whether it sits
        // over the accent block.
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            entries.forEachIndexed { index, pref ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(enabled = enabled) { onChange(pref) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = pref.name,
                        fontFamily = mono,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.4.sp,
                        color = if (index == activeIndex) onBlockLabel else mutedLabel,
                    )
                }
            }
        }
    }
}


@Composable
private fun SettingToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accentColor: Color,
    mono: androidx.compose.ui.text.font.FontFamily,
    enabled: Boolean = true
) {
    val alpha = if (enabled) 1f else 0.4f
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (!enabled) "Disabled while patching" else description,
                fontSize = 11.sp,
                fontFamily = mono,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f * alpha)
            )
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
    mono: androidx.compose.ui.text.font.FontFamily,
    borderColor: Color,
    enabled: Boolean = true
) {
    val corners = LocalMorpheCorners.current
    val dimens = LocalMorpheDimens.current
    val alpha = if (enabled) 1f else 0.4f
    val outputDir = defaultOutputDirectory?.let { File(it) }
    val outputDirExists = outputDir?.isDirectory == true

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionLabel("OUTPUT FOLDER", mono)
        Spacer(Modifier.height(6.dp))

        Text(
            text = if (!enabled) "Disabled while patching"
                   else "Where patched APKs are saved. A per-app subfolder is created inside.",
            fontSize = 11.sp,
            fontFamily = mono,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f * alpha)
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
                    fontFamily = mono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f * alpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            OutlinedButton(
                onClick = {
                    val chooser = JFileChooser().apply {
                        dialogTitle = "Select Output Folder"
                        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                        isAcceptAllFileFilterUsed = false
                        outputDir?.takeIf { it.isDirectory }?.let { currentDirectory = it }
                    }
                    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                        onDefaultOutputDirectoryChange(chooser.selectedFile.absolutePath)
                    }
                },
                enabled = enabled,
                shape = RoundedCornerShape(corners.small),
                border = BorderStroke(1.dp, borderColor),
                contentPadding = PaddingValues(horizontal = 10.dp),
                modifier = Modifier.fillMaxHeight()
            ) {
                Text(
                    "BROWSE",
                    fontFamily = mono,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 9.sp,
                    letterSpacing = 0.5.sp
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
                        "RESET",
                        fontFamily = mono,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 9.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        if (defaultOutputDirectory != null && !outputDirExists) {
            Text(
                text = "Folder not found — will be created on next patch",
                fontSize = 10.sp,
                fontFamily = mono,
                color = Color(0xFFE0A030)
            )
        }

        // Stored form first (mirrors config.json), absolute resolution second.
        // Hides the second line entirely when storage IS absolute, repeating
        // the same path twice would make no sense now, innit.
        if (defaultOutputDirectory != null) {
            val stored = app.morphe.engine.util.PortablePaths.storableForm(defaultOutputDirectory)
            val isBundleRelative = stored != defaultOutputDirectory
            Text(
                text = stored,
                fontSize = 9.sp,
                fontFamily = mono,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isBundleRelative) {
                Text(
                    text = "Resolves to: $defaultOutputDirectory",
                    fontSize = 9.sp,
                    fontFamily = mono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    mono: androidx.compose.ui.text.font.FontFamily,
    borderColor: Color,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val corners = LocalMorpheCorners.current
    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()

    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().hoverable(hoverInteraction),
        shape = RoundedCornerShape(corners.small),
        border = BorderStroke(
            1.dp,
            if (isHovered && enabled) contentColor.copy(alpha = 0.3f)
            else borderColor
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = contentColor,
            disabledContentColor = contentColor.copy(alpha = 0.4f)
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            label,
            fontFamily = mono,
            fontWeight = FontWeight.SemiBold,
            fontSize = 10.sp,
            letterSpacing = 0.5.sp,
            modifier = Modifier.weight(1f)
        )
    }
}


// ── Strip Libs Section ──

/**
 * Architectures exposed in the strip libs settings. Each entry has the
 * patcher-facing value (matching CpuArchitecture.arch) and a short display name.
 * Only modern arches are listed — legacy mips/armeabi are intentionally omitted.
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
    mono: androidx.compose.ui.text.font.FontFamily,
    accentColor: Color,
    enabled: Boolean = true,
    expanded: Boolean = false,
    onExpandedChange: (Boolean) -> Unit = {}
) {
    CollapsibleSection(
        title = "STRIP LIBS",
        mono = mono,
        expanded = expanded,
        onExpandedChange = onExpandedChange
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Uncheck architectures you don't need. When patching, the output APK will keep only the architectures present in the APK AND in this list. If none overlap, nothing is stripped to avoid broken APKs.",
                fontSize = 11.sp,
                fontFamily = mono,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
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
                    mono = mono,
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
    mono: androidx.compose.ui.text.font.FontFamily,
    accentColor: Color,
    borderColor: Color,
    enabled: Boolean = true,
    expanded: Boolean = false,
    onExpandedChange: (Boolean) -> Unit = {}
) {
    val corners = LocalMorpheCorners.current
    val dimens = LocalMorpheDimens.current
    val accents = LocalMorpheAccents.current
    val alpha = if (enabled) 1f else 0.4f

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
            title = "SIGNING",
            mono = mono,
            expanded = expanded,
            onExpandedChange = onExpandedChange
        ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = if (!enabled) "Disabled while patching"
                   else "Keystore used to sign patched APKs",
            fontSize = 11.sp,
            fontFamily = mono,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
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
                    fontFamily = mono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f * alpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            OutlinedButton(
                onClick = {
                    val dialog = FileDialog(null as Frame?, "Select Keystore", FileDialog.LOAD).apply {
                        setFilenameFilter { _, n ->
                            n.lowercase().let {
                                it.endsWith(".keystore") || it.endsWith(".jks") ||
                                it.endsWith(".bks") || it.endsWith(".p12") || it.endsWith(".pfx")
                            }
                        }
                        isVisible = true
                    }
                    if (dialog.directory != null && dialog.file != null) {
                        val selected = File(dialog.directory, dialog.file)
                        val validExtensions = listOf(".keystore", ".jks", ".bks", ".p12", ".pfx")
                        if (validExtensions.any { selected.name.lowercase().endsWith(it) }) {
                            keystoreError = null
                            onKeystorePathChange(selected.absolutePath)
                        } else {
                            keystoreError = "Invalid file type. Expected: ${validExtensions.joinToString(", ")}"
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
                    "BROWSE",
                    fontFamily = mono,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 9.sp,
                    letterSpacing = 0.5.sp
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
                        "RESET",
                        fontFamily = mono,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 9.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        // Warning if keystore path set but file doesn't exist. Patching will
        // refuse to start with this configured (see PatchingViewModel) — user
        // must restore the file, pick another, or reset to use Morphe's default.
        if (keystorePath != null && !keystoreExists) {
            Text(
                text = "Keystore not found — patching will fail until you restore it, pick another, or reset",
                fontSize = 10.sp,
                fontFamily = mono,
                color = Color(0xFFE0A030)
            )
        }

        // Error for invalid file type selection
        keystoreError?.let {
            Text(
                text = it,
                fontSize = 10.sp,
                fontFamily = mono,
                color = MaterialTheme.colorScheme.error
            )
        }

        // Either: stored form (relative when inside the bundle, absolute otherwise)
        // with a "Resolves to: ..." subtitle when relative. Mirrors config.json
        // so users can see which paths follow the bundle vs which are pinned.
        // Or: "using default" hint when no user-configured path is set.
        if (keystorePath != null) {
            val stored = app.morphe.engine.util.PortablePaths.storableForm(keystorePath)
            val isBundleRelative = stored != keystorePath
            Text(
                text = stored,
                fontSize = 9.sp,
                fontFamily = mono,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isBundleRelative) {
                Text(
                    text = "Resolves to: $keystorePath",
                    fontSize = 9.sp,
                    fontFamily = mono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
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
            val defaultStored = app.morphe.engine.util.PortablePaths.storableForm(defaultAbs)
            val isBundleRelative = defaultStored != defaultAbs
            val verb = if (MorpheData.defaultKeystoreFile.exists()) "Using"
                       else "Will create"
            Text(
                text = "$verb Morphe's default keystore at $defaultStored",
                fontSize = 9.sp,
                fontFamily = mono,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (isBundleRelative) {
                Text(
                    text = "Resolves to: $defaultAbs",
                    fontSize = 9.sp,
                    fontFamily = mono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LabeledField(label = "KEYSTORE PASSWORD", mono = mono) {
                SlimTextField(
                    value = localPassword,
                    onValueChange = {
                        localPassword = it
                        onCredentialsChange(it.ifEmpty { null }, localAlias, localEntryPassword)
                    },
                    placeholder = "",
                    mono = mono,
                    accents = accents,
                    corners = corners,
                    enabled = enabled,
                    visualTransformation = if (showPassword) androidx.compose.ui.text.input.VisualTransformation.None
                                           else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    trailing = {
                        IconButton(
                            onClick = { showPassword = !showPassword },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showPassword) "Hide" else "Show",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                        }
                    },
                )
            }

            LabeledField(label = "KEY ALIAS", mono = mono) {
                SlimTextField(
                    value = localAlias,
                    onValueChange = {
                        localAlias = it
                        onCredentialsChange(localPassword.ifEmpty { null }, it, localEntryPassword)
                    },
                    placeholder = "",
                    mono = mono,
                    accents = accents,
                    corners = corners,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            LabeledField(label = "KEY PASSWORD", mono = mono) {
                SlimTextField(
                    value = localEntryPassword,
                    onValueChange = {
                        localEntryPassword = it
                        onCredentialsChange(localPassword.ifEmpty { null }, localAlias, it)
                    },
                    placeholder = "",
                    mono = mono,
                    accents = accents,
                    corners = corners,
                    enabled = enabled,
                    visualTransformation = if (showEntryPassword) androidx.compose.ui.text.input.VisualTransformation.None
                                           else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    trailing = {
                        IconButton(
                            onClick = { showEntryPassword = !showEntryPassword },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                imageVector = if (showEntryPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
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
                    val path = keystorePath ?: return@OutlinedButton
                    val result = readKeystoreInfo(
                        path,
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
                modifier = Modifier.fillMaxWidth().height(dimens.controlHeight),
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
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "VERIFY CREDENTIALS",
                    fontFamily = mono,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 9.sp,
                    letterSpacing = 0.5.sp
                )
            }

            verifyResult?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = it,
                    fontSize = 10.sp,
                    fontFamily = mono,
                    color = if (verifySuccess) MorpheColors.Teal else Color(0xFFE0A030),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
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

                    // If no path set, ask the user where to save
                    val path = keystorePath ?: run {
                        val dialog = FileDialog(null as Frame?, "Save Keystore", FileDialog.SAVE).apply {
                            file = "morphe.keystore"
                            isVisible = true
                        }
                        if (dialog.directory != null && dialog.file != null) {
                            val chosen = File(dialog.directory, dialog.file).absolutePath
                            onKeystorePathChange(chosen)
                            chosen
                        } else {
                            return@OutlinedButton // user cancelled
                        }
                    }

                    try {
                        val file = File(path)
                        file.parentFile?.mkdirs()
                        val keyPair = ApkSigner.newPrivateKeyCertificatePair(
                            "Morphe",
                            java.util.Date(System.currentTimeMillis() + 8L * 365 * 24 * 60 * 60 * 1000))
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
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = if (generateSuccess) MorpheColors.Teal else accentColor
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (generateSuccess) "KEYSTORE GENERATED" else "GENERATE KEYSTORE",
                    fontFamily = mono,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 9.sp,
                    letterSpacing = 0.5.sp,
                    color = if (generateSuccess) MorpheColors.Teal else accentColor
                )
            }

            generateError?.let {
                Text(
                    text = it,
                    fontSize = 10.sp,
                    fontFamily = mono,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (!generateSuccess) {
                Text(
                    text = "Uses the credentials entered above",
                    fontSize = 9.sp,
                    fontFamily = mono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
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
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "CERTIFICATE",
                    fontFamily = mono,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 9.sp,
                    letterSpacing = 0.5.sp
                )
            }

            // Export
            OutlinedButton(
                onClick = {
                    val sourceFile = keystoreFile ?: return@OutlinedButton
                    if (!sourceFile.exists()) return@OutlinedButton
                    val dialog = FileDialog(null as Frame?, "Export Keystore", FileDialog.SAVE).apply {
                        file = sourceFile.name
                        isVisible = true
                    }
                    if (dialog.directory != null && dialog.file != null) {
                        try {
                            sourceFile.copyTo(File(dialog.directory, dialog.file), overwrite = true)
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
                    imageVector = Icons.Default.Share,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "EXPORT",
                    fontFamily = mono,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 9.sp,
                    letterSpacing = 0.5.sp
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
    val mono = LocalMorpheFont.current
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
                "CERTIFICATE INFO",
                fontFamily = mono,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 1.sp
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
                                fontSize = 10.sp,
                                fontFamily = mono,
                                color = Color(0xFFE0A030),
                                lineHeight = 14.sp
                            )
                        }
                        // If no cert data (alias not found), stop here
                        if (info.sha256Fingerprint.isEmpty()) return@Column
                        HorizontalDivider(color = borderColor)
                    }

                    CertInfoRow("Alias", info.alias, mono)
                    CertInfoRow("Issuer", info.issuer, mono)
                    CertInfoRow("Valid from", info.validFrom, mono)
                    CertInfoRow("Valid until", info.validTo, mono)

                    HorizontalDivider(color = borderColor)

                    Text(
                        "SHA-256 FINGERPRINT",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = mono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        letterSpacing = 1.sp
                    )
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(
                            text = info.sha256Fingerprint,
                            fontSize = 10.sp,
                            fontFamily = mono,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            lineHeight = 16.sp
                        )
                    }

                    HorizontalDivider(color = borderColor)

                    Text(
                        "SHA-1 FINGERPRINT",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = mono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        letterSpacing = 1.sp
                    )
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(
                            text = info.sha1Fingerprint,
                            fontSize = 10.sp,
                            fontFamily = mono,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            lineHeight = 16.sp
                        )
                    }
                }
            } else {
                Text(
                    text = "Could not read keystore. Check the password and alias.",
                    fontSize = 12.sp,
                    fontFamily = mono,
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
                    "CLOSE",
                    fontFamily = mono,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

@Composable
private fun CertInfoRow(
    label: String,
    value: String,
    mono: androidx.compose.ui.text.font.FontFamily
) {
    Column {
        Text(
            text = label.uppercase(),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = mono,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            letterSpacing = 1.sp
        )
        Text(
            text = value,
            fontSize = 11.sp,
            fontFamily = mono,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
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
        if (java.security.Security.getProvider("BC") == null) {
            java.security.Security.addProvider(
                Class.forName("org.bouncycastle.jce.provider.BouncyCastleProvider")
                    .getDeclaredConstructor().newInstance() as java.security.Provider
            )
        }
    } catch (_: Exception) {
        // BC not on classpath — BKS keystores won't be readable, but JKS/PKCS12 still work
    }

    // Try multiple keystore types: BKS (what Morphe generates), then JKS, then PKCS12
    // BKS requires BouncyCastle provider — try with provider name, fall back without
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
                issuer = cert.issuerDN.name,
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
        ThemePreference.AMOLED -> "AMOLED"
        ThemePreference.NORD -> "Nord"
        ThemePreference.CATPPUCCIN -> "Catppuccin"
        ThemePreference.SAKURA -> "Sakura"
        ThemePreference.MATCHA -> "Matcha"
        ThemePreference.DEEPSPACE -> "Deepspace"
        ThemePreference.SYSTEM -> "System"
    }
}

private fun ThemePreference.iconSymbol(): String {
    return when (this) {
        ThemePreference.LIGHT -> "☀"
        ThemePreference.DARK -> "☾"
        ThemePreference.AMOLED -> "◆"
        ThemePreference.NORD -> "❄"
        ThemePreference.CATPPUCCIN -> "🐱"
        ThemePreference.SAKURA -> "🌸"
        ThemePreference.MATCHA -> "🍵"
        ThemePreference.DEEPSPACE -> "✦"
        ThemePreference.SYSTEM -> "⚙"
    }
}

private fun ThemePreference.accentColor(): Color {
    return when (this) {
        ThemePreference.LIGHT -> MorpheColors.Blue
        ThemePreference.DARK -> MorpheColors.Blue
        ThemePreference.AMOLED -> MorpheColors.Cyan
        ThemePreference.NORD -> Color(0xFF88C0D0)
        ThemePreference.CATPPUCCIN -> Color(0xFFCBA6F7)
        ThemePreference.SAKURA -> Color(0xFFB43A67)
        ThemePreference.MATCHA -> Color(0xFF4C7A35)
        ThemePreference.DEEPSPACE -> Color(0xFF00D9FF)
        ThemePreference.SYSTEM -> MorpheColors.Blue
    }
}

private fun calculateCacheSize(): String {
    val patchesSize = FileUtils.getPatchesDir().walkTopDown().filter { it.isFile }.sumOf { it.length() }
    val logsSize = FileUtils.getLogsDir().walkTopDown().filter { it.isFile }.sumOf { it.length() }
    val totalSize = patchesSize + logsSize

    return when {
        totalSize < 1024 -> "$totalSize B"
        totalSize < 1024 * 1024 -> "%.1f KB".format(totalSize / 1024.0)
        else -> "%.1f MB".format(totalSize / (1024.0 * 1024.0))
    }
}

private fun clearAllCache(): Boolean {
    return try {
        var failedCount = 0
        FileUtils.getPatchesDir().listFiles()?.forEach { file ->
            try { if (!file.deleteRecursively()) throw Exception("Could not delete") }
            catch (e: Exception) { failedCount++; Logger.error("Failed to delete ${file.name}: ${e.message}") }
        }
        FileUtils.getLogsDir().listFiles()?.forEach { file ->
            try { if (!file.deleteRecursively()) throw Exception("Could not delete") }
            catch (e: Exception) { failedCount++; Logger.error("Failed to delete log ${file.name}: ${e.message}") }
        }

        FileUtils.cleanupAllTempDirs()
        if (failedCount > 0) {
            Logger.error("Cache clear incomplete: $failedCount file(s) could not be deleted (may be locked)")
            false
        } else {
            Logger.info("Cache cleared successfully")
            true
        }
    } catch (e: Exception) {
        Logger.error("Failed to clear cache", e)
        false
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
    mono: androidx.compose.ui.text.font.FontFamily,
    accentColor: Color,
    borderColor: Color,
    enabled: Boolean = true,
    expanded: Boolean = false,
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
        title = "PATCHED APP RUNTIME LOGS",
        mono = mono,
        expanded = expanded,
        onExpandedChange = onExpandedChange
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Capture logs from your phone after a patched app crashes or misbehaves. Clear before reproducing the bug, then save the filtered output to attach to a bug report.",
                fontSize = 11.sp,
                fontFamily = mono,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )

            // Device row
            if (deviceReady) {
                Text(
                    text = "Device: ${selectedDevice.displayName}${selectedDevice.architecture?.let { " ($it)" } ?: ""}",
                    fontSize = 11.sp,
                    fontFamily = mono,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            } else {
                Text(
                    text = "No device connected. Plug in your phone with USB debugging enabled.",
                    fontSize = 11.sp,
                    fontFamily = mono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }

            ActionButton(
                label = if (status is RuntimeLogsStatus.Clearing) "CLEARING…" else "CLEAR DEVICE LOGS",
                icon = Icons.Default.DeleteSweep,
                mono = mono,
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
                label = if (status is RuntimeLogsStatus.Saving) "SAVING…" else "SAVE DEVICE LOGS",
                icon = Icons.Default.Save,
                mono = mono,
                borderColor = borderColor,
                contentColor = accentColor,
                enabled = canAct,
                onClick = {
                    val device = selectedDevice ?: return@ActionButton
                    status = RuntimeLogsStatus.Saving
                    scope.launch {
                        val timestamp = SimpleDateFormat("yyyy-MM-dd-HHmmss", java.util.Locale.US).format(java.util.Date())
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
                    text = "Logs cleared on device.",
                    fontSize = 11.sp,
                    fontFamily = mono,
                    color = accentColor.copy(alpha = 0.85f)
                )
                is RuntimeLogsStatus.Saved -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (s.lineCount == 0)
                            "Nothing captured yet. Run the patched app on your phone, then save again."
                        else
                            "Saved ${s.lineCount} line(s) to ${s.file.name}",
                        fontSize = 11.sp,
                        fontFamily = mono,
                        color = if (s.lineCount == 0) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                else accentColor.copy(alpha = 0.85f)
                    )
                    if (s.lineCount > 0) {
                        val cornersLocal = LocalMorpheCorners.current
                        Text(
                            text = "OPEN LOGS",
                            fontSize = 10.sp,
                            fontFamily = mono,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.5.sp,
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
                    fontFamily = mono,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
