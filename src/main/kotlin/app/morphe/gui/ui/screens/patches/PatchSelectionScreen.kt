package app.morphe.gui.ui.screens.patches

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.PlaylistRemove
import androidx.compose.material.icons.filled.RemoveDone
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import app.morphe.gui.data.model.Patch
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import app.morphe.gui.ui.components.ErrorDialog
import app.morphe.gui.ui.components.DeviceIndicator
import app.morphe.gui.ui.components.MorpheSwitch
import app.morphe.gui.ui.components.SettingsButton
import app.morphe.gui.ui.components.morpheScrollbarStyle
import app.morphe.gui.ui.components.getErrorType
import app.morphe.gui.ui.components.getFriendlyErrorMessage
import app.morphe.gui.ui.screens.patching.PatchingScreen
import app.morphe.gui.data.repository.ConfigRepository
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.util.DeviceMonitor
import java.awt.FileDialog
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File

/**
 * Screen for selecting which patches to apply.
 * This screen is the one that selects which patch options need to be applied. Eg: Custom Branding, Spoof App Version, etc.
 */
data class PatchSelectionScreen(
    val apkPath: String,
    val apkName: String,
    /** Primary .mpp file path. Always non-null. In multi-source mode, the first
     *  enabled source's file. Used for legacy/single-source code paths and as
     *  the default when [patchesFilePaths] is empty. */
    val patchesFilePath: String,
    val packageName: String,
    val apkArchitectures: List<String> = emptyList(),
    /** All enabled-source .mpp file paths. Single-element in single-source mode.
     *  Used by the patching pipeline to feed the engine the union of patches. */
    val patchesFilePaths: List<String> = emptyList(),
    /** Parallel to [patchesFilePaths] — display name per source. Drives badging
     *  in the patch list. Empty disables badging (legacy single-source). */
    val patchSourceNames: List<String> = emptyList(),
) : Screen {

    @Composable
    override fun Content() {
        val effectiveList = patchesFilePaths.takeIf { it.isNotEmpty() } ?: listOf(patchesFilePath)
        val viewModel = koinScreenModel<PatchSelectionViewModel> {
            parametersOf(apkPath, apkName, patchesFilePath, packageName, apkArchitectures, effectiveList, patchSourceNames)
        }
        PatchSelectionScreenContent(viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatchSelectionScreenContent(viewModel: PatchSelectionViewModel) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val navigator = LocalNavigator.currentOrThrow
    val configRepository: ConfigRepository = koinInject()
    val uiState by viewModel.uiState.collectAsState()

    // Load keystore config for CLI preview
    var keystorePath by remember { mutableStateOf<String?>(null) }
    var keystorePassword by remember { mutableStateOf<String?>(null) }
    var keystoreAlias by remember { mutableStateOf<String?>(null) }
    var keystoreEntryPassword by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val config = configRepository.loadConfig()
        keystorePath = config.resolvedKeystorePath()?.absolutePath
        keystorePassword = config.keystorePassword
        keystoreAlias = config.keystoreAlias
        keystoreEntryPassword = config.keystoreEntryPassword
    }

    var showErrorDialog by remember { mutableStateOf(false) }
    var currentError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            currentError = error
            showErrorDialog = true
        }
    }

    if (showErrorDialog && currentError != null) {
        ErrorDialog(
            title = "Error Loading Patches",
            message = getFriendlyErrorMessage(currentError!!),
            errorType = getErrorType(currentError!!),
            onDismiss = {
                showErrorDialog = false
                viewModel.clearError()
            },
            onRetry = {
                showErrorDialog = false
                viewModel.clearError()
                viewModel.loadPatches()
            }
        )
    }

    var cleanMode by remember { mutableStateOf(false) }
    var showCommandPreview by remember { mutableStateOf(false) }
    var continueOnError by remember { mutableStateOf(false) }

    val dividerColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)

    Column(modifier = Modifier.fillMaxSize()) {
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
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                Text(
                    text = "SELECT PATCHES",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = mono,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = 1.5.sp,
                    lineHeight = 14.sp
                )
                Text(
                    text = "${uiState.selectedCount} of ${uiState.totalCount} selected",
                    fontSize = 10.sp,
                    fontFamily = mono,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    letterSpacing = 0.3.sp,
                    lineHeight = 8.sp
                )
            }

            // Command preview toggle
            if (!uiState.isLoading && uiState.allPatches.isNotEmpty()) {
                val cmdHover = remember { MutableInteractionSource() }
                val isCmdHovered by cmdHover.collectIsHoveredAsState()
                val cmdActive = showCommandPreview
                val cmdBorder by animateColorAsState(
                    when {
                        cmdActive -> accents.secondary.copy(alpha = 0.5f)
                        isCmdHovered -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                    },
                    animationSpec = tween(150)
                )

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .hoverable(cmdHover)
                        .clip(RoundedCornerShape(corners.small))
                        .border(1.dp, cmdBorder, RoundedCornerShape(corners.small))
                        .then(
                            if (cmdActive) Modifier.background(
                                accents.secondary.copy(alpha = 0.08f),
                                RoundedCornerShape(corners.small)
                            ) else Modifier
                        )
                        .clickable { showCommandPreview = !showCommandPreview },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = "Command Preview",
                        tint = if (cmdActive) accents.secondary
                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Continue on error toggle
                val errHover = remember { MutableInteractionSource() }
                val isErrHovered by errHover.collectIsHoveredAsState()
                val errBorder by animateColorAsState(
                    when {
                        continueOnError -> MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                        isErrHovered -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                    },
                    animationSpec = tween(150)
                )

                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
                    tooltip = {
                        PlainTooltip {
                            Text(
                                "Continue patching even if a patch fails",
                                fontFamily = mono,
                                fontSize = 11.sp
                            )
                        }
                    },
                    state = rememberTooltipState()
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .hoverable(errHover)
                            .clip(RoundedCornerShape(corners.small))
                            .border(1.dp, errBorder, RoundedCornerShape(corners.small))
                            .then(
                                if (continueOnError) Modifier.background(
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
                                    RoundedCornerShape(corners.small)
                                ) else Modifier
                            )
                            .clickable { continueOnError = !continueOnError },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlaylistRemove,
                            contentDescription = "Continue on error",
                            tint = if (continueOnError) MaterialTheme.colorScheme.error
                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))
            }

            DeviceIndicator()
            Spacer(modifier = Modifier.width(6.dp))
            SettingsButton(
                allowCacheClear = false,
                onDismiss = { viewModel.refreshStripLibsStatus() }
            )
        }

        // Command preview — collapsible
        if (!uiState.isLoading && uiState.allPatches.isNotEmpty()) {
            val commandPreview = remember(uiState.selectedPatches, uiState.stripLibsStatus, cleanMode, continueOnError, keystorePath) {
                viewModel.getCommandPreview(cleanMode, continueOnError, keystorePath, keystorePassword, keystoreAlias, keystoreEntryPassword)
            }
            AnimatedVisibility(
                visible = showCommandPreview,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                CommandPreview(
                    command = commandPreview,
                    cleanMode = cleanMode,
                    onToggleMode = { cleanMode = !cleanMode },
                    onCopy = {
                        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                        clipboard.setContents(StringSelection(commandPreview), null)
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // Search bar
        PatchSearchBar(
            query = uiState.searchQuery,
            onQueryChange = { viewModel.setSearchQuery(it) },
            showOnlySelected = uiState.showOnlySelected,
            onShowOnlySelectedChange = { viewModel.setShowOnlySelected(it) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )

        // Global selection-mode chips: only meaningful when there's exactly
        // ONE bundle. Multi-bundle moves these chips INTO each bundle box
        // so each source can be managed independently. The deprecated
        // applySaved/applyDefaults/selectAll/deselectAll methods loop over
        // bundles — for a single bundle, they're equivalent to the per-
        // bundle methods.
        val isSingleBundle = uiState.bundles.size == 1
        AnimatedVisibility(
            visible = !uiState.isLoading && isSingleBundle && uiState.bundles.firstOrNull()?.patches?.isNotEmpty() == true,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            val activeBundleId = uiState.bundles.firstOrNull()?.bundleId
            SelectionModeChips(
                hasSavedSelection = uiState.hasSavedSelection,
                activeMode = activeBundleId?.let { uiState.selectionModeFor(it) } ?: SelectionMode.CUSTOM,
                onApplySaved = {
                    activeBundleId?.let { viewModel.applySavedDefaultsInBundle(it) }
                },
                onApplyDefaults = {
                    activeBundleId?.let { viewModel.applyPatchDefaultsInBundle(it) }
                },
                onApplyAll = {
                    activeBundleId?.let { viewModel.selectAllInBundle(it) }
                },
                onApplyNone = {
                    activeBundleId?.let { viewModel.deselectAllInBundle(it) }
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            color = accents.primary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "LOADING PATCHES",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = mono,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            letterSpacing = 1.5.sp
                        )
                    }
                }
            }

            // Global empty state — when EVERY loaded bundle has zero patches
            // compatible with this APK. None of the enabled sources contribute
            // anything for this app's package; rendering empty bundle boxes
            // would be pure noise.
            !uiState.isLoading && uiState.bundles.all { it.patches.isEmpty() } -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (uiState.bundles.isEmpty()) "No patches found"
                               else "None of your enabled sources have patches for this app",
                        fontSize = 12.sp,
                        fontFamily = mono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }

            // Global "no matches for search" empty state — only fires when
            // EVERY bundle that HAS patches has been filtered to empty by
            // the active search. Bundles with 0 patches for this app are
            // hidden separately above, so we only consider non-empty sources.
            uiState.searchQuery.isNotBlank() && run {
                val nonEmptySourceIds = uiState.bundles
                    .filter { it.patches.isNotEmpty() }
                    .map { it.bundleId }.toSet()
                uiState.filteredBundles
                    .filter { it.bundleId in nonEmptySourceIds }
                    .all { it.patches.isEmpty() }
            } -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No patches match your search",
                        fontSize = 12.sp,
                        fontFamily = mono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }

            else -> {
                // Patch list — single-bundle renders flat (no box chrome),
                // multi-bundle renders per-bundle collapsible boxes.
                val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()

                // Expand/collapse state for multi-bundle, keyed by bundleId.
                // Default: all bundles expanded. Uses plain `remember` — state
                // resets if the user backs out and re-enters the screen, which
                // is acceptable since "show me everything" is the right default.
                val collapsedBundles = remember { mutableStateListOf<String>() }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val showBanner = uiState.stripLibsStatus !is StripLibsStatus.NoNativeLibs
                        if (showBanner) {
                            item(key = "strip_libs_banner") {
                                StripLibsStatusBanner(status = uiState.stripLibsStatus)
                            }
                        }

                        if (isSingleBundle) {
                            // ── Flat rendering (single bundle, no chrome) ──
                            val bundle = uiState.filteredBundles.firstOrNull() ?: return@LazyColumn
                            val bundleId = bundle.bundleId
                            val selectedInBundle = uiState.selectedByBundle[bundleId].orEmpty()
                            items(
                                items = bundle.patches,
                                key = { it.uniqueId }
                            ) { patch ->
                                PatchListItem(
                                    patch = patch,
                                    isSelected = selectedInBundle.contains(patch.uniqueId),
                                    onToggle = { viewModel.togglePatch(bundleId, patch.uniqueId) },
                                    sourceName = null,
                                    getOptionValue = { optionKey, default ->
                                        viewModel.getOptionValue(patch.name, optionKey, default)
                                    },
                                    onOptionValueChange = { optionKey, value ->
                                        viewModel.setOptionValue(patch.name, optionKey, value)
                                    }
                                )
                            }
                        } else {
                            // ── Per-bundle collapsible boxes (multi-bundle) ──
                            // Hide bundles whose pre-filter patches list is empty
                            // (i.e. the bundle has NO patches compatible with this
                            // APK at all). Bundles that loaded patches but are
                            // currently empty due to an active search still
                            // render — their box shows "no matches in this bundle".
                            val bundlesById = uiState.bundles.associateBy { it.bundleId }
                            val visibleBundles = uiState.filteredBundles.filter { fb ->
                                bundlesById[fb.bundleId]?.patches?.isNotEmpty() == true
                            }
                            visibleBundles.forEach { bundle ->
                                item(key = "bundle-${bundle.bundleId}") {
                                    BundleBox(
                                        bundle = bundle,
                                        selectedInBundle = uiState.selectedByBundle[bundle.bundleId].orEmpty(),
                                        selectionMode = uiState.selectionModeFor(bundle.bundleId),
                                        hasSavedForBundle = uiState.savedSelectedByBundle?.containsKey(bundle.bundleId) == true,
                                        expanded = bundle.bundleId !in collapsedBundles,
                                        searchActive = uiState.searchQuery.isNotBlank(),
                                        onExpandToggle = {
                                            if (bundle.bundleId in collapsedBundles) collapsedBundles.remove(bundle.bundleId)
                                            else collapsedBundles.add(bundle.bundleId)
                                        },
                                        onTogglePatch = { patchId -> viewModel.togglePatch(bundle.bundleId, patchId) },
                                        onSelectAll = { viewModel.selectAllInBundle(bundle.bundleId) },
                                        onDeselectAll = { viewModel.deselectAllInBundle(bundle.bundleId) },
                                        onApplyDefaults = { viewModel.applyPatchDefaultsInBundle(bundle.bundleId) },
                                        onApplySaved = { viewModel.applySavedDefaultsInBundle(bundle.bundleId) },
                                        getOptionValue = { patchName, optionKey, default ->
                                            viewModel.getOptionValue(patchName, optionKey, default)
                                        },
                                        onOptionValueChange = { patchName, optionKey, value ->
                                            viewModel.setOptionValue(patchName, optionKey, value)
                                        },
                                    )
                                }
                            }
                        }
                    }

                    androidx.compose.foundation.VerticalScrollbar(
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                        adapter = androidx.compose.foundation.rememberScrollbarAdapter(lazyListState),
                        style = morpheScrollbarStyle()
                    )
                }

                // ── Bottom action bar ──
                Box(
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
                        .padding(16.dp)
                ) {
                    val patchHover = remember { MutableInteractionSource() }
                    val isPatchHovered by patchHover.collectIsHoveredAsState()
                    val patchEnabled = uiState.selectedPatches.isNotEmpty()
                    val patchBg by animateColorAsState(
                        when {
                            !patchEnabled -> accents.primary.copy(alpha = 0.1f)
                            isPatchHovered -> accents.primary.copy(alpha = 0.9f)
                            else -> accents.primary
                        },
                        animationSpec = tween(150)
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .hoverable(patchHover)
                            .clip(RoundedCornerShape(corners.small))
                            .background(patchBg, RoundedCornerShape(corners.small))
                            .then(
                                if (patchEnabled) Modifier.clickable {
                                    val config = viewModel.createPatchConfig(continueOnError)
                                    navigator.push(PatchingScreen(config))
                                } else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "PATCH (${uiState.selectedCount})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = mono,
                            color = if (patchEnabled) Color.White
                                   else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                            letterSpacing = 1.5.sp
                        )
                    }
                }
            }
        }
    }
}

