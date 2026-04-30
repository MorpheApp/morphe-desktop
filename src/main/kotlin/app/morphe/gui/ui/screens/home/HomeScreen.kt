/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.gui.ui.screens.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalUriHandler
import app.morphe.morphe_cli.generated.resources.Res
import app.morphe.morphe_cli.generated.resources.morphe_dark
import app.morphe.morphe_cli.generated.resources.morphe_light
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalThemeState
import app.morphe.gui.ui.theme.ThemePreference
import org.jetbrains.compose.resources.painterResource
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import app.morphe.gui.data.model.SupportedApp
import app.morphe.gui.ui.components.TopBarRow
import app.morphe.gui.ui.components.morpheScrollbarStyle
import app.morphe.gui.ui.screens.home.components.ApkInfoCard
import app.morphe.gui.ui.screens.home.components.FullScreenDropZone
import app.morphe.gui.ui.components.OfflineBanner
import app.morphe.gui.ui.screens.patches.PatchesScreen
import app.morphe.gui.ui.screens.patches.PatchSelectionScreen
import app.morphe.gui.util.DownloadUrlResolver.openUrlAndFollowRedirects
import app.morphe.gui.util.VersionStatus
import app.morphe.gui.util.resolveStatusColorType
import app.morphe.gui.util.resolveVersionWarningContent
import app.morphe.gui.util.toColor
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

class HomeScreen : Screen {

    @Composable
    override fun Content() {
        val viewModel = koinScreenModel<HomeViewModel>()
        HomeScreenContent(viewModel = viewModel)
    }
}

