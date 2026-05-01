/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.gui.ui.screens.quick

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import app.morphe.morphe_cli.generated.resources.Res
import app.morphe.morphe_cli.generated.resources.morphe_dark
import app.morphe.morphe_cli.generated.resources.morphe_light
import app.morphe.gui.data.model.Patch
import app.morphe.gui.data.model.SupportedApp
import app.morphe.gui.data.repository.ConfigRepository
import app.morphe.gui.data.repository.PatchSourceManager
import app.morphe.gui.ui.components.OfflineBanner
import app.morphe.gui.ui.components.TopBarRow
import app.morphe.gui.ui.components.morpheScrollbarStyle
import app.morphe.gui.ui.screens.home.components.FullScreenDropZone
import app.morphe.gui.ui.theme.*
import app.morphe.gui.util.ChecksumStatus
import app.morphe.gui.util.StatusColorType
import app.morphe.gui.util.resolveStatusColorType
import app.morphe.gui.util.resolveVersionStatusDisplay
import app.morphe.gui.util.toColor
import app.morphe.gui.util.DownloadUrlResolver.openUrlAndFollowRedirects
import app.morphe.gui.util.VersionStatus
import app.morphe.gui.util.PatchService
import app.morphe.gui.util.AdbManager
import app.morphe.gui.util.DeviceMonitor
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

class QuickPatchScreen : Screen {
    @Composable
    override fun Content() {
        val patchSourceManager: PatchSourceManager = koinInject()
        val patchService: PatchService = koinInject()
        val configRepository: ConfigRepository = koinInject()
        val updateCheckRepository: app.morphe.gui.data.repository.UpdateCheckRepository = koinInject()
        val viewModel = remember {
            QuickPatchViewModel(patchSourceManager, patchService, configRepository, updateCheckRepository)
        }
        QuickPatchContent(viewModel)
    }
}