// ── Search Bar ──

@Composable
private fun PatchSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    showOnlySelected: Boolean,
    onShowOnlySelectedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Custom compact search field
        val searchFocused = remember { mutableStateOf(false) }
        val searchBorderColor by animateColorAsState(
            if (searchFocused.value) accents.primary.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
            animationSpec = tween(150)
        )

        Row(
            modifier = Modifier
                .weight(1f)
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
                        "Search patches…",
                        fontSize = 11.sp,
                        fontFamily = mono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    )
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 12.sp,
                        fontFamily = mono,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(accents.primary),
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

        // "Selected" filter chip
        val chipHover = remember { MutableInteractionSource() }
        val isChipHovered by chipHover.collectIsHoveredAsState()
        val chipBorder by animateColorAsState(
            when {
                showOnlySelected -> accents.primary.copy(alpha = 0.5f)
                isChipHovered -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
            },
            animationSpec = tween(150)
        )

        Box(
            modifier = Modifier
                .height(38.dp)
                .hoverable(chipHover)
                .clip(RoundedCornerShape(corners.small))
                .border(1.dp, chipBorder, RoundedCornerShape(corners.small))
                .then(
                    if (showOnlySelected) Modifier.background(
                        accents.primary.copy(alpha = 0.08f),
                        RoundedCornerShape(corners.small)
                    ) else Modifier
                )
                .clickable { onShowOnlySelectedChange(!showOnlySelected) }
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (showOnlySelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = accents.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    text = "SELECTED",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = mono,
                    color = if (showOnlySelected) accents.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

// ── Patch List Item ──

@Composable
private fun PatchListItem(
    patch: Patch,
    isSelected: Boolean,
    onToggle: () -> Unit,
    sourceName: String? = null,
    getOptionValue: (optionKey: String, default: String?) -> String = { _, d -> d ?: "" },
    onOptionValueChange: (optionKey: String, value: String) -> Unit = { _, _ -> }
) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val borderColor by animateColorAsState(
        when {
            isSelected && isHovered -> accents.primary.copy(alpha = 0.4f)
            isSelected -> accents.primary.copy(alpha = 0.2f)
            isHovered -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
        },
        animationSpec = tween(150)
    )

    var showOptions by remember { mutableStateOf(false) }
    val hasOptions = patch.options.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corners.small))
            .border(1.dp, borderColor, RoundedCornerShape(corners.small))
            .then(
                if (isSelected) Modifier.background(
                    accents.primary.copy(alpha = 0.04f),
                    RoundedCornerShape(corners.small)
                ) else Modifier
            )
            .hoverable(interactionSource)
    ) {
        // Header — clicking toggles patch
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(interactionSource = interactionSource, indication = null, onClick = onToggle)
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Custom checkbox
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(corners.small))
                    .border(
                        1.5.dp,
                        if (isSelected) accents.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        RoundedCornerShape(corners.small)
                    )
                    .then(
                        if (isSelected) Modifier.background(accents.primary, RoundedCornerShape(corners.small))
                        else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                // Name + app chips on same line
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = patch.name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = mono,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (sourceName != null) {
                        Box(
                            modifier = Modifier
                                .border(
                                    1.dp,
                                    accents.primary.copy(alpha = 0.3f),
                                    RoundedCornerShape(corners.small)
                                )
                                .background(
                                    accents.primary.copy(alpha = 0.06f),
                                    RoundedCornerShape(corners.small)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = sourceName.uppercase(),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = mono,
                                letterSpacing = 0.5.sp,
                                color = accents.primary,
                                maxLines = 1,
                            )
                        }
                    }

                    if (patch.compatiblePackages.isNotEmpty()) {
                        val genericSegments = setOf("com", "org", "net", "android", "google", "apps", "app", "www")
                        patch.compatiblePackages.take(2).forEach { pkg ->
                            val displayName = pkg.displayName?.takeIf { it.isNotBlank() } ?: run {
                                val meaningful = pkg.name.split(".").filter { it !in genericSegments }
                                meaningful.takeLast(2).joinToString(" ")
                                    .replaceFirstChar { it.uppercase() }
                            }
                            Box(
                                modifier = Modifier
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.1f),
                                        RoundedCornerShape(corners.small)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = displayName,
                                    fontSize = 9.sp,
                                    fontFamily = mono,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    letterSpacing = 0.3.sp
                                )
                            }
                        }
                    }
                }

                if (patch.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = patch.description,
                        fontSize = 11.sp,
                        fontFamily = mono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Gear button for options
            if (hasOptions) {
                val gearHover = remember { MutableInteractionSource() }
                val isGearHovered by gearHover.collectIsHoveredAsState()
                val gearBorder by animateColorAsState(
                    when {
                        showOptions -> accents.secondary.copy(alpha = 0.5f)
                        isGearHovered -> accents.secondary.copy(alpha = 0.3f)
                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                    },
                    animationSpec = tween(150)
                )
                val gearBg by animateColorAsState(
                    if (showOptions) accents.secondary.copy(alpha = 0.08f)
                    else Color.Transparent,
                    animationSpec = tween(150)
                )

                // Wrapper box — no clip, allows badge to overflow
                Box(
                    modifier = Modifier.size(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Gear button
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .hoverable(gearHover)
                            .clip(RoundedCornerShape(corners.small))
                            .border(1.dp, gearBorder, RoundedCornerShape(corners.small))
                            .background(gearBg, RoundedCornerShape(corners.small))
                            .clickable { showOptions = !showOptions },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Configure options",
                            tint = when {
                                showOptions -> accents.secondary
                                isGearHovered -> accents.secondary.copy(alpha = 0.7f)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            },
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    // Options count badge — outside clip
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 3.dp, y = (-3).dp)
                            .size(18.dp)
                            .background(accents.secondary, RoundedCornerShape(9.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${patch.options.size}",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = mono,
                            color = Color.White,
                            lineHeight = 9.sp
                        )
                    }
                }
            }
        }

        // Expandable options section
        if (hasOptions) {
            val optionDivider = MaterialTheme.colorScheme.outline.copy(alpha = 0.06f)

            AnimatedVisibility(
                visible = showOptions,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .drawBehind {
                            drawLine(
                                color = optionDivider,
                                start = Offset(14.dp.toPx(), 0f),
                                end = Offset(size.width - 14.dp.toPx(), 0f),
                                strokeWidth = 1f
                            )
                        }
                        .padding(start = 14.dp, end = 14.dp, bottom = 10.dp, top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    patch.options.forEach { option ->
                        PatchOptionEditor(
                            option = option,
                            value = getOptionValue(option.key, option.default),
                            onValueChange = { onOptionValueChange(option.key, it) }
                        )
                    }
                }
            }
        }
    }
}