@Composable
fun HomeScreenContent(
    viewModel: HomeViewModel
) {
    val navigator = LocalNavigator.currentOrThrow
    val uiState by viewModel.uiState.collectAsState()

    val navStackSize = navigator.items.size
    LaunchedEffect(navStackSize) {
        viewModel.refreshPatchesIfNeeded()
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(
                message = error,
                duration = SnackbarDuration.Short
            )
            viewModel.clearError()
        }
    }

    // Full screen drop zone wrapper
    FullScreenDropZone(
        isDragHovering = uiState.isDragHovering,
        onDragHoverChange = { viewModel.setDragHover(it) },
        onFilesDropped = { viewModel.onFilesDropped(it) },
        enabled = !uiState.isAnalyzing
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
        ) {
            val useSplitLayout = maxWidth >= 720.dp
            val isCompact = maxWidth < 500.dp
            val isSmall = maxHeight < 600.dp
            val padding = if (isCompact) 16.dp else 24.dp
            val outerMaxWidth = maxWidth

            // Version warning dialog state
            var showVersionWarningDialog by remember { mutableStateOf(false) }

            if (showVersionWarningDialog && uiState.apkInfo != null) {
                VersionWarningDialog(
                    versionStatus = uiState.apkInfo!!.versionStatus,
                    currentVersion = uiState.apkInfo!!.versionName,
                    suggestedVersion = uiState.apkInfo!!.suggestedVersion ?: "",
                    onConfirm = {
                        showVersionWarningDialog = false
                        val patchesFile = viewModel.getCachedPatchesFile()
                        if (patchesFile != null && uiState.apkInfo != null) {
                            navigator.push(PatchSelectionScreen(
                                apkPath = uiState.apkInfo!!.filePath,
                                apkName = uiState.apkInfo!!.appName,
                                patchesFilePath = patchesFile.absolutePath,
                                packageName = uiState.apkInfo!!.packageName,
                                apkArchitectures = uiState.apkInfo!!.architectures
                            ))
                        }
                    },
                    onDismiss = { showVersionWarningDialog = false }
                )
            }

            val useHorizontalHeader = maxWidth >= 600.dp
            val pinSupportedAppsToBottom = useHorizontalHeader && maxHeight >= 760.dp
            val patchesLoaded = !uiState.isLoadingPatches && viewModel.getCachedPatchesFile() != null
            val onChangePatchesClick: () -> Unit = {
                navigator.push(PatchesScreen(
                    apkPath = uiState.apkInfo?.filePath ?: "",
                    apkName = uiState.apkInfo?.appName ?: "Select APK first"
                ))
            }
            val onRetry: () -> Unit = { viewModel.retryLoadPatches() }
            val onClearClick: () -> Unit = { viewModel.clearSelection() }
            val onChangeClick: () -> Unit = {
                openFilePicker()?.let { file ->
                    viewModel.onFileSelected(file)
                }
            }
            val onContinueClick: () -> Unit = {
                handleContinue(uiState, viewModel, navigator) {
                    showVersionWarningDialog = true
                }
            }

            val headerContent: @Composable ColumnScope.() -> Unit = {
                if (useHorizontalHeader) {
                    HeaderBar(
                        uiState = uiState,
                        isSmall = isSmall,
                        onChangePatchesClick = onChangePatchesClick,
                        onRetry = onRetry
                    )
                } else {
                    Spacer(modifier = Modifier.height(if (isSmall) 8.dp else 16.dp))
                    BrandingSection(isCompact = isCompact)

                    if (!uiState.isLoadingPatches && uiState.patchesVersion != null) {
                        Spacer(modifier = Modifier.height(if (isSmall) 8.dp else 12.dp))
                        PatchesVersionCard(
                            patchesVersion = uiState.patchesVersion!!,
                            isLatest = uiState.isUsingLatestPatches,
                            onChangePatchesClick = onChangePatchesClick,
                            patchSourceName = uiState.patchSourceName,
                            isCompact = isCompact,
                            modifier = Modifier
                                .padding(horizontal = if (isCompact) 8.dp else 16.dp)
                        )
                    } else if (uiState.isLoadingPatches) {
                        Spacer(modifier = Modifier.height(if (isSmall) 8.dp else 12.dp))
                        PatchesLoadingIndicator()
                    } else if (uiState.patchLoadError != null) {
                        Spacer(modifier = Modifier.height(if (isSmall) 8.dp else 12.dp))
                        PatchesVersionCard(
                            patchesVersion = "NOT LOADED",
                            isLatest = false,
                            onChangePatchesClick = onChangePatchesClick,
                            isCompact = isCompact,
                            modifier = Modifier
                                .padding(horizontal = if (isCompact) 8.dp else 16.dp)
                        )
                    }

                    if (uiState.isOffline && !uiState.isLoadingPatches) {
                        Spacer(modifier = Modifier.height(if (isSmall) 8.dp else 12.dp))
                        OfflineBanner(
                            onRetry = onRetry,
                            modifier = Modifier
                                .padding(horizontal = if (isCompact) 8.dp else 16.dp)
                        )
                    }
                }
            }

            val workspaceContent: @Composable (Modifier) -> Unit = { modifier ->
                Box(
                    modifier = modifier
                        .fillMaxWidth()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    MiddleContent(
                        uiState = uiState,
                        isCompact = isCompact,
                        patchesLoaded = patchesLoaded,
                        onClearClick = onClearClick,
                        onChangeClick = onChangeClick,
                        onContinueClick = onContinueClick
                    )
                }
            }

            val supportedAppsContent: @Composable () -> Unit = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(
                        start = padding,
                        end = padding,
                        bottom = if (isSmall) 8.dp else 16.dp
                    )
                ) {
                    SupportedAppsSection(
                        isCompact = isCompact,
                        maxWidth = this@BoxWithConstraints.maxWidth,
                        isLoading = uiState.isLoadingPatches,
                        isDefaultSource = uiState.isDefaultSource,
                        supportedApps = uiState.supportedApps,
                        loadError = uiState.patchLoadError,
                        onRetry = onRetry
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // ── Pinned header (not scrollable) ──
                    if (useHorizontalHeader) {
                        HeaderBar(
                            uiState = uiState,
                            isSmall = isSmall,
                            onChangePatchesClick = onChangePatchesClick,
                            onRetry = onRetry
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Spacer(modifier = Modifier.height(if (isSmall) 8.dp else 16.dp))
                            BrandingSection(isCompact = isCompact)

                            if (!uiState.isLoadingPatches && uiState.patchesVersion != null) {
                                Spacer(modifier = Modifier.height(if (isSmall) 8.dp else 12.dp))
                                PatchesVersionCard(
                                    patchesVersion = uiState.patchesVersion!!,
                                    isLatest = uiState.isUsingLatestPatches,
                                    onChangePatchesClick = onChangePatchesClick,
                                    isCompact = isCompact,
                                    modifier = Modifier
                                        .padding(horizontal = if (isCompact) 8.dp else 16.dp)
                                )
                            } else if (uiState.isLoadingPatches) {
                                Spacer(modifier = Modifier.height(if (isSmall) 8.dp else 12.dp))
                                PatchesLoadingIndicator()
                            } else if (uiState.patchLoadError != null) {
                                Spacer(modifier = Modifier.height(if (isSmall) 8.dp else 12.dp))
                                PatchesVersionCard(
                                    patchesVersion = "NOT LOADED",
                                    isLatest = false,
                                    onChangePatchesClick = onChangePatchesClick,
                                    isCompact = isCompact,
                                    modifier = Modifier
                                        .padding(horizontal = if (isCompact) 8.dp else 16.dp)
                                )
                            }

                            // Offline banner
                            if (uiState.isOffline && !uiState.isLoadingPatches) {
                                Spacer(modifier = Modifier.height(if (isSmall) 8.dp else 12.dp))
                                OfflineBanner(
                                    onRetry = onRetry,
                                    modifier = Modifier
                                        .widthIn(max = 400.dp)
                                        .padding(horizontal = if (isCompact) 8.dp else 16.dp)
                                )
                            }
                        }
                    }

                    // ── Scrollable body ──
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        val bodyMaxHeight = this.maxHeight
                        val scrollState = rememberScrollState()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(scrollState)
                                .heightIn(min = bodyMaxHeight),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = if (pinSupportedAppsToBottom) Arrangement.SpaceBetween else Arrangement.Top
                        ) {
                            // ── Main workspace area ──
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(padding),
                                contentAlignment = Alignment.Center
                            ) {
                                MiddleContent(
                                    uiState = uiState,
                                    isCompact = isCompact,
                                    patchesLoaded = patchesLoaded,
                                    onClearClick = onClearClick,
                                    onChangeClick = onChangeClick,
                                    onContinueClick = onContinueClick
                                )
                            }

                            // ── Supported apps ──
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(
                                    start = padding,
                                    end = padding,
                                    bottom = if (isSmall) 8.dp else 16.dp
                                )
                            ) {
                                SupportedAppsSection(
                                    isCompact = isCompact,
                                    maxWidth = outerMaxWidth,
                                    isLoading = uiState.isLoadingPatches,
                                    isDefaultSource = uiState.isDefaultSource,
                                    supportedApps = uiState.supportedApps,
                                    loadError = uiState.patchLoadError,
                                    onRetry = onRetry
                                )
                            }
                        }

                        // Show scrollbar only when content overflows
                        if (scrollState.maxValue > 0) {
                            VerticalScrollbar(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .fillMaxHeight(),
                                adapter = rememberScrollbarAdapter(scrollState),
                                style = morpheScrollbarStyle()
                            )
                        }
                    }
                }

                // Top bar — only floated when not using horizontal header
                if (!useHorizontalHeader) {
                    TopBarRow(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = padding, end = padding),
                        allowCacheClear = true
                    )
                }

                // Snackbar host
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )

                // Drag overlay
                if (uiState.isDragHovering) {
                    DragOverlay()
                }
            }
        }
    }
}