@Composable
fun QuickPatchContent(viewModel: QuickPatchViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.10f)
    val density = androidx.compose.ui.platform.LocalDensity.current
    var leadingWidthPx by remember { mutableIntStateOf(0) }
    var trailingWidthPx by remember { mutableIntStateOf(0) }
    val centerSidePadding = with(density) { maxOf(leadingWidthPx, trailingWidthPx).toDp() } + 16.dp

    FullScreenDropZone(
        isDragHovering = uiState.isDragHovering,
        onDragHoverChange = { viewModel.setDragHover(it) },
        onFilesDropped = { files ->
            files.firstOrNull {
                it.name.endsWith(".apk", ignoreCase = true) ||
                it.name.endsWith(".apkm", ignoreCase = true) ||
                it.name.endsWith(".xapk", ignoreCase = true) ||
                it.name.endsWith(".apks", ignoreCase = true)
            }?.let { viewModel.onFileSelected(it) }
        },
        enabled = uiState.phase != QuickPatchPhase.ANALYZING
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // ── Header row — matches expert mode ──
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            drawLine(
                                color = borderColor,
                                start = Offset(0f, size.height),
                                end = Offset(size.width, size.height),
                                strokeWidth = 1f
                            )
                        }
                        .padding(vertical = 8.dp)
                ) {
                    // Logo — left-aligned
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 12.dp)
                            .onSizeChanged { leadingWidthPx = it.width }
                    ) {
                        BrandingLogo()
                    }

                    // Patches version badge — centered
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(start = centerSidePadding, end = centerSidePadding)
                    ) {
                        PatchesVersionBadge(
                            patchesVersion = uiState.patchesVersion,
                            isLoading = uiState.isLoadingPatches,
                            patchSourceName = uiState.patchSourceName,
                            latestLabel = if (uiState.patchesVersion != null &&
                                              uiState.patchesVersion == uiState.latestPatchesVersion) {
                                "LATEST STABLE"
                            } else null
                        )
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 12.dp)
                            .onSizeChanged { trailingWidthPx = it.width }
                    ) {
                        TopBarRow(
                            allowCacheClear = false,
                            isPatching = uiState.phase == QuickPatchPhase.DOWNLOADING || uiState.phase == QuickPatchPhase.PATCHING,
                            onUpdateChannelChanged = { viewModel.refreshUpdateCheck() },
                        )
                    }
                }

                // ── Content ──
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Offline banner
                    if (uiState.isOffline && uiState.phase == QuickPatchPhase.IDLE) {
                        OfflineBanner(
                            onRetry = { viewModel.retryLoadPatches() },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        )
                    }

                    // CLI update banner
                    if (uiState.showUpdateBanner) {
                        app.morphe.gui.ui.components.UpdateBanner(
                            info = uiState.updateInfo!!,
                            onDismissForSession = { viewModel.dismissUpdateForSession() },
                            onDismissForVersion = { viewModel.dismissUpdateForVersion() },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        )
                    }

                    // ── Main content ──
                    val lastApkInfo = remember(uiState.apkInfo) { uiState.apkInfo }
                    val lastOutputPath = remember(uiState.outputPath) { uiState.outputPath }

                    AnimatedContent(
                        targetState = uiState.phase,
                        modifier = Modifier.weight(1f),
                        transitionSpec = {
                            fadeIn(tween(200)) togetherWith fadeOut(tween(200))
                        }
                    ) { phase ->
                        when (phase) {
                            QuickPatchPhase.IDLE, QuickPatchPhase.ANALYZING -> {
                                IdleContent(
                                    isAnalyzing = phase == QuickPatchPhase.ANALYZING,
                                    isDragHovering = uiState.isDragHovering,
                                    onBrowse = { openFilePicker()?.let { viewModel.onFileSelected(it) } }
                                )
                            }
                            QuickPatchPhase.READY -> {
                                val info = uiState.apkInfo ?: lastApkInfo
                                if (info != null) {
                                    ReadyContent(
                                        apkInfo = info,
                                        compatiblePatches = uiState.compatiblePatches,
                                        onPatch = { viewModel.startPatching() },
                                        onClear = { viewModel.reset() }
                                    )
                                }
                            }
                            QuickPatchPhase.DOWNLOADING, QuickPatchPhase.PATCHING -> {
                                PatchingContent(
                                    phase = phase,
                                    statusMessage = uiState.statusMessage,
                                    onCancel = { viewModel.cancelPatching() }
                                )
                            }
                            QuickPatchPhase.COMPLETED -> {
                                val info = uiState.apkInfo ?: lastApkInfo
                                val output = uiState.outputPath ?: lastOutputPath
                                if (info != null && output != null) {
                                    CompletedContent(
                                        outputPath = output,
                                        apkInfo = info,
                                        onPatchAnother = { viewModel.reset() }
                                    )
                                }
                            }
                        }
                    }

                    // ── Supported apps (idle only) ──
                    if (uiState.phase == QuickPatchPhase.IDLE) {
                        Spacer(modifier = Modifier.height(16.dp))
                        SupportedAppsRow(
                            supportedApps = uiState.supportedApps,
                            isLoading = uiState.isLoadingPatches,
                            loadError = uiState.patchLoadError,
                            isDefaultSource = uiState.isDefaultSource,
                            onRetry = { viewModel.retryLoadPatches() }
                        )
                    }
                }
            }

            // Drag overlay
            if (uiState.isDragHovering) {
                DragOverlay()
            }

            // Error/warning bar
            uiState.error?.let { error ->
                val mono = LocalMorpheFont.current
                val isUnsupportedWarning = error.contains("not supported in Quick Patch")
                val accentColor = if (isUnsupportedWarning) accents.warning else MaterialTheme.colorScheme.error
                val barBg = MaterialTheme.colorScheme.surface
                val borderCol = accentColor.copy(alpha = 0.4f)

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(corners.small))
                        .border(1.dp, borderCol, RoundedCornerShape(corners.small))
                        .background(barBg)
                        .drawBehind {
                            drawRect(
                                color = accentColor,
                                size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height)
                            )
                        }
                        .padding(start = 3.dp)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(accentColor, RoundedCornerShape(1.dp))
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = error,
                        fontFamily = mono,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(12.dp))

                    // Dismiss — same hover pattern as the close button
                    val dismissHover = remember { MutableInteractionSource() }
                    val isDismissHovered by dismissHover.collectIsHoveredAsState()
                    val dismissBg by animateColorAsState(
                        if (isDismissHovered) accentColor.copy(alpha = 0.12f)
                        else Color.Transparent,
                        animationSpec = tween(150)
                    )
                    Box(
                        modifier = Modifier
                            .height(28.dp)
                            .hoverable(dismissHover)
                            .clip(RoundedCornerShape(corners.small))
                            .background(dismissBg)
                            .clickable { viewModel.clearError() }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "DISMISS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = mono,
                            color = if (isDismissHovered) accentColor
                                    else accentColor.copy(alpha = 0.7f),
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
//  BRANDING — Logo + patches version badge
// ════════════════════════════════════════════════════════════════════

@Composable
private fun BrandingLogo() {
    val themeState = LocalThemeState.current
    val isDark = when (themeState.current) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        else -> themeState.current.isDark()
    }

    Image(
        painter = painterResource(if (isDark) Res.drawable.morphe_dark else Res.drawable.morphe_light),
        contentDescription = "Morphe Logo",
        modifier = Modifier.height(28.dp)
    )
}