// ── Patch Option Editor ──

@Composable
private fun PatchOptionEditor(
    option: app.morphe.gui.data.model.PatchOption,
    value: String,
    onValueChange: (String) -> Unit
) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = option.title.ifBlank { option.key },
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = mono,
                color = accents.secondary
            )
            if (option.required) {
                Text(
                    text = "*",
                    fontSize = 11.sp,
                    fontFamily = mono,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        if (option.description.isNotBlank()) {
            Text(
                text = option.description,
                fontSize = 10.sp,
                fontFamily = mono,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        when (option.type) {
            app.morphe.gui.data.model.PatchOptionType.BOOLEAN -> {
                var localChecked by remember(option.key) { mutableStateOf(value.equals("true", ignoreCase = true)) }
                LaunchedEffect(value) {
                    val v = value.equals("true", ignoreCase = true)
                    if (localChecked != v) localChecked = v
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MorpheSwitch(
                        checked = localChecked,
                        onCheckedChange = { newChecked ->
                            localChecked = newChecked
                            onValueChange(newChecked.toString())
                        },
                        accentColor = accents.secondary
                    )
                    Text(
                        text = if (localChecked) "Enabled" else "Disabled",
                        fontSize = 10.sp,
                        fontFamily = mono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
            app.morphe.gui.data.model.PatchOptionType.FILE -> {
                var localPath by remember(option.key) { mutableStateOf(value) }
                LaunchedEffect(value) {
                    if (localPath != value) localPath = value
                }

                // Detect if this is an image file option from key/title
                val keyLower = option.key.lowercase() + " " + option.title.lowercase()
                val isImage = keyLower.contains("icon") || keyLower.contains("image") ||
                    keyLower.contains("logo") || keyLower.contains("banner") ||
                    keyLower.contains("png") || keyLower.contains("jpg")
                val fileFilterDesc = if (isImage) "Image files" else "All files"
                val fileExtensions = if (isImage) "png,jpg,jpeg,webp" else "*"

                val fieldFocused = remember { mutableStateOf(false) }
                val fieldBorder by animateColorAsState(
                    if (fieldFocused.value) accents.secondary.copy(alpha = 0.6f)
                    else accents.secondary.copy(alpha = 0.2f),
                    animationSpec = tween(150)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Path text field
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(corners.small))
                            .border(1.dp, fieldBorder, RoundedCornerShape(corners.small))
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            if (localPath.isEmpty()) {
                                Text(
                                    text = if (isImage) "Select image…" else "Select file…",
                                    fontSize = 11.sp,
                                    fontFamily = mono,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                )
                            }
                            androidx.compose.foundation.text.BasicTextField(
                                value = localPath,
                                onValueChange = { newPath ->
                                    localPath = newPath
                                    onValueChange(newPath)
                                },
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(
                                    fontSize = 11.sp,
                                    fontFamily = mono,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(accents.secondary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { fieldFocused.value = it.isFocused }
                            )
                        }
                    }

                    // Browse button
                    val browseHover = remember { MutableInteractionSource() }
                    val isBrowseHovered by browseHover.collectIsHoveredAsState()
                    val browseBorder by animateColorAsState(
                        if (isBrowseHovered) accents.secondary.copy(alpha = 0.5f)
                        else accents.secondary.copy(alpha = 0.2f),
                        animationSpec = tween(150)
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .hoverable(browseHover)
                            .clip(RoundedCornerShape(corners.small))
                            .border(1.dp, browseBorder, RoundedCornerShape(corners.small))
                            .clickable {
                                val dialog = FileDialog(null as java.awt.Frame?, fileFilterDesc, FileDialog.LOAD)
                                if (isImage) {
                                    // setFile pattern works on macOS; setFilenameFilter works on Linux/Windows
                                    dialog.file = "*.png;*.jpg;*.jpeg;*.webp"
                                    dialog.setFilenameFilter { _, name ->
                                        val lower = name.lowercase()
                                        lower.endsWith(".png") || lower.endsWith(".jpg") ||
                                            lower.endsWith(".jpeg") || lower.endsWith(".webp")
                                    }
                                }
                                dialog.isVisible = true
                                val selected = dialog.file
                                if (selected != null) {
                                    val fullPath = File(dialog.directory, selected).absolutePath
                                    localPath = fullPath
                                    onValueChange(fullPath)
                                }
                            }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "BROWSE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = mono,
                            color = if (isBrowseHovered) accents.secondary else accents.secondary.copy(alpha = 0.7f),
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
            else -> {
                var localText by remember(option.key) { mutableStateOf(value) }
                LaunchedEffect(value) {
                    if (localText != value) localText = value
                }

                val fieldFocused = remember { mutableStateOf(false) }
                val fieldBorder by animateColorAsState(
                    if (fieldFocused.value) accents.secondary.copy(alpha = 0.6f)
                    else accents.secondary.copy(alpha = 0.2f),
                    animationSpec = tween(150)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .clip(RoundedCornerShape(corners.small))
                        .border(1.dp, fieldBorder, RoundedCornerShape(corners.small))
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (localText.isEmpty()) {
                            Text(
                                text = option.default ?: option.type.name.lowercase(),
                                fontSize = 11.sp,
                                fontFamily = mono,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                        }
                        androidx.compose.foundation.text.BasicTextField(
                            value = localText,
                            onValueChange = { newText ->
                                localText = newText
                                onValueChange(newText)
                            },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(
                                fontSize = 11.sp,
                                fontFamily = mono,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(accents.secondary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { fieldFocused.value = it.isFocused }
                        )
                    }
                }
            }
        }
    }
}

// ── Default Disabled Info Card ──

/**
 * Quick-action chip row above the patch list. Each chip is a one-click preset that
 * sets the current selection. The chip whose state matches the current selection
 * gets highlighted (accent border + tint) so the user can see at a glance what
 * preset they're on.
 */
@Composable
private fun SelectionModeChips(
    hasSavedSelection: Boolean,
    activeMode: SelectionMode,
    onApplySaved: () -> Unit,
    onApplyDefaults: () -> Unit,
    onApplyAll: () -> Unit,
    onApplyNone: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // SAVED is computed by overlaying the saved-selection check on top of CUSTOM —
        // when hasSavedSelection is true AND the current selection matches the saved
        // bundle, we treat it as SAVED. The VM only knows ALL/DEFAULTS/NONE/CUSTOM, so
        // we approximate: if hasSavedSelection is true and activeMode is CUSTOM, the
        // user could still be on their saved set. We can't tell here without the
        // bundle; for now SAVED highlights only when activeMode == SelectionMode.SAVED
        // (which is set after applySavedDefaults by virtue of the chip being clicked).
        SelectionModeChip(
            label = "YOUR DEFAULTS",
            icon = Icons.Default.Bookmark,
            active = activeMode == SelectionMode.SAVED,
            enabled = hasSavedSelection,
            onClick = onApplySaved,
            modifier = Modifier.weight(1f)
        )
        SelectionModeChip(
            label = "PATCH DEFAULTS",
            icon = Icons.Default.AutoAwesome,
            active = activeMode == SelectionMode.DEFAULTS,
            onClick = onApplyDefaults,
            modifier = Modifier.weight(1f)
        )
        SelectionModeChip(
            label = "ALL",
            icon = Icons.Default.DoneAll,
            active = activeMode == SelectionMode.ALL,
            onClick = onApplyAll,
            modifier = Modifier.weight(1f)
        )
        SelectionModeChip(
            label = "NONE",
            icon = Icons.Default.RemoveDone,
            active = activeMode == SelectionMode.NONE,
            onClick = onApplyNone,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SelectionModeChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current

    val hover = remember { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()

    val borderColor by animateColorAsState(
        when {
            !enabled -> MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
            active -> accents.primary.copy(alpha = 0.55f)
            isHovered -> accents.primary.copy(alpha = 0.35f)
            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
        },
        animationSpec = tween(150)
    )
    val bgColor by animateColorAsState(
        when {
            !enabled -> Color.Transparent
            active -> accents.primary.copy(alpha = 0.10f)
            isHovered -> accents.primary.copy(alpha = 0.04f)
            else -> Color.Transparent
        },
        animationSpec = tween(150)
    )
    val textColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
        active -> accents.primary
        else -> accents.primary.copy(alpha = 0.7f)
    }

    Box(
        modifier = modifier
            .height(32.dp)
            .hoverable(hover)
            .clip(RoundedCornerShape(corners.small))
            .border(1.dp, borderColor, RoundedCornerShape(corners.small))
            .background(bgColor, RoundedCornerShape(corners.small))
            .let { if (enabled) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = mono,
                color = textColor,
                letterSpacing = 1.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ── Command Preview ──

@Composable
private fun CommandPreview(
    command: String,
    cleanMode: Boolean,
    onToggleMode: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier
) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current

    val terminalGreen = accents.secondary
    val terminalText = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
    val terminalBg = MaterialTheme.colorScheme.surface

    var showCopied by remember { mutableStateOf(false) }

    LaunchedEffect(showCopied) {
        if (showCopied) {
            kotlinx.coroutines.delay(1500)
            showCopied = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corners.small))
            .border(
                1.dp,
                terminalGreen.copy(alpha = 0.15f),
                RoundedCornerShape(corners.small)
            )
            .background(terminalBg)
            .padding(12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = null,
                    tint = terminalGreen.copy(alpha = 0.7f),
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "COMMAND PREVIEW",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = mono,
                    color = terminalGreen.copy(alpha = 0.7f),
                    letterSpacing = 1.sp
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Copy button
                val copyHover = remember { MutableInteractionSource() }
                val isCopyHovered by copyHover.collectIsHoveredAsState()

                Box(
                    modifier = Modifier
                        .hoverable(copyHover)
                        .clip(RoundedCornerShape(corners.small))
                        .clickable {
                            onCopy()
                            showCopied = true
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            tint = if (showCopied) terminalGreen
                                   else terminalGreen.copy(alpha = if (isCopyHovered) 0.8f else 0.4f),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = if (showCopied) "COPIED" else "COPY",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = mono,
                            color = if (showCopied) terminalGreen
                                   else terminalGreen.copy(alpha = if (isCopyHovered) 0.8f else 0.4f),
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                // Mode toggle
                val modeHover = remember { MutableInteractionSource() }
                val isModeHovered by modeHover.collectIsHoveredAsState()

                Box(
                    modifier = Modifier
                        .hoverable(modeHover)
                        .clip(RoundedCornerShape(corners.small))
                        .clickable(onClick = onToggleMode)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (cleanMode) "COMPACT" else "EXPAND",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = mono,
                        color = terminalGreen.copy(alpha = if (isModeHovered) 0.8f else 0.4f),
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Command text
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 120.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = command,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = terminalText,
                lineHeight = 16.sp
            )
        }
    }
}

// ── Architecture Selector ──

@Composable
private fun StripLibsStatusBanner(
    status: StripLibsStatus,
    modifier: Modifier = Modifier
) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current

    // Each status variant maps to a BannerDisplay that tells the banner what color,
    // headline, description, and arch chips to render.
    // accents.secondary is the app's "informational" accent; MaterialTheme tertiary is
    // used for warning/fallback states.
    val display: BannerDisplay = when (status) {
        is StripLibsStatus.NoNativeLibs -> BannerDisplay(
            dotColor = accents.secondary.copy(alpha = 0.4f),
            headline = "NO NATIVE LIBS",
            detail = "stripping does not apply"
        )
        is StripLibsStatus.Universal -> BannerDisplay(
            dotColor = accents.secondary.copy(alpha = 0.4f),
            headline = "UNIVERSAL LIBS",
            detail = "single universal folder · stripping does not apply"
        )
        is StripLibsStatus.KeepAll -> BannerDisplay(
            dotColor = accents.secondary.copy(alpha = 0.4f),
            headline = "NO STRIPPING NEEDED",
            detail = "keep-list covers every arch in this APK",
            notInApkChips = status.notInApk
        )
        is StripLibsStatus.Fallback -> BannerDisplay(
            dotColor = MaterialTheme.colorScheme.tertiary,
            headline = "FALLBACK · KEEPING ALL",
            detail = "no preferred archs present — review Strip Libs settings",
            keepChips = status.apkArches
        )
        is StripLibsStatus.WillStrip -> BannerDisplay(
            dotColor = accents.secondary,
            headline = "STRIPPING NATIVE LIBS",
            detail = "keeping listed archs only",
            keepChips = status.keeping,
            stripChips = status.stripping,
            notInApkChips = status.notInApk
        )
    }
    val (dotColor, headline, detail, keepChips, stripChips, notInApkChips) = display

    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corners.small))
            .border(1.dp, dotColor.copy(alpha = 0.18f), RoundedCornerShape(corners.small))
            .background(dotColor.copy(alpha = 0.035f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = headline,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = mono,
            color = MaterialTheme.colorScheme.onSurface,
            letterSpacing = 1.sp,
            maxLines = 1
        )
        Text(
            text = "— $detail",
            fontSize = 10.sp,
            fontFamily = mono,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "· Settings → Strip Libs",
            fontSize = 9.sp,
            fontFamily = mono,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            letterSpacing = 0.5.sp,
            maxLines = 1
        )
        Spacer(modifier = Modifier.weight(1f))
        keepChips.forEach { arch ->
            ArchChip(label = arch, accent = dotColor, role = ArchChipRole.KEEP)
        }
        stripChips.forEach { arch ->
            ArchChip(label = arch, accent = dotColor, role = ArchChipRole.STRIP)
        }
        notInApkChips.forEach { arch ->
            ArchChip(label = arch, accent = dotColor, role = ArchChipRole.NOT_IN_APK)
        }
    }
}

private enum class ArchChipRole { KEEP, STRIP, NOT_IN_APK }

@Composable
private fun ArchChip(
    label: String,
    accent: Color,
    role: ArchChipRole
) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current

    // Chip visual treatment per role:
    //  - KEEP       : filled accent background, strong border, full-opacity text
    //  - STRIP      : outlined only, dim border, dimmed text
    //  - NOT_IN_APK : outlined only, very dim border, dimmed italicized text —
    //                 signals "this preference has no effect on this APK"
    val borderAlpha = when (role) {
        ArchChipRole.KEEP -> 0.4f
        ArchChipRole.STRIP -> 0.15f
        ArchChipRole.NOT_IN_APK -> 0.12f
    }
    val textAlpha = when (role) {
        ArchChipRole.KEEP -> 1f
        ArchChipRole.STRIP -> 0.45f
        ArchChipRole.NOT_IN_APK -> 0.5f
    }
    val roleLabel = when (role) {
        ArchChipRole.KEEP -> "keep"
        ArchChipRole.STRIP -> "strip"
        ArchChipRole.NOT_IN_APK -> "not in apk"
    }
    val labelColor = when (role) {
        ArchChipRole.KEEP -> accent.copy(alpha = textAlpha)
        ArchChipRole.STRIP -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = textAlpha)
        ArchChipRole.NOT_IN_APK -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = textAlpha)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(corners.small))
            .border(1.dp, accent.copy(alpha = borderAlpha), RoundedCornerShape(corners.small))
            .then(
                if (role == ArchChipRole.KEEP) {
                    Modifier.background(accent.copy(alpha = 0.08f), RoundedCornerShape(corners.small))
                } else Modifier
            )
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = roleLabel,
                fontSize = 8.sp,
                fontFamily = mono,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                color = accent.copy(alpha = textAlpha * 0.7f),
                fontStyle = if (role == ArchChipRole.NOT_IN_APK) androidx.compose.ui.text.font.FontStyle.Italic
                            else androidx.compose.ui.text.font.FontStyle.Normal
            )
            Text(
                text = label,
                fontSize = 10.sp,
                fontFamily = mono,
                fontWeight = FontWeight.Medium,
                color = labelColor,
                fontStyle = if (role == ArchChipRole.NOT_IN_APK) androidx.compose.ui.text.font.FontStyle.Italic
                            else androidx.compose.ui.text.font.FontStyle.Normal
            )
        }
    }
}

/**
 * Per-status display data for the strip-libs banner. Lets the `when(status)` branch
 * stay terse (each variant just fills in what's relevant) and the rendering code
 * below stay uniform.
 */
private data class BannerDisplay(
    val dotColor: Color,
    val headline: String,
    val detail: String,
    val keepChips: List<String> = emptyList(),
    val stripChips: List<String> = emptyList(),
    val notInApkChips: List<String> = emptyList()
)

// ────────────────────────────────────────────────────────────────────────────
//  Per-bundle collapsible box (multi-bundle view)
// ────────────────────────────────────────────────────────────────────────────

/**
 * Collapsible box containing one bundle's patches. Each box has its own
 * header (bundle name, count, expand chevron, "Your Defaults" chip),
 * per-bundle control buttons (Select all / Deselect / Defaults / Saved),
 * and the patches list itself.
 *
 * In search-active state, the box stays visible even if [BundlePatches.patches]
 * is empty — it renders a "no matches in this bundle" inline empty state so
 * the structural grouping stays stable while the user iterates on the query.
 */
@Composable
private fun BundleBox(
    bundle: BundlePatches,
    selectedInBundle: Set<String>,
    selectionMode: SelectionMode,
    hasSavedForBundle: Boolean,
    expanded: Boolean,
    searchActive: Boolean,
    onExpandToggle: () -> Unit,
    onTogglePatch: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onApplyDefaults: () -> Unit,
    onApplySaved: () -> Unit,
    getOptionValue: (patchName: String, optionKey: String, default: String?) -> String,
    onOptionValueChange: (patchName: String, optionKey: String, value: String) -> Unit,
) {
    val corners = LocalMorpheCorners.current
    val mono = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current

    val enabledCount = selectedInBundle.size
    val totalCount = bundle.patches.size

    val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.20f)
    val bgColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corners.medium))
            .background(bgColor)
            .border(1.dp, outlineColor, RoundedCornerShape(corners.medium))
    ) {
        // ── Header ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onExpandToggle() }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Chevron
            Text(
                text = if (expanded) "▼" else "▶",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                fontFamily = mono,
            )
            Text(
                text = bundle.bundleName,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = mono,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            // Count chip — "Your Defaults" badge lives in SelectionModeChips
            // below so we don't duplicate the signal here.
            Text(
                text = "$enabledCount / $totalCount",
                fontSize = 10.sp,
                fontFamily = mono,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                letterSpacing = 0.5.sp,
            )
            Spacer(Modifier.weight(1f))
        }

        // ── Body (controls + patches) ──
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Per-bundle control row — REUSES the same SelectionModeChips
                // composable the single-bundle path uses, so icons, hover
                // states, "Your Defaults" badge, and full-width layout match
                // exactly. Callbacks scope each action to THIS bundle.
                SelectionModeChips(
                    hasSavedSelection = hasSavedForBundle,
                    activeMode = selectionMode,
                    onApplySaved = onApplySaved,
                    onApplyDefaults = onApplyDefaults,
                    onApplyAll = onSelectAll,
                    onApplyNone = onDeselectAll,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )

                // Patches inside this bundle. Note: this is a regular Column,
                // NOT a LazyColumn — bundles aren't typically huge enough
                // (tens of patches) to justify lazy rendering, and nesting
                // LazyColumns inside a LazyColumn is unsupported.
                if (bundle.patches.isEmpty() && searchActive) {
                    Text(
                        text = "No matches in this bundle",
                        fontSize = 11.sp,
                        fontFamily = mono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        bundle.patches.forEach { patch ->
                            PatchListItem(
                                patch = patch,
                                isSelected = selectedInBundle.contains(patch.uniqueId),
                                onToggle = { onTogglePatch(patch.uniqueId) },
                                // Bundle context is implicit from the box header
                                sourceName = null,
                                getOptionValue = { optionKey, default ->
                                    getOptionValue(patch.name, optionKey, default)
                                },
                                onOptionValueChange = { optionKey, value ->
                                    onOptionValueChange(patch.name, optionKey, value)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