private fun handleContinue(
    uiState: HomeUiState,
    viewModel: HomeViewModel,
    navigator: cafe.adriel.voyager.navigator.Navigator,
    showWarning: () -> Unit
) {
    val patchesFile = viewModel.getCachedPatchesFile() ?: return
    val versionStatus = uiState.apkInfo?.versionStatus
    if (versionStatus != null && versionStatus != VersionStatus.LATEST_STABLE && versionStatus != VersionStatus.UNKNOWN) {
        showWarning()
    } else {
        uiState.apkInfo?.let { info ->
            navigator.push(PatchSelectionScreen(
                apkPath = info.filePath,
                apkName = info.appName,
                patchesFilePath = patchesFile.absolutePath,
                packageName = info.packageName,
                apkArchitectures = info.architectures
            ))
        }
    }
}

// ════════════════════════════════════════════════════════════════════
//  HEADER BAR — Logo + patches version + status, horizontal
// ════════════════════════════════════════════════════════════════════

@Composable
private fun HeaderBar(
    uiState: HomeUiState,
    isSmall: Boolean,
    onChangePatchesClick: () -> Unit,
    onRetry: () -> Unit
) {
    val mono = LocalMorpheFont.current
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.10f)
    val density = androidx.compose.ui.platform.LocalDensity.current
    var leadingWidthPx by remember { mutableIntStateOf(0) }
    var trailingWidthPx by remember { mutableIntStateOf(0) }
    val centerSidePadding = with(density) { maxOf(leadingWidthPx, trailingWidthPx).toDp() } + 16.dp

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
        // Logo — left-aligned, compact
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 12.dp)
                .onSizeChanged { leadingWidthPx = it.width }
        ) {
            BrandingSection(isCompact = true)
        }

        // Patches version inline — centered
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(start = centerSidePadding, end = centerSidePadding)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (!uiState.isLoadingPatches && uiState.patchesVersion != null) {
                    PatchesVersionInline(
                        patchesVersion = uiState.patchesVersion!!,
                        isLatest = uiState.isUsingLatestPatches,
                        onChangePatchesClick = onChangePatchesClick,
                        patchSourceName = uiState.patchSourceName
                    )
                } else if (uiState.isLoadingPatches) {
                    PatchesLoadingIndicator()
                } else if (uiState.patchLoadError != null) {
                    PatchesVersionInline(
                        patchesVersion = "NOT LOADED",
                        isLatest = false,
                        onChangePatchesClick = onChangePatchesClick
                    )
                }

                if (uiState.isOffline && !uiState.isLoadingPatches) {
                    Spacer(modifier = Modifier.width(12.dp))
                    OfflineBadge(onRetry = onRetry)
                }
            }
        }


        // Device indicator + settings — inline in the header
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp)
                .onSizeChanged { trailingWidthPx = it.width }
        ) {
            TopBarRow(allowCacheClear = true)
        }
    }
}

/**
 * Inline patches version for the header bar — compact, horizontal.
 */
@Composable
private fun PatchesVersionInline(
    patchesVersion: String,
    isLatest: Boolean,
    onChangePatchesClick: () -> Unit,
    patchSourceName: String? = null
) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()
    val borderColor by animateColorAsState(
        MaterialTheme.colorScheme.outline.copy(alpha = if (isHovered) 0.24f else 0.1f),
        animationSpec = tween(200)
    )

    Row(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(corners.small))
            .border(1.dp, borderColor, RoundedCornerShape(corners.small))
            .background(MaterialTheme.colorScheme.surface)
            .hoverable(hoverInteraction)
            .clickable(onClick = onChangePatchesClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = patchSourceName?.uppercase() ?: "PATCHES",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = mono,
            color = homeMutedTextColor(0.4f),
            letterSpacing = 1.5.sp
        )
        Text(
            text = " · ",
            fontSize = 10.sp,
            fontFamily = mono,
            color = homeMutedTextColor(0.25f)
        )
        Text(
            text = patchesVersion,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = mono,
            color = homeAccentTextColor(accents.primary)
        )
        if (isLatest) {
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .background(accents.secondary.copy(alpha = 0.1f), RoundedCornerShape(corners.small))
                    .border(1.dp, accents.secondary.copy(alpha = 0.2f), RoundedCornerShape(corners.small))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text(
                    text = "LATEST",
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

@Composable
private fun PatchesLoadingIndicator() {
    val mono = LocalMorpheFont.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Loading patches…",
            fontSize = 11.sp,
            fontFamily = mono,
            color = homeMutedTextColor(0.5f)
        )
    }
}

@Composable
private fun OfflineBadge(onRetry: () -> Unit) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()
    val borderColor by animateColorAsState(
        if (isHovered) MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
        else MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
        animationSpec = tween(200)
    )

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(corners.small))
            .border(1.dp, borderColor, RoundedCornerShape(corners.small))
            .hoverable(hoverInteraction)
            .clickable(onClick = onRetry)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(MaterialTheme.colorScheme.error, RoundedCornerShape(1.dp))
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "OFFLINE",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = mono,
            color = MaterialTheme.colorScheme.error,
            letterSpacing = 1.sp
        )
    }
}

