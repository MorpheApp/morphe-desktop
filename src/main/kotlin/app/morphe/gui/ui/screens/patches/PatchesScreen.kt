/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.gui.ui.screens.patches

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import app.morphe.engine.model.Release
import org.koin.core.parameter.parametersOf
import cafe.adriel.voyager.koin.koinScreenModel
import app.morphe.gui.ui.components.ErrorDialog
import app.morphe.gui.ui.components.DeviceIndicator
import app.morphe.gui.ui.components.SettingsButton
import app.morphe.gui.ui.components.morpheScrollbarStyle
import app.morphe.gui.ui.components.getErrorType
import app.morphe.gui.ui.components.getFriendlyErrorMessage
import app.morphe.gui.ui.components.OfflineBanner
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.MorpheColors
import app.morphe.gui.ui.theme.LocalMorpheFont
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/**
 * Screen for selecting patch version to apply.
 * This is the screen that selects the patches.mpp file
 */
data class PatchesScreen(
    val apkPath: String,
    val apkName: String
) : Screen {

    @Composable
    override fun Content() {
        val viewModel = koinScreenModel<PatchesViewModel> { parametersOf(apkPath, apkName) }
        PatchesScreenContent(viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatchesScreenContent(viewModel: PatchesViewModel) {
    val corners = LocalMorpheCorners.current
    val navigator = LocalNavigator.currentOrThrow
    val uiState by viewModel.uiState.collectAsState()
    val mono = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current

    var showErrorDialog by remember { mutableStateOf(false) }
    var currentError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            currentError = error
            showErrorDialog = true
        }
    }

    // Error dialog
    if (showErrorDialog && currentError != null) {
        ErrorDialog(
            title = "Error",
            message = getFriendlyErrorMessage(currentError!!),
            errorType = getErrorType(currentError!!),
            onDismiss = {
                showErrorDialog = false
                viewModel.clearError()
            },
            onRetry = {
                showErrorDialog = false
                viewModel.clearError()
                viewModel.loadReleases()
            }
        )
    }

    val dividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // ── Header bar ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawLine(
                        color = dividerColor,
                        start = Offset(0f, size.height),
                        end = Offset(size.width, size.height),
                        strokeWidth = 1f
                    )
                }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Back button
            val backHover = remember { MutableInteractionSource() }
            val isBackHovered by backHover.collectIsHoveredAsState()
            val backBorder by animateColorAsState(
                if (isBackHovered) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                animationSpec = tween(150)
            )

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .hoverable(backHover)
                    .clip(RoundedCornerShape(corners.small))
                    .border(1.dp, backBorder, RoundedCornerShape(corners.small))
                    .clickable { navigator.pop() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Title block
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "SELECT PATCHES",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = mono,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = 1.5.sp,
                    lineHeight = 14.sp
                )
                if (viewModel.getApkName().isNotBlank()) {
                    Text(
                        text = viewModel.getApkName(),
                        fontSize = 10.sp,
                        fontFamily = mono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        letterSpacing = 0.3.sp,
                        lineHeight = 8.sp
                    )
                }
            }

            // Actions
            val refreshHover = remember { MutableInteractionSource() }
            val isRefreshHovered by refreshHover.collectIsHoveredAsState()
            val refreshBorder by animateColorAsState(
                MaterialTheme.colorScheme.outline.copy(alpha = if (isRefreshHovered) 0.24f else 0.1f),
                animationSpec = tween(150)
            )

            if (!uiState.isLocalSource) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .hoverable(refreshHover)
                        .clip(RoundedCornerShape(corners.small))
                        .border(1.dp, refreshBorder, RoundedCornerShape(corners.small))
                        .then(
                            if (!uiState.isLoading) Modifier.clickable { viewModel.loadReleases() }
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = if (uiState.isLoading) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
            }

            DeviceIndicator()
            Spacer(modifier = Modifier.width(6.dp))
            SettingsButton(allowCacheClear = true)
        }

        // ── Content area ──
        Column(modifier = Modifier.fillMaxSize()) {
            // Local source banner
            if (uiState.isLocalSource) {
                LocalSourceBanner(
                    patchFile = uiState.downloadedPatchFile,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                // Channel selector
                if (!uiState.isOffline) {
                    ChannelSelector(
                        selectedChannel = uiState.selectedChannel,
                        onChannelSelected = { viewModel.setChannel(it) },
                        stableCount = uiState.stableReleases.size,
                        devCount = uiState.devReleases.size,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }

                // Offline banner
                if (uiState.isOffline && uiState.currentReleases.isNotEmpty()) {
                    OfflineBanner(
                        onRetry = { viewModel.loadReleases() },
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 0.dp)
                    )
                }
            }

            when {
                uiState.isLocalSource -> {
                    Spacer(modifier = Modifier.weight(1f))
                }
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "FETCHING RELEASES",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = mono,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                letterSpacing = 2.sp
                            )
                        }
                    }
                }
                uiState.currentReleases.isEmpty() && !uiState.isLoading -> {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "NO RELEASES FOUND",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = mono,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                letterSpacing = 1.5.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = { viewModel.loadReleases() },
                                shape = RoundedCornerShape(corners.small),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
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
                }
                else -> {
                    // Releases list
                    val releasesListState = rememberLazyListState()
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        LazyColumn(
                            state = releasesListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val latestStableTag = uiState.stableReleases.firstOrNull()?.tagName
                            val latestDevTag = uiState.devReleases.firstOrNull()?.tagName
                            items(
                                items = uiState.currentReleases,
                                key = { it.tagName }
                            ) { release ->
                                ReleaseCard(
                                    release = release,
                                    isSelected = release.tagName == uiState.selectedRelease?.tagName,
                                    isDownloaded = release.tagName in uiState.cachedReleaseVersions,
                                    isOffline = uiState.isOffline,
                                    isLatest = release.tagName == latestStableTag ||
                                               release.tagName == latestDevTag,
                                    onClick = { viewModel.selectRelease(release) }
                                )
                            }
                        }

                        VerticalScrollbar(
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                            adapter = rememberScrollbarAdapter(releasesListState),
                            style = morpheScrollbarStyle()
                        )
                    }

                    // Bottom action bar
                    BottomActionBar(
                        uiState = uiState,
                        onDownloadClick = { viewModel.downloadPatches() },
                        onSelectClick = {
                            viewModel.confirmSelection()
                            navigator.pop()
                        },
                        onExportJsonClick = {
                            val fileDialog = FileDialog(null as Frame?, "Export Options JSON", FileDialog.SAVE).apply {
                                file = "options.json"
                                isVisible = true
                            }
                            val directory = fileDialog.directory
                            val file = fileDialog.file
                            if (directory != null && file != null) {
                                viewModel.exportOptionsJson(File(directory, file))
                            }
                        }
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
//  CHANNEL SELECTOR
// ═══════════════════════════════════════════════════════════════════

@Composable
private fun ChannelSelector(
    selectedChannel: ReleaseChannel,
    onChannelSelected: (ReleaseChannel) -> Unit,
    stableCount: Int,
    devCount: Int,
    modifier: Modifier = Modifier
) {
    val accents = LocalMorpheAccents.current

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ChannelChip(
            label = "STABLE",
            count = stableCount,
            isSelected = selectedChannel == ReleaseChannel.STABLE,
            onClick = { onChannelSelected(ReleaseChannel.STABLE) },
            accentColor = accents.primary,
            modifier = Modifier.weight(1f)
        )
        ChannelChip(
            label = "DEV",
            count = devCount,
            isSelected = selectedChannel == ReleaseChannel.DEV,
            onClick = { onChannelSelected(ReleaseChannel.DEV) },
            accentColor = accents.primary,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ChannelChip(
    label: String,
    count: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()

    val borderColor by animateColorAsState(
        when {
            isSelected -> accentColor.copy(alpha = 0.5f)
            isHovered -> accentColor.copy(alpha = 0.3f)
            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
        },
        animationSpec = tween(150)
    )
    val bgColor = if (isSelected) accentColor.copy(alpha = 0.08f) else Color.Transparent

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corners.small))
            .border(1.dp, borderColor, RoundedCornerShape(corners.small))
            .background(bgColor)
            .hoverable(hoverInteraction)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Selection dot
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(accentColor, RoundedCornerShape(1.dp))
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                fontFamily = mono,
                color = if (isSelected) accentColor else MaterialTheme.colorScheme.onSurface,
                letterSpacing = 1.5.sp
            )
            if (count > 0) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$count",
                    fontSize = 10.sp,
                    fontFamily = mono,
                    color = if (isSelected) accentColor.copy(alpha = 0.6f)
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
//  RELEASE CARD
// ════════════════════════════════════════════════════════════════════

@Composable
private fun ReleaseCard(
    release: Release,
    isSelected: Boolean,
    isDownloaded: Boolean,
    isOffline: Boolean = false,
    isLatest: Boolean = false,
    onClick: () -> Unit
) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val selectedColor = accents.primary
    val downloadedColor = accents.secondary
    val accentColor = when {
        isSelected -> selectedColor
        isDownloaded -> downloadedColor
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    var isExpanded by remember { mutableStateOf(false) }
    val hasNotes = !release.body.isNullOrBlank()

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val borderColor by animateColorAsState(
        when {
            isSelected -> accentColor.copy(alpha = 0.5f)
            isDownloaded -> downloadedColor.copy(alpha = 0.32f)
            isHovered -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
        },
        animationSpec = tween(150)
    )

    val bgColor = when {
        isSelected -> selectedColor.copy(alpha = 0.07f)
        isDownloaded -> downloadedColor.copy(alpha = 0.045f)
        else -> MaterialTheme.colorScheme.surface
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corners.medium))
            .border(1.dp, borderColor, RoundedCornerShape(corners.medium))
            .background(bgColor)
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Left accent stripe
            if (isSelected || isDownloaded) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(accentColor)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = release.tagName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = mono,
                                color = when {
                                    isSelected -> selectedColor
                                    isDownloaded -> downloadedColor
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                            if (isLatest) {
                                val latestColor = accents.secondary
                                Box(
                                    modifier = Modifier
                                        .background(latestColor.copy(alpha = 0.12f), RoundedCornerShape(corners.small))
                                        .border(1.dp, latestColor.copy(alpha = 0.32f), RoundedCornerShape(corners.small))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "LATEST",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = mono,
                                        color = latestColor,
                                        letterSpacing = 1.sp
                                    )
                                }
                            }
                            if (release.isDevRelease()) {
                                Box(
                                    modifier = Modifier
                                        .background(accents.primary.copy(alpha = 0.1f), RoundedCornerShape(corners.small))
                                        .border(1.dp, accents.primary.copy(alpha = 0.22f), RoundedCornerShape(corners.small))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "DEV",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = mono,
                                        color = accents.primary,
                                        letterSpacing = 1.sp
                                    )
                                }
                            }
                            if (isDownloaded) {
                                Box(
                                    modifier = Modifier
                                        .background(downloadedColor.copy(alpha = 0.1f), RoundedCornerShape(corners.small))
                                        .border(1.dp, downloadedColor.copy(alpha = 0.24f), RoundedCornerShape(corners.small))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "CACHED",
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = mono,
                                        color = downloadedColor,
                                        letterSpacing = 1.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Patch file info
                        release.assets.find { it.isPatchFile() }?.let { patchAsset ->
                            Text(
                                text = "${patchAsset.name} (${patchAsset.getFormattedSize()})",
                                fontSize = 11.sp,
                                fontFamily = mono,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                letterSpacing = 0.3.sp
                            )
                        }

                        val formattedDate = release.publishedAt?.let { formatDate(it) } ?: ""
                        if (formattedDate.isNotEmpty()) {
                            Text(
                                text = "${if (isOffline) "Cached:" else "Published:"} $formattedDate",
                                fontSize = 10.sp,
                                fontFamily = mono,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                        }

                        if (hasNotes) {
                            Spacer(modifier = Modifier.height(6.dp))
                            val noteHover = remember { MutableInteractionSource() }
                            val isNoteHovered by noteHover.collectIsHoveredAsState()
                            val noteBorder by animateColorAsState(
                                if (isNoteHovered) accentColor.copy(alpha = 0.3f)
                                else accentColor.copy(alpha = 0.15f),
                                animationSpec = tween(150)
                            )

                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(corners.small))
                                    .border(1.dp, noteBorder, RoundedCornerShape(corners.small))
                                    .hoverable(noteHover)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { isExpanded = !isExpanded }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = if (isExpanded) "HIDE NOTES" else "PATCH NOTES",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = mono,
                                    color = accentColor,
                                    letterSpacing = 0.5.sp
                                )
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                // Expandable release notes
                if (isExpanded && hasNotes) {
                    val notesDividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.06f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(1.dp)
                            .background(notesDividerColor)
                    )
                    FormattedReleaseNotes(
                        markdown = release.body.orEmpty(),
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
//  RELEASE NOTES
// ════════════════════════════════════════════════════════════════════

@Composable
private fun FormattedReleaseNotes(markdown: String, modifier: Modifier = Modifier) {
    val mono = LocalMorpheFont.current
    val lines = parseMarkdown(markdown)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        lines.forEach { line ->
            when (line) {
                is MdLine.Header -> Text(
                    text = line.text,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = mono,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = 0.5.sp
                )
                is MdLine.SubHeader -> Text(
                    text = line.text,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = mono,
                    color = MaterialTheme.colorScheme.onSurface
                )
                is MdLine.Bullet -> {
                    Row {
                        Text(
                            text = "·  ",
                            fontSize = 11.sp,
                            fontFamily = mono,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        )
                        Text(
                            text = line.text,
                            fontSize = 11.sp,
                            fontFamily = mono,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            lineHeight = 17.sp
                        )
                    }
                }
                is MdLine.Plain -> Text(
                    text = line.text,
                    fontSize = 11.sp,
                    fontFamily = mono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    lineHeight = 17.sp
                )
            }
        }
    }
}

private sealed class MdLine {
    data class Header(val text: String) : MdLine()
    data class SubHeader(val text: String) : MdLine()
    data class Bullet(val text: String) : MdLine()
    data class Plain(val text: String) : MdLine()
}

private fun parseMarkdown(markdown: String): List<MdLine> {
    return markdown.lines()
        .filter { it.isNotBlank() }
        .map { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("# ") -> MdLine.Header(cleanMarkdown(trimmed.removePrefix("# ")))
                trimmed.startsWith("## ") -> MdLine.Header(cleanMarkdown(trimmed.removePrefix("## ")))
                trimmed.startsWith("### ") -> MdLine.SubHeader(cleanMarkdown(trimmed.removePrefix("### ")))
                trimmed.startsWith("* ") -> MdLine.Bullet(cleanMarkdown(trimmed.removePrefix("* ")))
                trimmed.startsWith("- ") -> MdLine.Bullet(cleanMarkdown(trimmed.removePrefix("- ")))
                else -> MdLine.Plain(cleanMarkdown(trimmed))
            }
        }
}

/**
 * Strip markdown syntax to plain readable text:
 * - **bold** → bold
 * - [text](url) → text
 * - ([hash](url)) → remove entirely (commit refs)
 */
private fun cleanMarkdown(text: String): String {
    var result = text
    // Remove commit refs like ([abc1234](https://...))
    result = result.replace(Regex("""\(\[[\da-f]{7,}]\([^)]*\)\)"""), "")
    // [text](url) → text
    result = result.replace(Regex("""\[([^\]]*?)]\([^)]*\)"""), "$1")
    // **bold** → bold
    result = result.replace(Regex("""\*\*(.+?)\*\*"""), "$1")
    // Clean up extra whitespace
    result = result.replace(Regex("""\s+"""), " ").trim()
    return result
}

// ════════════════════════════════════════════════════════════════════
//  BOTTOM ACTION BAR
// ════════════════════════════════════════════════════════════════════

@Composable
private fun BottomActionBar(
    uiState: PatchesUiState,
    onDownloadClick: () -> Unit,
    onSelectClick: () -> Unit,
    onExportJsonClick: () -> Unit,
) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val dividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = dividerColor,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1f
                )
            }
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        // Download progress
        if (uiState.isDownloading) {
            LinearProgressIndicator(
                progress = { uiState.downloadProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.dp)),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "DOWNLOADING…",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = mono,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                letterSpacing = 1.5.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (uiState.downloadedPatchFile == null) {
                // Download button
                Button(
                    onClick = onDownloadClick,
                    enabled = uiState.selectedRelease != null && !uiState.isDownloading,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accents.primary),
                    shape = RoundedCornerShape(corners.small)
                ) {
                    Text(
                        text = if (uiState.isDownloading) "DOWNLOADING…" else "DOWNLOAD",
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = mono,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp
                    )
                }
            } else {
                // Select button
                Button(
                    onClick = onSelectClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accents.primary),
                    shape = RoundedCornerShape(corners.small)
                ) {
                    Text(
                        text = "SELECT",
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = mono,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp
                    )
                }

                // Export JSON
                if (uiState.isExporting) {
                    Box(
                        modifier = Modifier.height(44.dp).width(44.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            strokeWidth = 2.dp
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = onExportJsonClick,
                        modifier = Modifier.height(44.dp),
                        shape = RoundedCornerShape(corners.small),
                        border = BorderStroke(1.dp, accents.primary.copy(alpha = 0.3f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = accents.primary)
                    ) {
                        Text(
                            text = "EXPORT JSON",
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = mono,
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
//  LOCAL SOURCE BANNER
// ════════════════════════════════════════════════════════════════════

@Composable
private fun LocalSourceBanner(
    patchFile: File?,
    modifier: Modifier = Modifier
) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corners.medium))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.16f), RoundedCornerShape(corners.medium))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // Left accent stripe
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(accents.primary)
            )

            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = accents.primary,
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = "LOCAL PATCH FILE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = mono,
                        color = accents.primary,
                        letterSpacing = 1.5.sp
                    )
                    if (patchFile != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = patchFile.name,
                            fontSize = 11.sp,
                            fontFamily = mono,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            letterSpacing = 0.3.sp
                        )
                    }
                }
            }
        }
    }
}

private fun formatDate(isoDate: String): String {
    return try {
        // Takes "2024-01-15T10:30:00Z" and returns "Jan 15, 2024 at 10:30 AM"
        val datePart = isoDate.substringBefore("T")
        val timePart = isoDate.substringAfter("T").substringBefore("Z").substringBefore("+")
        val parts = datePart.split("-")
        if (parts.size == 3) {
            val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
            val month = months.getOrElse(parts[1].toInt() - 1) { "???" }
            val day = parts[2].toInt()
            val year = parts[0]
            val timeParts = timePart.split(":")
            val timeStr = if (timeParts.size >= 2) {
                val hour = timeParts[0].toInt()
                val minute = timeParts[1]
                val amPm = if (hour >= 12) "PM" else "AM"
                val hour12 = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
                " at $hour12:$minute $amPm UTC"
            } else ""
            "$month $day, $year$timeStr"
        } else {
            datePart
        }
    } catch (e: Exception) {
        isoDate
    }
}