@Composable
private fun PatchesVersionBadge(
    patchesVersion: String?,
    isLoading: Boolean,
    patchSourceName: String? = null,
    latestLabel: String? = null,
) {
    val mono = LocalMorpheFont.current
    val corners = LocalMorpheCorners.current
    val accents = LocalMorpheAccents.current

    if (isLoading) {
        Row(
            modifier = Modifier
                .height(34.dp)
                .clip(RoundedCornerShape(corners.small))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(corners.small))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.5.dp,
                color = accents.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "LOADING…",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = mono,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                letterSpacing = 1.sp
            )
        }
    } else if (patchesVersion != null) {
        Row(
            modifier = Modifier
                .height(34.dp)
                .clip(RoundedCornerShape(corners.small))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(corners.small))
                .background(MaterialTheme.colorScheme.surface)
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = patchSourceName?.uppercase() ?: "PATCHES",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = mono,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                letterSpacing = 1.5.sp
            )
            Text(
                text = " · ",
                fontSize = 10.sp,
                fontFamily = mono,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
            )
            Text(
                text = patchesVersion,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = mono,
                color = accents.primary
            )
            if (latestLabel != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .background(accents.secondary.copy(alpha = 0.1f), RoundedCornerShape(corners.small))
                        .border(1.dp, accents.secondary.copy(alpha = 0.2f), RoundedCornerShape(corners.small))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = latestLabel,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = mono,
                        color = accents.secondary,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
//  IDLE — Simple drop zone
// ════════════════════════════════════════════════════════════════════

@Composable
private fun IdleContent(
    isAnalyzing: Boolean,
    isDragHovering: Boolean,
    onBrowse: () -> Unit
) {
    val corners = LocalMorpheCorners.current
    val accents = LocalMorpheAccents.current
    val bracketColor = if (isDragHovering) accents.primary.copy(alpha = 0.7f)
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(enabled = !isAnalyzing) { onBrowse() }
            .drawBehind {
                val strokeWidth = 2f
                val len = 32.dp.toPx()
                val inset = 0f

                // Top-left
                drawLine(bracketColor, Offset(inset, inset), Offset(inset + len, inset), strokeWidth)
                drawLine(bracketColor, Offset(inset, inset), Offset(inset, inset + len), strokeWidth)
                // Top-right
                drawLine(bracketColor, Offset(size.width - inset, inset), Offset(size.width - inset - len, inset), strokeWidth)
                drawLine(bracketColor, Offset(size.width - inset, inset), Offset(size.width - inset, inset + len), strokeWidth)
                // Bottom-left
                drawLine(bracketColor, Offset(inset, size.height - inset), Offset(inset + len, size.height - inset), strokeWidth)
                drawLine(bracketColor, Offset(inset, size.height - inset), Offset(inset, size.height - inset - len), strokeWidth)
                // Bottom-right
                drawLine(bracketColor, Offset(size.width - inset, size.height - inset), Offset(size.width - inset - len, size.height - inset), strokeWidth)
                drawLine(bracketColor, Offset(size.width - inset, size.height - inset), Offset(size.width - inset, size.height - inset - len), strokeWidth)
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (isAnalyzing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(40.dp),
                    color = accents.primary,
                    strokeWidth = 3.dp
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Analyzing APK…",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = if (isDragHovering) accents.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Drop APK here",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDragHovering) accents.primary
                           else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "or click to browse",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = ".apk  ·  .apkm  ·  .xapk  ·  .apks",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
//  READY — Compact APK card + patch button
// ════════════════════════════════════════════════════════════════════

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ReadyContent(
    apkInfo: QuickApkInfo,
    compatiblePatches: List<Patch>,
    onPatch: () -> Unit,
    onClear: () -> Unit
) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)

    val statusColorType = resolveStatusColorType(apkInfo.versionStatus, apkInfo.checksumStatus)
    val accentColor = if (statusColorType == StatusColorType.PRIMARY) accents.secondary
                      else statusColorType.toColor()

    val enabledPatches = compatiblePatches.filter { it.isEnabled }
    val disabledPatches = compatiblePatches.filter { !it.isEnabled }
    var patchSearchQuery by remember { mutableStateOf("") }
    // Patches list is collapsed by default — the chip flow can grow long enough
    // to overwhelm the simplified flow's "just hit PATCH" intent. Users who
    // want to inspect or search expand it manually.
    var patchesExpanded by remember { mutableStateOf(false) }

    val readyScrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(readyScrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── APK info card — bordered box with accent stripe ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(corners.medium))
                .border(1.dp, borderColor, RoundedCornerShape(corners.medium))
                .background(MaterialTheme.colorScheme.surface)
                .drawBehind {
                    drawRect(
                        color = accentColor,
                        size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height)
                    )
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 3.dp)
            ) {
                // Header: app identity + dismiss
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // App initial
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(corners.small))
                            .background(accentColor.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = apkInfo.displayName.first().uppercase(),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = mono,
                            color = accentColor
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = apkInfo.displayName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "v${apkInfo.versionName} · ${apkInfo.formattedSize}",
                            fontSize = 11.sp,
                            fontFamily = mono,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            letterSpacing = 0.3.sp
                        )
                    }

                    // Dismiss button
                    val closeHover = remember { MutableInteractionSource() }
                    val isCloseHovered by closeHover.collectIsHoveredAsState()
                    val closeBg by animateColorAsState(
                        if (isCloseHovered) MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                        else Color.Transparent,
                        animationSpec = tween(150)
                    )

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .hoverable(closeHover)
                            .clip(RoundedCornerShape(corners.small))
                            .background(closeBg)
                            .clickable(onClick = onClear),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = if (isCloseHovered) MaterialTheme.colorScheme.error
                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Status bar
                val statusDisplay = resolveVersionStatusDisplay(
                    apkInfo.versionStatus, apkInfo.checksumStatus, apkInfo.suggestedVersion
                )
                val statusText = statusDisplay?.label
                val statusDetail = statusDisplay?.detail

                if (statusText != null) {
                    // Modifier order matters: putting the outer padding BEFORE
                    // background insets the tinted strip 20dp from each card
                    // edge, lining up with the divider stroke above. We then
                    // clip the background to a small rounded shape and apply
                    // inner padding so the dot + text sit nicely inset from
                    // the strip's edges.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .drawBehind {
                                drawLine(
                                    color = borderColor,
                                    start = Offset(20.dp.toPx(), 0f),
                                    end = Offset(size.width - 20.dp.toPx(), 0f),
                                    strokeWidth = 1f
                                )
                            }
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(corners.small))
                            .background(accentColor.copy(alpha = 0.06f))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(accentColor, RoundedCornerShape(1.dp))
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = statusText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = mono,
                            color = accentColor,
                            letterSpacing = 1.sp
                        )
                        if (statusDetail != null) {
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = statusDetail,
                                fontSize = 11.sp,
                                fontFamily = mono,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // ── Architectures row ──
                if (apkInfo.architectures.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .drawBehind {
                                drawLine(
                                    color = borderColor,
                                    start = Offset(20.dp.toPx(), 0f),
                                    end = Offset(size.width - 20.dp.toPx(), 0f),
                                    strokeWidth = 1f
                                )
                            }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val deviceState by DeviceMonitor.state.collectAsState()
                        val deviceArch = deviceState.selectedDevice?.architecture
                        val hasMultipleArchs = apkInfo.architectures.size > 1
                        val highlightArch = if (hasMultipleArchs && deviceArch != null) deviceArch else null

                        Text(
                            text = "ARCH",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = mono,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            letterSpacing = 1.5.sp
                        )
                        apkInfo.architectures.forEach { arch ->
                            val isDeviceArch = highlightArch != null && arch == highlightArch
                            val tagBorder = if (isDeviceArch) accents.primary.copy(alpha = 0.5f)
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                            val tagBg = if (isDeviceArch) accents.primary.copy(alpha = 0.08f)
                                else Color.Transparent
                            val tagColor = if (isDeviceArch) accents.primary
                                else MaterialTheme.colorScheme.onSurface
                            val dimmed = highlightArch != null && !isDeviceArch

                            Box(
                                modifier = Modifier
                                    .border(1.dp, tagBorder, RoundedCornerShape(corners.small))
                                    .background(tagBg, RoundedCornerShape(corners.small))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = arch,
                                    fontSize = 11.sp,
                                    fontWeight = if (isDeviceArch) FontWeight.Bold else FontWeight.Medium,
                                    fontFamily = mono,
                                    color = if (dimmed) tagColor.copy(alpha = 0.35f) else tagColor
                                )
                            }
                        }
                    }
                }

                // ── Min SDK row ──
                if (apkInfo.minSdk != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .drawBehind {
                                drawLine(
                                    color = borderColor,
                                    start = Offset(20.dp.toPx(), 0f),
                                    end = Offset(size.width - 20.dp.toPx(), 0f),
                                    strokeWidth = 1f
                                )
                            }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "MIN SDK",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = mono,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            letterSpacing = 1.5.sp
                        )
                        Text(
                            text = "${apkInfo.minSdk}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = mono,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        // ── Patches card. Collapsed by default — header alone, intrinsic
        // height. When expanded, gains a bounded body so the chip flow
        // doesn't dominate a short window; the body's own scroll handles
        // long patch lists. ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (patchesExpanded) {
                        Modifier.heightIn(min = 280.dp, max = 520.dp)
                    } else Modifier
                )
                .clip(RoundedCornerShape(corners.medium))
                .border(1.dp, borderColor, RoundedCornerShape(corners.medium))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (compatiblePatches.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No compatible patches for this app",
                            fontSize = 11.sp,
                            fontFamily = mono,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    // Header — clickable to toggle the patch chip body. Tap
                    // anywhere on the row to expand/collapse; a chevron at
                    // the trailing edge indicates the current state.
                    val chevronRotation by animateFloatAsState(
                        targetValue = if (patchesExpanded) 180f else 0f,
                        animationSpec = tween(200),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { patchesExpanded = !patchesExpanded }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PATCHES",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = mono,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            letterSpacing = 1.5.sp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "${enabledPatches.size} enabled",
                            fontSize = 11.sp,
                            fontFamily = mono,
                            fontWeight = FontWeight.Medium,
                            color = accents.primary
                        )
                        if (disabledPatches.isNotEmpty()) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "·",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "${disabledPatches.size} disabled",
                                fontSize = 11.sp,
                                fontFamily = mono,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = if (patchesExpanded) "Collapse patches" else "Expand patches",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .size(18.dp)
                                .graphicsLayer { rotationZ = chevronRotation },
                        )
                    }

                    AnimatedVisibility(
                        visible = patchesExpanded,
                        // Weight here threads the bounded patches-card height
                        // down to the body's verticalScroll. Without it, the
                        // inner Column → AnimatedVisibility chain measures
                        // unbounded vertically and verticalScroll throws
                        // "scrollable measured with infinity".
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Divider
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(borderColor)
                            )

                            // Body: search + chips, scrollable
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 20.dp, vertical = 14.dp)
                            ) {
                        val muted = MaterialTheme.colorScheme.onSurfaceVariant
                        val searchInteraction = remember { MutableInteractionSource() }
                        val isSearchFocused by searchInteraction.collectIsFocusedAsState()
                        val searchBorder by animateColorAsState(
                            if (isSearchFocused) MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                            animationSpec = tween(150)
                        )

                        BasicTextField(
                            value = patchSearchQuery,
                            onValueChange = { patchSearchQuery = it },
                            singleLine = true,
                            interactionSource = searchInteraction,
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = mono,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(accents.primary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                                .clip(RoundedCornerShape(corners.small))
                                .border(1.dp, searchBorder, RoundedCornerShape(corners.small)),
                            decorationBox = { innerTextField ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = null,
                                        tint = muted.copy(alpha = 0.55f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(modifier = Modifier.weight(1f)) {
                                        if (patchSearchQuery.isEmpty()) {
                                            Text(
                                                "Search patches…",
                                                fontSize = 11.sp,
                                                fontFamily = mono,
                                                color = muted.copy(alpha = 0.4f)
                                            )
                                        }
                                        innerTextField()
                                    }
                                    if (patchSearchQuery.isNotEmpty()) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(18.dp)
                                                .clip(RoundedCornerShape(corners.small))
                                                .clickable { patchSearchQuery = "" },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Clear,
                                                contentDescription = "Clear",
                                                tint = muted.copy(alpha = 0.5f),
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        )

                        Spacer(Modifier.height(12.dp))

                        val filteredPatches = if (patchSearchQuery.isBlank()) {
                            compatiblePatches
                        } else {
                            compatiblePatches.filter {
                                it.name.contains(patchSearchQuery, ignoreCase = true) ||
                                it.description.contains(patchSearchQuery, ignoreCase = true)
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            filteredPatches.forEach { patch ->
                                val isEnabled = patch.isEnabled
                                val chipBorder = if (isEnabled) accents.primary.copy(alpha = 0.5f)
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                                val chipBg = if (isEnabled) accents.primary.copy(alpha = 0.08f)
                                    else Color.Transparent
                                val chipTextColor = if (isEnabled) accents.primary
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)

                                Box(
                                    modifier = Modifier
                                        .border(1.dp, chipBorder, RoundedCornerShape(corners.small))
                                        .background(chipBg, RoundedCornerShape(corners.small))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = patch.name,
                                        fontSize = 10.sp,
                                        fontWeight = if (isEnabled) FontWeight.Medium else FontWeight.Normal,
                                        fontFamily = mono,
                                        color = chipTextColor,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        if (filteredPatches.isEmpty() && patchSearchQuery.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "No patches matching \"$patchSearchQuery\"",
                                fontSize = 11.sp,
                                fontFamily = mono,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                        }
                            } // close body Column
                        } // close AnimatedVisibility wrapper Column
                    } // close AnimatedVisibility
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Patch button — full width
        val patchHover = remember { MutableInteractionSource() }
        val isPatchHovered by patchHover.collectIsHoveredAsState()
        val patchBg by animateColorAsState(
            if (isPatchHovered) accents.primary.copy(alpha = 0.9f) else accents.primary,
            animationSpec = tween(150)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .hoverable(patchHover)
                .clip(RoundedCornerShape(corners.small))
                .background(patchBg, RoundedCornerShape(corners.small))
                .clickable(onClick = onPatch),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "PATCH WITH DEFAULTS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = mono,
                color = Color.White,
                letterSpacing = 1.sp
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "${enabledPatches.size} patches will be applied" +
                if (disabledPatches.isNotEmpty()) " · ${disabledPatches.size} excluded" else "",
            fontSize = 11.sp,
            fontFamily = mono,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            textAlign = TextAlign.Center
        )
    }
}

// ════════════════════════════════════════════════════════════════════
//  PATCHING — Progress
// ════════════════════════════════════════════════════════════════════

@Composable
private fun PatchingContent(
    phase: QuickPatchPhase,
    statusMessage: String,
    onCancel: () -> Unit
) {
    val mono = LocalMorpheFont.current
    val corners = LocalMorpheCorners.current
    val accents = LocalMorpheAccents.current

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp),
            strokeWidth = 3.dp,
            color = accents.secondary
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = when (phase) {
                QuickPatchPhase.DOWNLOADING -> "PREPARING"
                QuickPatchPhase.PATCHING -> "PATCHING"
                else -> ""
            },
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = mono,
            color = accents.secondary,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = statusMessage,
            fontSize = 11.sp,
            fontFamily = mono,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        val cancelHover = remember { MutableInteractionSource() }
        val isCancelHovered by cancelHover.collectIsHoveredAsState()
        val cancelBg by animateColorAsState(
            if (isCancelHovered) MaterialTheme.colorScheme.error.copy(alpha = 0.1f) else Color.Transparent,
            animationSpec = tween(150)
        )

        Box(
            modifier = Modifier
                .hoverable(cancelHover)
                .clip(RoundedCornerShape(corners.small))
                .background(cancelBg)
                .clickable(onClick = onCancel)
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text(
                text = "CANCEL",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = mono,
                color = MaterialTheme.colorScheme.error,
                letterSpacing = 0.5.sp
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════
//  COMPLETED — Success
// ════════════════════════════════════════════════════════════════════

@Composable
private fun CompletedContent(
    outputPath: String,
    apkInfo: QuickApkInfo,
    onPatchAnother: () -> Unit
) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
    val outputFile = File(outputPath)
    val scope = rememberCoroutineScope()
    val adbManager = remember { AdbManager() }
    val monitorState by DeviceMonitor.state.collectAsState()
    var isInstalling by remember { mutableStateOf(false) }
    var installError by remember { mutableStateOf<String?>(null) }
    var installSuccess by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "PATCHING COMPLETE",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = mono,
            color = accents.secondary,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Output file card
        Box(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(corners.medium))
                .border(1.dp, borderColor, RoundedCornerShape(corners.medium))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(accents.secondary)
                    .align(Alignment.CenterStart)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 3.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Text(
                        text = "OUTPUT FILE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = mono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        letterSpacing = 1.5.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = outputFile.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = mono,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (outputFile.exists()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = formatFileSize(outputFile.length()),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = mono,
                            color = accents.secondary
                        )
                    }
                }

                // Open folder link
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            drawLine(
                                color = borderColor,
                                start = Offset(20.dp.toPx(), 0f),
                                end = Offset(size.width - 20.dp.toPx(), 0f),
                                strokeWidth = 1f
                            )
                        }
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val folderHover = remember { MutableInteractionSource() }
                    val isFolderHovered by folderHover.collectIsHoveredAsState()
                    val folderBg by animateColorAsState(
                        if (isFolderHovered) accents.primary.copy(alpha = 0.1f)
                        else Color.Transparent,
                        animationSpec = tween(150)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .hoverable(folderHover)
                            .clip(RoundedCornerShape(corners.small))
                            .background(folderBg, RoundedCornerShape(corners.small))
                            .border(
                                1.dp,
                                accents.primary.copy(alpha = if (isFolderHovered) 0.5f else 0.3f),
                                RoundedCornerShape(corners.small)
                            )
                            .clickable {
                                try {
                                    val folder = outputFile.parentFile
                                    if (folder != null && Desktop.isDesktopSupported()) {
                                        Desktop.getDesktop().open(folder)
                                    }
                                } catch (_: Exception) {}
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "OPEN FOLDER →",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = mono,
                            color = if (isFolderHovered) accents.primary
                                    else accents.primary.copy(alpha = 0.7f),
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }

        // ADB install
        if (monitorState.isAdbAvailable == true) {
            Spacer(modifier = Modifier.height(12.dp))

            val readyDevices = monitorState.devices.filter { it.isReady }
            val selectedDevice = monitorState.selectedDevice

            when {
                installSuccess -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(accents.secondary, RoundedCornerShape(1.dp))
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "INSTALLED ON ${(selectedDevice?.displayName ?: "DEVICE").uppercase()}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = mono,
                            color = accents.secondary,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
                isInstalling -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = accents.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "INSTALLING…",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = mono,
                            color = accents.primary,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
                readyDevices.isNotEmpty() -> {
                    val device = selectedDevice ?: readyDevices.first()
                    val installHover = remember { MutableInteractionSource() }
                    val isInstallHovered by installHover.collectIsHoveredAsState()
                    val installBg by animateColorAsState(
                        if (isInstallHovered) accents.secondary.copy(alpha = 0.9f) else accents.secondary,
                        animationSpec = tween(150)
                    )

                    Box(
                        modifier = Modifier
                            .widthIn(max = 480.dp)
                            .fillMaxWidth()
                            .height(38.dp)
                            .hoverable(installHover)
                            .clip(RoundedCornerShape(corners.small))
                            .background(installBg, RoundedCornerShape(corners.small))
                            .clickable {
                                scope.launch {
                                    isInstalling = true
                                    installError = null
                                    val result = adbManager.installApk(
                                        apkPath = outputPath,
                                        deviceId = device.id
                                    )
                                    result.fold(
                                        onSuccess = { installSuccess = true },
                                        onFailure = { installError = it.message }
                                    )
                                    isInstalling = false
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "INSTALL ON ${device.displayName.uppercase()}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = mono,
                            color = Color.White,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
                else -> {
                    Text(
                        text = "Connect a device via USB to install with ADB",
                        fontSize = 10.sp,
                        fontFamily = mono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
            }

            installError?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = error,
                    fontSize = 10.sp,
                    fontFamily = mono,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Patch another button
        val patchAnotherHover = remember { MutableInteractionSource() }
        val isPatchAnotherHovered by patchAnotherHover.collectIsHoveredAsState()
        val patchAnotherBg by animateColorAsState(
            if (isPatchAnotherHovered) accents.primary.copy(alpha = 0.9f) else accents.primary,
            animationSpec = tween(150)
        )

        Box(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .height(42.dp)
                .hoverable(patchAnotherHover)
                .clip(RoundedCornerShape(corners.small))
                .background(patchAnotherBg, RoundedCornerShape(corners.small))
                .clickable(onClick = onPatchAnother),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "PATCH ANOTHER",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = mono,
                color = Color.White,
                letterSpacing = 1.sp
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════
//  SUPPORTED APPS — Simple row at the bottom
// ════════════════════════════════════════════════════════════════════

@Composable
private fun SupportedAppsRow(
    supportedApps: List<SupportedApp>,
    isLoading: Boolean,
    loadError: String? = null,
    isDefaultSource: Boolean = true,
    onRetry: () -> Unit = {}
) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val uriHandler = LocalUriHandler.current
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "SUPPORTED APPS",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = mono,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            letterSpacing = 3.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = if (isDefaultSource) "Download the exact version from APKMirror and drop it here."
                   else "Drop the APK for a supported app here.",
            fontSize = 11.sp,
            fontFamily = mono,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(max = 500.dp)
                .padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        when {
            isLoading -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = accents.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Loading supported apps…",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
            loadError != null || supportedApps.isEmpty() -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = loadError ?: "Could not load supported apps",
                        fontSize = 11.sp,
                        fontFamily = mono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    val retryHover = remember { MutableInteractionSource() }
                    val isRetryHovered by retryHover.collectIsHoveredAsState()
                    Box(
                        modifier = Modifier
                            .hoverable(retryHover)
                            .clip(RoundedCornerShape(corners.small))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                    alpha = if (isRetryHovered) 0.3f else 0.12f
                                ),
                                RoundedCornerShape(corners.small)
                            )
                            .clickable(onClick = onRetry)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "RETRY",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = mono,
                            color = MaterialTheme.colorScheme.onSurface,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
            else -> {
                // Search bar for many apps
                var searchQuery by remember { mutableStateOf("") }
                val filteredApps = if (searchQuery.isBlank()) supportedApps
                else supportedApps.filter {
                    it.displayName.contains(searchQuery, ignoreCase = true) ||
                    it.packageName.contains(searchQuery, ignoreCase = true)
                }

                if (supportedApps.size > 4) {
                    val muted = MaterialTheme.colorScheme.onSurfaceVariant
                    val searchInteraction = remember { MutableInteractionSource() }
                    val isSearchFocused by searchInteraction.collectIsFocusedAsState()
                    val searchBorder by animateColorAsState(
                        if (isSearchFocused) MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                        animationSpec = tween(150)
                    )

                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        interactionSource = searchInteraction,
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = mono,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(accents.primary),
                        modifier = Modifier
                            .widthIn(max = 260.dp)
                            .fillMaxWidth()
                            .height(32.dp)
                            .clip(RoundedCornerShape(corners.small))
                            .border(1.dp, searchBorder, RoundedCornerShape(corners.small)),
                        decorationBox = { innerTextField ->
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    tint = muted.copy(alpha = 0.55f),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(modifier = Modifier.weight(1f)) {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            "Filter apps…",
                                            fontSize = 11.sp,
                                            fontFamily = mono,
                                            color = muted.copy(alpha = 0.4f)
                                        )
                                    }
                                    innerTextField()
                                }
                                if (searchQuery.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(RoundedCornerShape(corners.small))
                                            .clickable { searchQuery = "" },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Clear,
                                            contentDescription = "Clear",
                                            tint = muted.copy(alpha = 0.5f),
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (filteredApps.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No matching apps",
                            fontSize = 11.sp,
                            fontFamily = mono,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        )
                    }
                    return@Column
                }

                // Horizontal scrolling cards
                val useScrolling = filteredApps.size > 4
                val cardsScrollState = rememberScrollState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (useScrolling) Modifier.horizontalScroll(cardsScrollState) else Modifier)
                        .height(IntrinsicSize.Max)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { focusManager.clearFocus() },
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    filteredApps.forEach { app ->
                        val url = app.apkDownloadUrl

                        Surface(
                            modifier = Modifier
                                .then(
                                    if (useScrolling) Modifier.width(170.dp)
                                    else Modifier.weight(1f)
                                )
                                .fillMaxHeight(),
                            shape = RoundedCornerShape(corners.small),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight()
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = app.displayName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (!isDefaultSource) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }

                                Text(
                                    text = if (app.recommendedVersion != null) "STABLE" else "ANY VERSION",
                                    fontSize = 9.sp,
                                    fontFamily = mono,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 1.2.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                )

                                if (url != null) {
                                    val pillInteraction = remember { MutableInteractionSource() }
                                    val isPillHovered by pillInteraction.collectIsHoveredAsState()
                                    val pillBg by animateColorAsState(
                                        if (isPillHovered) accents.primary.copy(alpha = 0.15f)
                                        else Color.Transparent,
                                        animationSpec = tween(150)
                                    )
                                    val pillBorder by animateColorAsState(
                                        if (isPillHovered) accents.primary.copy(alpha = 0.7f)
                                        else accents.primary.copy(alpha = 0.35f),
                                        animationSpec = tween(150)
                                    )

                                    Box(
                                        modifier = Modifier
                                            .hoverable(pillInteraction)
                                            .clip(RoundedCornerShape(corners.small))
                                            .background(pillBg, RoundedCornerShape(corners.small))
                                            .border(
                                                1.dp,
                                                pillBorder,
                                                RoundedCornerShape(corners.small)
                                            )
                                            .clickable {
                                                openUrlAndFollowRedirects(url) { resolved ->
                                                    uriHandler.openUri(resolved)
                                                }
                                            }
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Text(
                                            text = app.recommendedVersion?.let { "v$it ↗" } ?: "Download ↗",
                                            fontSize = 11.sp,
                                            fontFamily = mono,
                                            color = accents.primary,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (useScrolling && cardsScrollState.maxValue > 0) {
                    Spacer(Modifier.height(6.dp))
                    HorizontalScrollbar(
                        adapter = rememberScrollbarAdapter(cardsScrollState),
                        modifier = Modifier.fillMaxWidth(),
                        style = morpheScrollbarStyle()
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
//  DRAG OVERLAY
// ════════════════════════════════════════════════════════════════════

@Composable
private fun DragOverlay() {
    val accents = LocalMorpheAccents.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.92f))
            .border(
                width = 2.dp,
                color = accents.primary.copy(alpha = 0.5f),
                shape = RoundedCornerShape(0.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.CloudUpload,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = accents.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Drop APK here",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = accents.primary
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════
//  UTILITIES
// ════════════════════════════════════════════════════════════════════

private fun openFilePicker(): File? {
    val fileDialog = FileDialog(null as Frame?, "Select APK", FileDialog.LOAD).apply {
        isMultipleMode = false
        setFilenameFilter { _, name -> name.lowercase().let { it.endsWith(".apk") || it.endsWith(".apkm") || it.endsWith(".xapk") || it.endsWith(".apks") } }
        isVisible = true
    }
    val directory = fileDialog.directory
    val file = fileDialog.file
    return if (directory != null && file != null) File(directory, file) else null
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    }
}