// ════════════════════════════════════════════════════════════════════
//  MIDDLE CONTENT — Drop zone / APK info / Analyzing
// ════════════════════════════════════════════════════════════════════

@Composable
private fun MiddleContent(
    uiState: HomeUiState,
    isCompact: Boolean,
    patchesLoaded: Boolean,
    onClearClick: () -> Unit,
    onChangeClick: () -> Unit,
    onContinueClick: () -> Unit
) {
    when {
        uiState.isAnalyzing -> {
            AnalyzingSection(isCompact = isCompact)
        }
        uiState.apkInfo != null -> {
            ApkSelectedSection(
                patchesLoaded = patchesLoaded,
                apkInfo = uiState.apkInfo,
                isCompact = isCompact,
                onClearClick = onClearClick,
                onChangeClick = onChangeClick,
                onContinueClick = onContinueClick
            )
        }
        else -> {
            DropPromptSection(
                isDragHovering = uiState.isDragHovering,
                isCompact = isCompact,
                onBrowseClick = onChangeClick
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════
//  DROP ZONE — Corner brackets, scanner/targeting aesthetic
// ════════════════════════════════════════════════════════════════════

@Composable
private fun DropPromptSection(
    isDragHovering: Boolean,
    isCompact: Boolean = false,
    onBrowseClick: () -> Unit
) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val bracketColor = if (isDragHovering) accents.primary.copy(alpha = 0.72f)
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
    val bracketLen = if (isCompact) 24f else 32f

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .widthIn(max = 440.dp)
            .fillMaxWidth()
    ) {
        // Drop zone with corner brackets
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(if (isCompact) 1.6f else 1.4f)
                .drawBehind {
                    val strokeWidth = 2f
                    val len = bracketLen.dp.toPx()
                    val inset = 0f

                    // Top-left corner
                    drawLine(bracketColor, Offset(inset, inset), Offset(inset + len, inset), strokeWidth)
                    drawLine(bracketColor, Offset(inset, inset), Offset(inset, inset + len), strokeWidth)
                    // Top-right corner
                    drawLine(bracketColor, Offset(size.width - inset, inset), Offset(size.width - inset - len, inset), strokeWidth)
                    drawLine(bracketColor, Offset(size.width - inset, inset), Offset(size.width - inset, inset + len), strokeWidth)
                    // Bottom-left corner
                    drawLine(bracketColor, Offset(inset, size.height - inset), Offset(inset + len, size.height - inset), strokeWidth)
                    drawLine(bracketColor, Offset(inset, size.height - inset), Offset(inset, size.height - inset - len), strokeWidth)
                    // Bottom-right corner
                    drawLine(bracketColor, Offset(size.width - inset, size.height - inset), Offset(size.width - inset - len, size.height - inset), strokeWidth)
                    drawLine(bracketColor, Offset(size.width - inset, size.height - inset), Offset(size.width - inset, size.height - inset - len), strokeWidth)
                },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (isDragHovering) "RELEASE TO DROP" else "DROP APK HERE",
                    fontSize = if (isCompact) 16.sp else 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = mono,
                    color = if (isDragHovering) accents.primary
                            else MaterialTheme.colorScheme.onSurface,
                    letterSpacing = 3.sp
                )

                Spacer(modifier = Modifier.height(if (isCompact) 12.dp else 16.dp))

                Text(
                    text = "or",
                    fontSize = 11.sp,
                    fontFamily = mono,
                    color = homeMutedTextColor(0.3f)
                )

                Spacer(modifier = Modifier.height(if (isCompact) 12.dp else 16.dp))

                OutlinedButton(
                    onClick = onBrowseClick,
                    modifier = Modifier.height(if (isCompact) 38.dp else 42.dp),
                    shape = RoundedCornerShape(corners.small),
                    border = BorderStroke(1.dp, accents.primary.copy(alpha = 0.4f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = homeAccentTextColor(accents.primary))
                ) {
                    Text(
                        text = "BROWSE FILES",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = mono,
                        letterSpacing = 1.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = ".apk  ·  .apkm  ·  .xapk  ·  .apks",
                    fontSize = 10.sp,
                    fontFamily = mono,
                    color = homeMutedTextColor(0.25f),
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
//  APK SELECTED — Info card + action buttons
// ════════════════════════════════════════════════════════════════════

@Composable
private fun ApkSelectedSection(
    patchesLoaded: Boolean,
    apkInfo: ApkInfo,
    isCompact: Boolean,
    onClearClick: () -> Unit,
    onChangeClick: () -> Unit,
    onContinueClick: () -> Unit
) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val showWarning = apkInfo.versionStatus != VersionStatus.LATEST_STABLE &&
                      apkInfo.versionStatus != VersionStatus.UNKNOWN
    val accents = LocalMorpheAccents.current
    val warningColor = resolveStatusColorType(apkInfo.versionStatus, apkInfo.checksumStatus).toColor()
    val primaryColor = if (showWarning) warningColor else accents.primary

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.widthIn(max = 500.dp)
    ) {
        ApkInfoCard(
            apkInfo = apkInfo,
            onClearClick = onClearClick,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(if (isCompact) 16.dp else 20.dp))

        if (isCompact) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onContinueClick,
                    enabled = patchesLoaded,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                    shape = RoundedCornerShape(corners.small)
                ) {
                    ActionButtonContent(patchesLoaded, showWarning, mono)
                }
                OutlinedButton(
                    onClick = onChangeClick,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(corners.small),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text(
                        "CHANGE APK",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = mono,
                        letterSpacing = 1.sp
                    )
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onChangeClick,
                    modifier = Modifier.height(44.dp),
                    shape = RoundedCornerShape(corners.small),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text(
                        "CHANGE APK",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = mono,
                        letterSpacing = 1.sp
                    )
                }
                Button(
                    onClick = onContinueClick,
                    enabled = patchesLoaded,
                    modifier = Modifier.widthIn(min = 160.dp).height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                    shape = RoundedCornerShape(corners.small)
                ) {
                    ActionButtonContent(patchesLoaded, showWarning, mono)
                }
            }
        }
    }
}

@Composable
private fun ActionButtonContent(
    patchesLoaded: Boolean,
    showWarning: Boolean,
    mono: androidx.compose.ui.text.font.FontFamily
) {
    if (!patchesLoaded) {
        CircularProgressIndicator(
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onPrimary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            "LOADING…",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = mono,
            letterSpacing = 1.sp
        )
    } else {
        if (showWarning) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Warning",
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            "CONTINUE",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = mono,
            letterSpacing = 1.sp
        )
    }
}

// ════════════════════════════════════════════════════════════════════
//  ANALYZING STATE
// ════════════════════════════════════════════════════════════════════

@Composable
private fun AnalyzingSection(isCompact: Boolean = false) {
    val mono = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = if (isCompact) 16.dp else 32.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(if (isCompact) 28.dp else 32.dp),
            color = accents.primary,
            strokeWidth = 2.dp
        )

        Spacer(modifier = Modifier.height(if (isCompact) 12.dp else 16.dp))

        Text(
            text = "ANALYZING",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = mono,
            color = MaterialTheme.colorScheme.onSurface,
            letterSpacing = 2.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Reading app metadata…",
            fontSize = 11.sp,
            fontFamily = mono,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

// ════════════════════════════════════════════════════════════════════
//  SUPPORTED APPS — Bottom section, horizontal scrolling cards
// ════════════════════════════════════════════════════════════════════

/**
 * Bottom section — horizontal scrolling cards.
 */
@Composable
private fun SupportedAppsSection(
    isCompact: Boolean = false,
    maxWidth: Dp = 800.dp,
    isLoading: Boolean = false,
    isDefaultSource: Boolean = true,
    supportedApps: List<SupportedApp> = emptyList(),
    loadError: String? = null,
    onRetry: () -> Unit = {}
) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val useVerticalLayout = maxWidth < 400.dp

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "SUPPORTED APPS",
            fontSize = if (isCompact) 10.sp else 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = mono,
            color = homeMutedTextColor(0.7f),
            letterSpacing = 3.sp
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = if (isDefaultSource) "Download the exact version from APKMirror and drop it here."
                   else "Drop the APK for a supported app here.",
            fontSize = if (isCompact) 10.sp else 11.sp,
            fontFamily = mono,
            fontWeight = FontWeight.Normal,
            color = homeMutedTextColor(0.5f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(max = if (useVerticalLayout) 280.dp else 500.dp)
                .padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(if (isCompact) 12.dp else 16.dp))

        when {
            isLoading -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Loading patches...",
                        fontSize = 11.sp,
                        fontFamily = mono,
                        color = homeMutedTextColor(0.5f)
                    )
                }
            }
            loadError != null -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "LOAD FAILED",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = mono,
                        color = MaterialTheme.colorScheme.error,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = loadError,
                        fontSize = 11.sp,
                        fontFamily = mono,
                        color = homeMutedTextColor(0.6f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onRetry,
                        shape = RoundedCornerShape(corners.small),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                    ) {
                        Text(
                            "RETRY",
                            fontFamily = mono,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
            supportedApps.isEmpty() -> {
                Text(
                    text = "No supported apps found",
                    fontSize = 11.sp,
                    fontFamily = mono,
                    color = homeMutedTextColor(0.5f)
                )
            }
            else -> {
                val focusManager = LocalFocusManager.current
                var searchQuery by remember { mutableStateOf("") }
                val filteredApps = if (searchQuery.isBlank()) supportedApps
                else supportedApps.filter {
                    it.displayName.contains(searchQuery, ignoreCase = true) ||
                    it.packageName.contains(searchQuery, ignoreCase = true)
                }

                if (supportedApps.size > 4) {
                    if (isDefaultSource) {
                        // Default search field for Morphe-source patches.
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = {
                                Text(
                                    "Filter apps…",
                                    fontSize = 11.sp,
                                    fontFamily = mono,
                                    color = homeMutedTextColor(0.4f)
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    tint = homeMutedTextColor(0.6f),
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(
                                            Icons.Default.Clear,
                                            contentDescription = "Clear",
                                            tint = homeMutedTextColor(0.5f),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = mono,
                                fontSize = 11.sp
                            ),
                            shape = RoundedCornerShape(corners.small),
                            modifier = Modifier
                                .widthIn(max = 260.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                                cursorColor = accents.primary
                            )
                        )
                    } else {
                        // Slim, elongated search field for third-party patches.
                        // Uses BasicTextField + a custom decoration so we can break
                        // out of OutlinedTextField's 56dp minimum height.
                        SlimSearchField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            mono = mono,
                            corners = corners,
                            accents = accents
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                var selectedApp by remember { mutableStateOf<SupportedApp?>(null) }
                // Clear selection if the selected app is filtered out
                LaunchedEffect(searchQuery, filteredApps) {
                    if (selectedApp != null && filteredApps.none { it.packageName == selectedApp?.packageName }) {
                        selectedApp = null
                    }
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
                            color = homeMutedTextColor(0.3f)
                        )
                    }
                } else {
                    SupportedAppsMasterDetail(
                        apps = filteredApps,
                        selectedApp = selectedApp,
                        onSelect = { app ->
                            selectedApp = if (selectedApp?.packageName == app.packageName) null else app
                        },
                        onClose = { selectedApp = null },
                        isDefaultSource = isDefaultSource,
                        useVerticalLayout = useVerticalLayout,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = if (isCompact) 8.dp else 16.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { focusManager.clearFocus() }
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
//  SHARED COMPONENTS
// ════════════════════════════════════════════════════════════════════

@Composable
private fun BrandingSection(isCompact: Boolean = false) {
    val themeState = LocalThemeState.current
    val isDark = when (themeState.current) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        else -> themeState.current.isDark()
    }
    Image(
        painter = painterResource(if (isDark) Res.drawable.morphe_dark else Res.drawable.morphe_light),
        contentDescription = "Morphe Logo",
        modifier = Modifier.height(if (isCompact) 36.dp else 60.dp)
    )
}

@Composable
private fun homeMutedTextColor(alpha: Float): Color {
    return MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
}

@Composable
private fun homeAccentTextColor(accent: Color): Color {
    return accent
}

@Composable
private fun PatchesVersionCard(
    patchesVersion: String,
    isLatest: Boolean,
    onChangePatchesClick: () -> Unit,
    patchSourceName: String? = null,
    isCompact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()
    val borderColor by animateColorAsState(
        if (isHovered) accents.primary.copy(alpha = 0.4f)
        else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
        animationSpec = tween(200)
    )

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(corners.medium))
                .border(1.dp, borderColor, RoundedCornerShape(corners.medium))
                .background(MaterialTheme.colorScheme.surface)
                .hoverable(hoverInteraction)
                .clickable(onClick = onChangePatchesClick)
        ) {
            // Source name + version + badge — single row
            Row(
                modifier = Modifier
                    .padding(vertical = if (isCompact) 8.dp else 10.dp)
                    .padding(start = 12.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
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
                    fontSize = if (isCompact) 12.sp else 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = mono,
                    color = accents.primary
                )
                if (isLatest) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(accents.secondary.copy(alpha = 0.1f), RoundedCornerShape(corners.small))
                            .border(1.dp, accents.secondary.copy(alpha = 0.2f), RoundedCornerShape(corners.small))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "LATEST",
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
}

@Composable
private fun VersionWarningDialog(
    versionStatus: VersionStatus,
    currentVersion: String,
    suggestedVersion: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val warningContent = resolveVersionWarningContent(versionStatus, currentVersion, suggestedVersion)
    val warnColor = warningContent.colorType.toColor()
    val title = warningContent.title
    val message = warningContent.message

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(corners.medium),
        containerColor = MaterialTheme.colorScheme.surface,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = warnColor,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontFamily = mono,
                fontSize = 14.sp,
                letterSpacing = 1.sp
            )
        },
        text = {
            Text(
                text = message,
                fontFamily = mono,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = warnColor),
                shape = RoundedCornerShape(corners.small)
            ) {
                Text(
                    "CONTINUE ANYWAY",
                    fontFamily = mono,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(corners.small)
            ) {
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

/**
 * Renders the supported apps as a centered, horizontally-scrolling row of
 * vertical cards. The selected card expands its own width to reveal more
 * detail without becoming a separate panel.
 */
@Composable
private fun SupportedAppsMasterDetail(
    apps: List<SupportedApp>,
    selectedApp: SupportedApp?,
    onSelect: (SupportedApp) -> Unit,
    onClose: () -> Unit,
    isDefaultSource: Boolean,
    useVerticalLayout: Boolean,
    modifier: Modifier = Modifier
) {
    val cardSpacing = 10.dp

    BoxWithConstraints(modifier = modifier) {
        val parentWidth = maxWidth
        val scrollState = rememberScrollState()

        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .horizontalScroll(scrollState)
                    .widthIn(min = parentWidth)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(cardSpacing, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.Top
            ) {
                apps.forEach { app ->
                    SupportedAppVerticalCard(
                        app = app,
                        isSelected = app.packageName == selectedApp?.packageName,
                        onClick = { onSelect(app) },
                        isDefaultSource = isDefaultSource
                    )
                }
            }

            if (scrollState.maxValue > 0) {
                Spacer(Modifier.height(6.dp))
                HorizontalScrollbar(
                    adapter = rememberScrollbarAdapter(scrollState),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    style = morpheScrollbarStyle()
                )
            }
        }
    }
}

/**
 * Single morphing card for a supported app. The card shows badge, name,
 * both versions and a download button at all times. When selected, it
 * expands its own width (same height) to reveal extended details on the
 * right — there is no separate panel; the card itself grows.
 *
 * For third-party (non-Morphe) patch sources we hide the experimental row
 * and download buttons (no Morphe API URL exists for them) and shrink the
 * card height accordingly.
 */
@Composable
private fun SupportedAppVerticalCard(
    app: SupportedApp,
    isSelected: Boolean,
    onClick: () -> Unit,
    isDefaultSource: Boolean,
    modifier: Modifier = Modifier
) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current

    // ── Dimensions ──
    val collapsedWidth = 188.dp
    val expandedExtraWidth = 320.dp
    val cardHeight = if (isDefaultSource) 250.dp else 190.dp

    // ── Animations ──
    val animatedExtraWidth by animateDpAsState(
        targetValue = if (isSelected) expandedExtraWidth else 0.dp,
        animationSpec = tween(durationMillis = 340, easing = FastOutSlowInEasing),
        label = "extraWidth"
    )

    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()

    val backgroundColor by animateColorAsState(
        when {
            isSelected -> accents.primary.copy(alpha = 0.08f)
            isHovered -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            else -> MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(200),
        label = "cardBg"
    )
    val borderColor by animateColorAsState(
        when {
            isSelected -> accents.primary.copy(alpha = 0.6f)
            isHovered -> MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
        },
        animationSpec = tween(200),
        label = "cardBorder"
    )

    val initial = app.displayName.firstOrNull()?.uppercase() ?: "?"
    val hasExperimental = app.experimentalVersions.isNotEmpty()
    val latestExperimental = app.experimentalVersions.firstOrNull()
    val otherStable = app.supportedVersions.filter { it != app.recommendedVersion }
    val downloadUrl = app.apkDownloadUrl

    // The whole card is one Row sharing a single border. The left section is
    // always visible and clickable; the right section is conditionally rendered
    // with an animated width.
    Row(
        modifier = modifier
            .height(cardHeight)
            .clip(RoundedCornerShape(corners.medium))
            .border(1.dp, borderColor, RoundedCornerShape(corners.medium))
            .background(backgroundColor)
    ) {
        // ════════════════════════════════════════════════
        //  LEFT: always-visible card content
        // ════════════════════════════════════════════════
        Column(
            modifier = Modifier
                .width(collapsedWidth)
                .fillMaxHeight()
                .hoverable(hoverInteraction)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App name
            Text(
                text = app.displayName,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Initial badge
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .border(
                        1.dp,
                        accents.primary.copy(alpha = if (isSelected) 0.7f else 0.4f),
                        RoundedCornerShape(corners.small)
                    )
                    .background(
                        accents.primary.copy(alpha = if (isSelected) 0.15f else 0.06f),
                        RoundedCornerShape(corners.small)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = mono,
                    color = accents.primary
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Stable: "STABLE LATEST" + version button
            VersionWithDownload(
                channelLabel = "STABLE LATEST",
                channelColor = accents.primary,
                version = app.recommendedVersion,
                downloadUrl = downloadUrl,
                mono = mono,
                corners = corners,
                nullLabel = "Any version"
            )

            // Experimental row only for default (Morphe) patch sources.
            // Third-party patches don't get experimental support here.
            if (isDefaultSource) {
                Spacer(modifier = Modifier.height(12.dp))
                VersionWithDownload(
                    channelLabel = "EXPERIMENTAL LATEST",
                    channelColor = accents.warning,
                    version = latestExperimental,
                    downloadUrl = if (hasExperimental) app.experimentalDownloadUrl else null,
                    mono = mono,
                    corners = corners
                )
            }
        }

        // ════════════════════════════════════════════════
        //  RIGHT: expanded detail (animated width)
        // ════════════════════════════════════════════════
        if (animatedExtraWidth > 0.dp) {
            // Internal vertical divider connecting the two halves of the same card
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(borderColor)
            )

            Column(
                modifier = Modifier
                    .width((animatedExtraWidth - 1.dp).coerceAtLeast(0.dp))
                    .fillMaxHeight()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                // ── Package name + close ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = app.packageName,
                        fontSize = 10.sp,
                        fontFamily = mono,
                        color = homeMutedTextColor(0.55f),
                        letterSpacing = 0.3.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))

                    val closeHover = remember { MutableInteractionSource() }
                    val isCloseHovered by closeHover.collectIsHoveredAsState()
                    val closeBg by animateColorAsState(
                        if (isCloseHovered) MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                        else Color.Transparent,
                        animationSpec = tween(150),
                        label = "closeBg"
                    )
                    val closeBorderColor by animateColorAsState(
                        if (isCloseHovered) MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f),
                        animationSpec = tween(150),
                        label = "closeBorder"
                    )
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .hoverable(closeHover)
                            .clip(RoundedCornerShape(corners.small))
                            .background(closeBg, RoundedCornerShape(corners.small))
                            .border(1.dp, closeBorderColor, RoundedCornerShape(corners.small))
                            .clickable(onClick = onClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Close",
                            tint = if (isCloseHovered) MaterialTheme.colorScheme.error
                                   else homeMutedTextColor(0.55f),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ── ALSO STABLE tags ──
                Text(
                    text = "ALSO STABLE",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = mono,
                    color = accents.primary.copy(alpha = 0.85f),
                    letterSpacing = 1.2.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                if (otherStable.isNotEmpty()) {
                    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        otherStable.take(8).forEach { version ->
                            VersionPill(
                                version = version,
                                color = accents.primary,
                                mono = mono,
                                corners = corners
                            )
                        }
                        if (otherStable.size > 8) {
                            Text(
                                text = "+${otherStable.size - 8}",
                                fontSize = 10.sp,
                                fontFamily = mono,
                                color = homeMutedTextColor(0.5f),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                            )
                        }
                    }
                } else {
                    Text(
                        text = "none",
                        fontSize = 10.sp,
                        fontFamily = mono,
                        color = homeMutedTextColor(0.35f)
                    )
                }

                if (isDefaultSource) {
                    Spacer(modifier = Modifier.height(14.dp))

                    // ── EXPERIMENTAL tags (Morphe-source patches only) ──
                    Text(
                        text = "EXPERIMENTAL",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = mono,
                        color = accents.warning.copy(alpha = 0.85f),
                        letterSpacing = 1.2.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    if (app.experimentalVersions.isNotEmpty()) {
                        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            app.experimentalVersions.take(8).forEach { version ->
                                VersionPill(
                                    version = version,
                                    color = accents.warning,
                                    mono = mono,
                                    corners = corners
                                )
                            }
                            if (app.experimentalVersions.size > 8) {
                                Text(
                                    text = "+${app.experimentalVersions.size - 8}",
                                    fontSize = 10.sp,
                                    fontFamily = mono,
                                    color = homeMutedTextColor(0.5f),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "none",
                            fontSize = 10.sp,
                            fontFamily = mono,
                            color = homeMutedTextColor(0.35f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * A small "channel label + version button" used inside the collapsed card.
 * The label tells the user which channel (e.g. STABLE LATEST), and the button
 * below shows the version string with an open-in-new icon, doubling as the
 * download link to that specific version's page.
 */
@Composable
private fun VersionWithDownload(
    channelLabel: String,
    channelColor: Color,
    version: String?,
    downloadUrl: String?,
    mono: androidx.compose.ui.text.font.FontFamily,
    corners: app.morphe.gui.ui.theme.MorpheCornerStyle,
    nullLabel: String = "—"
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = channelLabel,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = mono,
            color = channelColor.copy(alpha = 0.85f),
            letterSpacing = 1.2.sp
        )

//        Spacer(modifier = Modifier.height(3.dp))

        if (downloadUrl != null) {
            val uriHandler = LocalUriHandler.current
            OutlinedButton(
                onClick = {
                    openUrlAndFollowRedirects(downloadUrl) { urlResolved ->
                        uriHandler.openUri(urlResolved)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(28.dp),
                shape = RoundedCornerShape(corners.small),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = channelColor),
                border = BorderStroke(1.dp, channelColor.copy(alpha = 0.4f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = version?.let { "v$it" } ?: nullLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = mono,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        } else {
            // No download URL — show the version as plain text. Either the
            // version is genuinely absent (e.g. no experimental version exists,
            // version == null) in which case we render a faint placeholder, or
            // this is a third-party patch source where we don't provide download
            // links but the version is real and should look like the primary
            // information on the card.
            Text(
                text = version?.let { "v$it" } ?: nullLabel,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = mono,
                color = if (version != null) channelColor
                        else channelColor.copy(alpha = 0.3f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 0.dp)
            )
        }
    }
}

/**
 * Slim, elongated search field used when third-party patches are loaded.
 * Built on BasicTextField so we can drop below the 56dp minimum height that
 * Material 3's OutlinedTextField enforces internally. Visually mirrors the
 * default OutlinedTextField (border, leading search icon, trailing clear,
 * mono placeholder), just thinner and wider.
 */
@Composable
private fun SlimSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    mono: androidx.compose.ui.text.font.FontFamily,
    corners: app.morphe.gui.ui.theme.MorpheCornerStyle,
    accents: app.morphe.gui.ui.theme.MorpheAccentColors
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderColor by animateColorAsState(
        if (isFocused) MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
        animationSpec = tween(150),
        label = "slimSearchBorder"
    )

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        interactionSource = interactionSource,
        textStyle = MaterialTheme.typography.bodySmall.copy(
            fontFamily = mono,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface
        ),
        cursorBrush = SolidColor(accents.primary),
        modifier = Modifier
            .widthIn(max = 360.dp)
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(corners.small))
            .border(1.dp, borderColor, RoundedCornerShape(corners.small)),
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
                    if (value.isEmpty()) {
                        Text(
                            "Filter apps…",
                            fontSize = 11.sp,
                            fontFamily = mono,
                            color = muted.copy(alpha = 0.4f)
                        )
                    }
                    innerTextField()
                }
                if (value.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(RoundedCornerShape(corners.small))
                            .clickable { onValueChange("") },
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
}

/**
 * Small version pill used inside the expanded card section.
 */
@Composable
private fun VersionPill(
    version: String,
    color: Color,
    mono: androidx.compose.ui.text.font.FontFamily,
    corners: app.morphe.gui.ui.theme.MorpheCornerStyle
) {
    Box(
        modifier = Modifier
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(corners.small))
            .background(color.copy(alpha = 0.05f), RoundedCornerShape(corners.small))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = "v$version",
            fontSize = 10.sp,
            fontFamily = mono,
            fontWeight = FontWeight.Medium,
            color = color.copy(alpha = 0.85f)
        )
    }
}

// ════════════════════════════════════════════════════════════════════
//  DRAG OVERLAY
// ════════════════════════════════════════════════════════════════════

@Composable
private fun DragOverlay() {
    val mono = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val bracketColor = accents.primary.copy(alpha = 0.6f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.9f))
            .drawBehind {
                val strokeWidth = 3f
                val len = 48.dp.toPx()
                val inset = 24.dp.toPx()

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
            Text(
                text = "DROP APK",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = mono,
                color = accents.primary,
                letterSpacing = 6.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = ".apk  ·  .apkm  ·  .xapk  ·  .apks",
                fontSize = 11.sp,
                fontFamily = mono,
                color = accents.primary.copy(alpha = 0.4f),
                letterSpacing = 1.sp
            )
        }
    }
}

private fun openFilePicker(): File? {
    val fileDialog = FileDialog(null as Frame?, "Select APK File", FileDialog.LOAD).apply {
        isMultipleMode = false
        setFilenameFilter { _, name -> name.lowercase().let { it.endsWith(".apk") || it.endsWith(".apkm") || it.endsWith(".xapk") || it.endsWith(".apks") } }
        isVisible = true
    }

    val directory = fileDialog.directory
    val file = fileDialog.file

    return if (directory != null && file != null) {
        File(directory, file)
    } else {
        null
    }
}
