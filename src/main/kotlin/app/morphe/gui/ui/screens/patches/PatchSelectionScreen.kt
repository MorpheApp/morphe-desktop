/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.patches

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import app.morphe.gui.ui.components.MorpheDialogButton
import androidx.compose.ui.window.Dialog
import app.morphe.gui.ui.components.MorpheDialogCard
import app.morphe.gui.ui.components.MorpheDialogSurface
import app.morphe.gui.ui.components.MorpheDialogText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

import androidx.compose.ui.unit.sp
import app.morphe.gui.LocalOnSettingsDismiss
import app.morphe.gui.data.model.Patch
import app.morphe.gui.data.model.PatchOption
import app.morphe.gui.data.model.PatchOptionType
import app.morphe.gui.data.repository.ConfigRepository
import app.morphe.gui.icon.IconExporter
import app.morphe.gui.icon.IconStudioDialog
import app.morphe.gui.ui.components.DeviceIndicator
import app.morphe.gui.ui.components.ErrorDialog
import app.morphe.gui.ui.components.MorpheSwitch
import app.morphe.gui.ui.components.SettingsButton
import app.morphe.gui.ui.components.ToolsButton
import app.morphe.gui.ui.components.getErrorType
import app.morphe.gui.ui.components.getFriendlyErrorMessage
import app.morphe.gui.ui.components.morpheScrollbarStyle
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.screens.patching.PatchingScreen
import app.morphe.gui.ui.theme.contrastingForeground
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.ui.theme.LocalMorpheMono
import app.morphe.gui.util.MorpheFilePicker
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import app.morphe.gui.ui.components.MorpheBadge
import app.morphe.gui.ui.components.MorpheBadgeTone
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription

private val FILTER_BAR_HEIGHT = 32.dp

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
    /** Parallel to [patchesFilePaths]. Display name per source. Drives badging
     *  in the patch list. Empty disables badging (legacy single-source). */
    val patchSourceNames: List<String> = emptyList(),
    /** One-click repatch seed (source/bundle name → patch uniqueIds). Empty =
     *  normal flow. Set when entering from a "Your apps" / patched-row Repatch. */
    val initialSelectionByBundle: Map<String, Set<String>> = emptyMap(),
    /** One-click repatch option seed ("patchName.optionKey" → value). */
    val initialPatchOptions: Map<String, String> = emptyMap(),
    /** The app's versionName (parsed from the APK), threaded to the output-name helper so
     *  the filename is unique by app version even for renamed bundles. Blank = not supplied. */
    val apkVersion: String = "",
) : Screen {

    @Composable
    override fun Content() {
        val effectiveList = patchesFilePaths.takeIf { it.isNotEmpty() } ?: listOf(patchesFilePath)
        val viewModel = koinScreenModel<PatchSelectionViewModel> {
            parametersOf(
                apkPath, apkName, patchesFilePath, packageName, apkArchitectures,
                effectiveList, patchSourceNames, initialSelectionByBundle, initialPatchOptions,
                apkVersion,
            )
        }
        PatchSelectionScreenContent(viewModel = viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatchSelectionScreenContent(viewModel: PatchSelectionViewModel) {
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val navigator = LocalNavigator.currentOrThrow
    val configRepository: ConfigRepository = koinInject()
    val uiState by viewModel.uiState.collectAsState()
    val targetPackage = viewModel.targetPackage()

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
    var showRunInfo by remember { mutableStateOf(false) }

    if (showRunInfo) {
        val info = remember(uiState.bundles) { viewModel.runInfo() }
        RunInfoDialog(info = info, onDismiss = { showRunInfo = false })
    }

    val dividerColor = MaterialTheme.colorScheme.outlineVariant

    Column(modifier = Modifier.fillMaxSize()) {
        val containerColor = Color.Transparent
        val baseBorderColor = MaterialTheme.colorScheme.outlineVariant
        val baseIconTint = MaterialTheme.colorScheme.onSurfaceVariant

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
            val backBorder by animateColorAsState(
                baseBorderColor,
                animationSpec = tween(150)
            )

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .hoverable(backHover)
                    .clip(RoundedCornerShape(corners.small))
                    .background(containerColor)
                    .border(1.dp, backBorder, RoundedCornerShape(corners.small))
                    .clickable { navigator.pop() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = MorpheIcons.ArrowBack,
                    contentDescription = "Back",
                    tint = baseIconTint,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Title block
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Select patches",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = font,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${uiState.selectedCount} of ${uiState.totalCount} selected",
                    fontSize = 11.sp,
                    fontFamily = font,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Command preview toggle
            if (!uiState.isLoading && uiState.bundles.isNotEmpty()) {
                val cmdHover = remember { MutableInteractionSource() }
                val cmdActive = showCommandPreview
                val cmdAccent = MaterialTheme.colorScheme.onSurface
                val cmdBorder by animateColorAsState(
                    if (cmdActive) cmdAccent.copy(alpha = 0.5f)
                    else baseBorderColor,
                    animationSpec = tween(150)
                )

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .hoverable(cmdHover)
                        .clip(RoundedCornerShape(corners.small))
                        .background(containerColor)
                        .background(if (cmdActive) cmdAccent.copy(alpha = 0.08f) else Color.Transparent)
                        .border(1.dp, cmdBorder, RoundedCornerShape(corners.small))
                        .clickable { showCommandPreview = !showCommandPreview },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = MorpheIcons.Terminal,
                        contentDescription = "Command Preview",
                        tint = if (cmdActive) cmdAccent
                               else baseIconTint,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Continue on error toggle
                val errHover = remember { MutableInteractionSource() }
                val errBorder by animateColorAsState(
                    if (continueOnError) MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                    else baseBorderColor,
                    animationSpec = tween(150)
                )

                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                    tooltip = {
                        PlainTooltip {
                            Text(
                                "Continue patching even if a patch fails",
                                fontFamily = font,
                                fontWeight = FontWeight.Normal,
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
                            .background(containerColor)
                            .background(if (continueOnError) MaterialTheme.colorScheme.error.copy(alpha = 0.08f) else Color.Transparent)
                            .border(1.dp, errBorder, RoundedCornerShape(corners.small))
                            .clickable { continueOnError = !continueOnError },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = MorpheIcons.PlaylistRemove,
                            contentDescription = "Continue on error",
                            tint = if (continueOnError) MaterialTheme.colorScheme.error
                                   else baseIconTint,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))
            }

            val infoHover = remember { MutableInteractionSource() }
            val isInfoHovered by infoHover.collectIsHoveredAsState()
            val infoBorder by animateColorAsState(
                when {
                    showRunInfo -> accents.primary.copy(alpha = 0.5f)
                    isInfoHovered -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
                },
                animationSpec = tween(150)
            )
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                tooltip = {
                    PlainTooltip {
                        Text("APK and patch bundle used for this run", fontFamily = font, fontSize = 11.sp)
                    }
                },
                state = rememberTooltipState()
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .hoverable(infoHover)
                        .clip(RoundedCornerShape(corners.small))
                        .border(1.dp, infoBorder, RoundedCornerShape(corners.small))
                        .then(
                            if (showRunInfo) Modifier.background(
                                accents.primary.copy(alpha = 0.08f),
                                RoundedCornerShape(corners.small)
                            ) else Modifier
                        )
                        .clickable { showRunInfo = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = MorpheIcons.Info,
                        contentDescription = "Run info",
                        tint = if (showRunInfo) accents.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(6.dp))

            DeviceIndicator()
            Spacer(modifier = Modifier.width(6.dp))
            ToolsButton(allowCacheClear = false)
            Spacer(modifier = Modifier.width(6.dp))
            CompositionLocalProvider(LocalOnSettingsDismiss provides { viewModel.refreshStripLibsStatus() }) {
                SettingsButton()
            }
        }

        if (!uiState.isLoading && uiState.bundles.isNotEmpty()) {
            val commandPreview = remember(uiState.selectedByBundle, uiState.stripLibsStatus, cleanMode, continueOnError, keystorePath) {
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
        // bundles. For a single bundle, they're equivalent to the per-
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
                            text = "Loading patches",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = font,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Global empty state. When EVERY loaded bundle has zero patches
            // compatible with this APK. None of the enabled sources contribute
            // anything for this app's package. Rendering empty bundle boxes
            // would be pure noise.
            !uiState.isLoading && uiState.bundles.all { it.patches.isEmpty() } -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (uiState.bundles.isEmpty()) "No patches found"
                               else "None of your enabled sources have patches for this app",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        fontFamily = font,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // Global "no matches for search" empty state. Only fires when
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
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        fontFamily = font,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            else -> {
                // Patch list. Single-bundle renders flat (no box chrome),
                // multi-bundle renders per-bundle collapsible boxes.
                val scrollState = rememberScrollState()

                // Expand/collapse state for multi-bundle, keyed by bundleId.
                // Default: all bundles expanded. Uses plain `remember`. State
                // resets if the user backs out and re-enters the screen, which
                // is acceptable since "show me everything" is the right default.
                val collapsedBundles = remember { mutableStateListOf<String>() }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val showBanner = uiState.stripLibsStatus !is StripLibsStatus.NoNativeLibs
                        if (showBanner) {
                            StripLibsStatusBanner(status = uiState.stripLibsStatus)
                        }

                        if (isSingleBundle) {
                            // ── Flat rendering (single bundle, no chrome) ──
                            val bundle = uiState.filteredBundles.firstOrNull() ?: return@Column
                            val bundleId = bundle.bundleId
                            val selectedInBundle = uiState.selectedByBundle[bundleId].orEmpty()
                            val newInBundle = uiState.newPatchesByBundle[bundleId].orEmpty()
                            bundle.patches.newestFirst(newInBundle).forEach { patch ->
                                PatchListItem(
                                    patch = patch,
                                    isSelected = selectedInBundle.contains(patch.uniqueId),
                                    isNew = patch.uniqueId in newInBundle,
                                    onToggle = { viewModel.togglePatch(bundleId, patch.uniqueId) },
                                    sourceName = null,
                                    packageName = targetPackage,
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
                            // render. Their box shows "no matches in this bundle".
                            val bundlesById = uiState.bundles.associateBy { it.bundleId }
                            val visibleBundles = uiState.filteredBundles.filter { fb ->
                                bundlesById[fb.bundleId]?.patches?.isNotEmpty() == true
                            }
                            visibleBundles.forEach { bundle ->
                                BundleBox(
                                    bundle = bundle,
                                    packageName = targetPackage,
                                    selectedInBundle = uiState.selectedByBundle[bundle.bundleId].orEmpty(),
                                    newInBundle = uiState.newPatchesByBundle[bundle.bundleId].orEmpty(),
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

                    VerticalScrollbar(
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                        adapter = rememberScrollbarAdapter(scrollState),
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
                    val patchEnabled = uiState.selectedCount > 0

                    OutlinedButton(
                        onClick = {
                            val config = viewModel.createPatchConfig(continueOnError)
                            navigator.push(PatchingScreen(config))
                        },
                        enabled = patchEnabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = accents.primary,
                            contentColor = accents.primary.contrastingForeground()
                        ),
                        border = BorderStroke(1.dp, accents.primary),
                        shape = RoundedCornerShape(corners.small)
                    ) {
                        Text(
                            text = "Patch (${uiState.selectedCount})",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = font
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
    val font = LocalMorpheFont.current
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
            else MaterialTheme.colorScheme.outlineVariant,
            animationSpec = tween(150)
        )

        Row(
            modifier = Modifier
                .weight(1f)
                .height(FILTER_BAR_HEIGHT)
                .clip(RoundedCornerShape(corners.small))
                .border(1.dp, searchBorderColor, RoundedCornerShape(corners.small))
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = MorpheIcons.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(16.dp)
            )

            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(
                        "Search patches…",
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.Normal,
                        fontFamily = font,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.Normal,
                        fontFamily = font,
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
                        imageVector = MorpheIcons.Clear,
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
                else -> MaterialTheme.colorScheme.outlineVariant
            },
            animationSpec = tween(150)
        )

        Box(
            modifier = Modifier
                .height(FILTER_BAR_HEIGHT)
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
                        imageVector = MorpheIcons.Check,
                        contentDescription = null,
                        tint = accents.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    text = "Selected",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    fontFamily = font,
                    color = if (showOnlySelected) accents.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
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
    isNew: Boolean = false,
    sourceName: String? = null,
    packageName: String = "",
    getOptionValue: (optionKey: String, default: String?) -> String = { _, d -> d ?: "" },
    onOptionValueChange: (optionKey: String, value: String) -> Unit = { _, _ -> }
) {
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val colors = MaterialTheme.colorScheme
    val containerColor = when {
        isNew && isSelected -> colors.tertiaryContainer.copy(alpha = 0.55f)
        isNew -> colors.tertiaryContainer.copy(alpha = 0.25f)
        isSelected -> accents.primary.copy(alpha = 0.22f)
        isHovered -> accents.primary.copy(alpha = 0.06f)
        else -> Color.Transparent
    }
    val borderColor by animateColorAsState(
        when {
            isSelected -> accents.primary.copy(alpha = 0.7f)
            isHovered -> colors.outlineVariant
            else -> colors.outlineVariant.copy(alpha = 0.5f)
        },
        animationSpec = tween(150)
    )

    var showOptions by remember { mutableStateOf(false) }
    val hasOptions = patch.options.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corners.small))
            .background(containerColor, RoundedCornerShape(corners.small))
            .border(1.dp, borderColor, RoundedCornerShape(corners.small))
            .hoverable(interactionSource)
    ) {
        // The row speaks for its contents, so the New badge has to be read out
        // here or it is never announced.
        val rowDescription = listOfNotNull(
            patch.name,
            if (isSelected) "enabled" else "disabled",
            "new".takeIf { isNew },
        ).joinToString(", ")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(interactionSource = interactionSource, indication = null, onClick = onToggle)
                .semantics(mergeDescendants = true) { contentDescription = rowDescription }
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val boxShape = RoundedCornerShape(corners.small)
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(boxShape)
                    .background(if (isSelected) accents.primary else Color.Transparent, boxShape)
                    .then(
                        if (isSelected) Modifier
                        else Modifier.border(
                            1.dp,
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            boxShape,
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = MorpheIcons.Check,
                        contentDescription = null,
                        tint = accents.primary.contrastingForeground(),
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
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        fontFamily = font,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (isNew) {
                        MorpheBadge(text = "New", tone = MorpheBadgeTone.Primary)
                    }

                    if (sourceName != null) {
                        val badgeColor = if (isSelected) {
                            accents.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Box(
                            modifier = Modifier
                                .border(
                                    1.dp,
                                    badgeColor.copy(alpha = if (isSelected) 0.3f else 0.2f),
                                    RoundedCornerShape(corners.small)
                                )
                                .background(
                                    if (isSelected) badgeColor.copy(alpha = 0.06f) else Color.Transparent,
                                    RoundedCornerShape(corners.small)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = sourceName,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal,
                                fontFamily = font,
                                color = badgeColor.copy(alpha = if (isSelected) 1f else 0.7f),
                                maxLines = 1
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
                                    fontSize = 11.sp,
                                    fontFamily = font,
                                    fontWeight = FontWeight.Normal,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        fontFamily = font,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        showOptions -> accents.primary.copy(alpha = 0.5f)
                        isGearHovered -> accents.primary.copy(alpha = 0.3f)
                        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                    },
                    animationSpec = tween(150)
                )
                val gearBg by animateColorAsState(
                    if (showOptions) accents.primary.copy(alpha = 0.08f)
                    else Color.Transparent,
                    animationSpec = tween(150)
                )

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
                            imageVector = MorpheIcons.Settings,
                            contentDescription = "Configure options",
                            tint = when {
                                showOptions -> accents.primary
                                isGearHovered -> accents.primary.copy(alpha = 0.7f)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            },
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 3.dp, y = (-3).dp)
                            .size(18.dp)
                            .background(accents.primary, RoundedCornerShape(corners.small)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${patch.options.size}",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = font,
                            color = MaterialTheme.colorScheme.onPrimary,
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
                            packageName = packageName,
                            onValueChange = { onOptionValueChange(option.key, it) }
                        )
                    }
                }
            }
        }
    }
}

// ── Patch Option Editor ──

/** Launcher for the `customIcon` option: shows status + opens the Icon Studio,
 *  which exports the mipmap folder and sets the option to that folder path. */
@Composable
private fun IconStudioOption(
    value: String,
    packageName: String,
    onValueChange: (String) -> Unit,
) {
    val accents = LocalMorpheAccents.current
    val font = LocalMorpheFont.current
    val corners = LocalMorpheCorners.current
    val shape = RoundedCornerShape(corners.small)
    val hasIcon = value.isNotBlank()
    var showStudio by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconActionPill(MorpheIcons.Edit, if (hasIcon) "Edit icon" else "Design icon", accents.primary, filled = true, shape = shape, font = font) { showStudio = true }
        // Import an already-prepared folder (e.g. one made in the Manager).
        IconActionPill(MorpheIcons.FolderOpen, "Import folder", accents.primary.copy(alpha = 0.8f), filled = false, shape = shape, font = font) {
            scope.launch {
                MorpheFilePicker.pickDirectory(title = "Select an icon folder")
                    ?.let { onValueChange(it.absolutePath) }
            }
        }
        if (hasIcon) {
            IconActionPill(MorpheIcons.Delete, "Delete", MaterialTheme.colorScheme.error, filled = false, shape = shape, font = font) { showDeleteConfirm = true }
        }
        Text(
            text = if (hasIcon) "Custom icon ready" else "No custom icon set",
            fontSize = 11.sp,
            fontFamily = font,
            color = if (hasIcon) accents.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showStudio) {
        IconStudioDialog(
            packageName = packageName,
            onSave = { folder -> onValueChange(folder) },
            onDismiss = { showStudio = false },
        )
    }

    if (showDeleteConfirm) {
        MorpheDialogCard(onDismiss = { showDeleteConfirm = false }, title = "Delete custom icon?") {
            MorpheDialogText(
                "This clears the icon and removes its saved project and generated files. This can't be undone"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                MorpheDialogButton("Cancel", MaterialTheme.colorScheme.onSurfaceVariant, filled = false) {
                    showDeleteConfirm = false
                }
                MorpheDialogButton("Delete", Color(0xFFE0504D), filled = true) {
                    runCatching { IconExporter.projectDir(packageName).deleteRecursively() }
                    onValueChange("")
                    showDeleteConfirm = false
                }
            }
        }
    }
}

/** A small icon+label pill used by the customIcon row (edit / import / delete). */
@Composable
private fun IconActionPill(
    icon: ImageVector,
    text: String,
    color: Color,
    filled: Boolean,
    shape: Shape,
    font: FontFamily,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(shape)
            .then(if (filled) Modifier.background(color.copy(alpha = 0.15f)) else Modifier)
            .border(1.dp, color.copy(alpha = if (filled) 0.5f else 0.35f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(11.dp))
            Text(text, fontSize = 11.sp, fontWeight = FontWeight.Normal, fontFamily = font, color = color)
        }
    }
}

@Composable
private fun PatchOptionEditor(
    option: PatchOption,
    value: String,
    packageName: String = "",
    onValueChange: (String) -> Unit
) {
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = option.title.ifBlank { option.key },
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = font,
                color = accents.primary
            )
            if (option.required) {
                Text(
                    text = "*",
                    fontSize = 12.sp,
                    fontFamily = font,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        if (option.description.isNotBlank()) {
            val descText = option.description.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
                ?: option.description
            Text(
                text = descText,
                fontSize = 10.sp,
                fontFamily = font,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        // For customIcon, add our own note: the Icon Studio builds this folder for you.
        if (option.key.equals("customIcon", ignoreCase = true)) {
            Text(
                text = "You don't have to build this folder yourself! Click on the Design/Edit icon to create it in the Icon Studio or import it from the Manager!)",
                fontSize = 10.sp,
                fontFamily = font,
                fontWeight = FontWeight.Normal,
                color = accents.primary,
            )
        }
        when (option.type) {
            PatchOptionType.BOOLEAN -> {
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
                        accentColor = accents.primary
                    )
                    Text(
                        text = if (localChecked) "Enabled" else "Disabled",
                        fontSize = 10.sp,
                        fontFamily = font,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
            PatchOptionType.FILE -> {
                if (option.key.equals("customIcon", ignoreCase = true)) {
                    IconStudioOption(value = value, packageName = packageName, onValueChange = onValueChange)
                } else {
                var localPath by remember(option.key) { mutableStateOf(value) }
                LaunchedEffect(value) {
                    if (localPath != value) localPath = value
                }

                // Detect if this is an image file option from key/title
                val keyLower = option.key.lowercase() + " " + option.title.lowercase()
                val isImage = keyLower.contains("icon") || keyLower.contains("image") ||
                    keyLower.contains("logo") || keyLower.contains("banner") ||
                    keyLower.contains("png") || keyLower.contains("jpg")
                val fileExtensions = if (isImage) listOf("png", "jpg", "jpeg", "webp") else emptyList<String>()

                val fieldFocused = remember { mutableStateOf(false) }
                val fieldBorder by animateColorAsState(
                    if (fieldFocused.value) accents.primary.copy(alpha = 0.6f)
                    else accents.primary.copy(alpha = 0.2f),
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
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (localPath.isEmpty()) {
                                Text(
                                    text = if (isImage) "Select image…" else "Select file…",
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp,
                                    fontFamily = font,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            BasicTextField(
                                value = localPath,
                                onValueChange = { newPath ->
                                    localPath = newPath
                                    onValueChange(newPath)
                                },
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp,
                                    fontFamily = font,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(accents.primary),
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
                        if (isBrowseHovered) accents.primary.copy(alpha = 0.5f)
                        else accents.primary.copy(alpha = 0.2f),
                        animationSpec = tween(150)
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .hoverable(browseHover)
                            .clip(RoundedCornerShape(corners.small))
                            .border(1.dp, browseBorder, RoundedCornerShape(corners.small))
                            .clickable {
                                scope.launch {
                                    val picked = MorpheFilePicker.pickFile(
                                        extensions = fileExtensions,
                                    ) ?: return@launch
                                    localPath = picked.absolutePath
                                    onValueChange(picked.absolutePath)
                                }
                            }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Browse",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = font,
                            color = if (isBrowseHovered) accents.primary else accents.primary.copy(alpha = 0.7f)
                        )
                    }
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
                    if (fieldFocused.value) accents.primary.copy(alpha = 0.6f)
                    else accents.primary.copy(alpha = 0.2f),
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
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (localText.isEmpty()) {
                            Text(
                                text = option.default ?: option.type.name.lowercase(),
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                fontFamily = font,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                        BasicTextField(
                            value = localText,
                            onValueChange = { newText ->
                                localText = newText
                                onValueChange(newText)
                            },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                fontFamily = font,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(accents.primary),
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
        // SAVED is computed by overlaying the saved-selection check on top of CUSTOM
        // when hasSavedSelection is true AND the current selection matches the saved
        // bundle, we treat it as SAVED. The VM only knows ALL/DEFAULTS/NONE/CUSTOM, so
        // we approximate: if hasSavedSelection is true and activeMode is CUSTOM, the
        // user could still be on their saved set. We can't tell here without the
        // bundle. For now SAVED highlights only when activeMode == SelectionMode.SAVED
        // (which is set after applySavedDefaults by virtue of the chip being clicked).
        SelectionModeChip(
            label = "Your defaults",
            icon = MorpheIcons.Bookmark,
            active = activeMode == SelectionMode.SAVED,
            enabled = hasSavedSelection,
            onClick = onApplySaved,
            modifier = Modifier.weight(1f)
        )
        SelectionModeChip(
            label = "Patch defaults",
            icon = MorpheIcons.AutoAwesome,
            active = activeMode == SelectionMode.DEFAULTS,
            onClick = onApplyDefaults,
            modifier = Modifier.weight(1f)
        )
        SelectionModeChip(
            label = "All",
            icon = MorpheIcons.DoneAll,
            active = activeMode == SelectionMode.ALL,
            onClick = onApplyAll,
            modifier = Modifier.weight(1f)
        )
        SelectionModeChip(
            label = "None",
            icon = MorpheIcons.RemoveDone,
            active = activeMode == SelectionMode.NONE,
            onClick = onApplyNone,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SelectionModeChip(
    label: String,
    icon: ImageVector,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current

    val hover = remember { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()

    val borderColor by animateColorAsState(
        when {
            !enabled -> MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
            active -> accents.primary.copy(alpha = 0.55f)
            isHovered -> accents.primary.copy(alpha = 0.35f)
            else -> MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = tween(150)
    )
    val bgColor by animateColorAsState(
        when {
            !enabled -> Color.Transparent
            active -> accents.primary
            isHovered -> accents.primary.copy(alpha = 0.10f)
            else -> Color.Transparent
        },
        animationSpec = tween(150)
    )
    val textColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        active -> accents.primary.contrastingForeground()
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
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = font,
                color = textColor,
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
    val font = LocalMorpheFont.current
    val mono = LocalMorpheMono.current
    val accents = LocalMorpheAccents.current

    val terminalAccent = MaterialTheme.colorScheme.onSurface
    val terminalText = MaterialTheme.colorScheme.onSurface
    val terminalBg = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)

    var showCopied by remember { mutableStateOf(false) }

    LaunchedEffect(showCopied) {
        if (showCopied) {
            delay(1500.milliseconds)
            showCopied = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corners.small))
            .border(
                1.dp,
                terminalAccent.copy(alpha = 0.15f),
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
                    imageVector = MorpheIcons.Terminal,
                    contentDescription = null,
                    tint = terminalAccent.copy(alpha = 0.7f),
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Command preview",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = font,
                    color = terminalAccent.copy(alpha = 0.7f)
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
                        Text(
                            text = if (showCopied) "Copied" else "Copy",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = font,
                            color = if (showCopied) terminalAccent
                                   else terminalAccent.copy(alpha = if (isCopyHovered) 0.8f else 0.4f)
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
                        text = if (cleanMode) "Compact" else "Expand",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        fontFamily = font,
                        color = terminalAccent.copy(alpha = if (isModeHovered) 0.8f else 0.4f)
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
                fontFamily = mono,
                fontWeight = FontWeight.Normal,
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
    val font = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current

    // Each status variant maps to a BannerDisplay that tells the banner what color,
    // headline, description, and arch chips to render.
    // accents.secondary is the app's "informational" accent. MaterialTheme tertiary is
    // used for warning/fallback states.
    val display: BannerDisplay = when (status) {
        is StripLibsStatus.NoNativeLibs -> BannerDisplay(
            dotColor = accents.secondary.copy(alpha = 0.4f),
            headline = "No native libs",
            detail = "stripping does not apply"
        )
        is StripLibsStatus.Universal -> BannerDisplay(
            dotColor = accents.secondary.copy(alpha = 0.4f),
            headline = "Universal libs",
            detail = "single universal folder - stripping does not apply"
        )
        is StripLibsStatus.KeepAll -> BannerDisplay(
            dotColor = accents.secondary.copy(alpha = 0.4f),
            headline = "No stripping needed",
            detail = "keep-list covers every arch in this APK",
            notInApkChips = status.notInApk
        )
        is StripLibsStatus.Fallback -> BannerDisplay(
            dotColor = MaterialTheme.colorScheme.tertiary,
            headline = "Fallback - keeping all",
            detail = "no preferred archs present - review Strip Libs settings",
            keepChips = status.apkArches
        )
        is StripLibsStatus.WillStrip -> BannerDisplay(
            dotColor = accents.secondary,
            headline = "Stripping native libs",
            detail = "keeping listed archs only",
            keepChips = status.keeping,
            stripChips = status.stripping,
            notInApkChips = status.notInApk
        )
    }
    val (dotColor, headline, detail, keepChips, stripChips, notInApkChips) = display

    FlowRow(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corners.small))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(corners.small))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = headline,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = font,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
        Text(
            text = "- $detail",
            fontSize = 11.sp,
            fontFamily = font,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "· Settings → Strip Libs",
            fontSize = 9.sp,
            fontFamily = font,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Spacer(modifier = Modifier.weight(1f))
        keepChips.forEach { arch ->
            ArchChip(label = arch, accent = accents.secondary, role = ArchChipRole.KEEP)
        }
        stripChips.forEach { arch ->
            ArchChip(label = arch, accent = MaterialTheme.colorScheme.error, role = ArchChipRole.STRIP)
        }
        notInApkChips.forEach { arch ->
            ArchChip(label = arch, accent = accents.primary, role = ArchChipRole.NOT_IN_APK)
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
    val font = LocalMorpheFont.current

    // Chip visual treatment per role:
    //  - KEEP       : filled accent background, strong border, full-opacity text
    //  - STRIP      : outlined only, dim border, dimmed text
    //  - NOT_IN_APK : outlined only, very dim border, dimmed italicized text
    //                 signals "this preference has no effect on this APK"
    val borderAlpha = when (role) {
        ArchChipRole.KEEP -> 0.4f
        ArchChipRole.STRIP -> 0.3f
        ArchChipRole.NOT_IN_APK -> 0.3f
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
                fontSize = 9.sp,
                fontFamily = font,
                fontWeight = FontWeight.Medium,
                color = accent.copy(alpha = textAlpha * 0.7f)
            )
            Text(
                text = label,
                fontSize = 11.sp,
                fontFamily = font,
                fontWeight = FontWeight.Normal,
                color = labelColor
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
 * is empty. It renders a "no matches in this bundle" inline empty state so
 * the structural grouping stays stable while the user iterates on the query.
 */
@Composable
private fun BundleBox(
    bundle: BundlePatches,
    packageName: String = "",
    selectedInBundle: Set<String>,
    newInBundle: Set<String>,
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
    val font = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current

    val enabledCount = selectedInBundle.size
    val totalCount = bundle.patches.size

    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    val bgColor = Color.Transparent

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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = font,
            )
            Text(
                text = bundle.bundleName,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = font,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            // Count chip. "Your Defaults" badge lives in SelectionModeChips
            // below so we don't duplicate the signal here.
            Text(
                text = "$enabledCount / $totalCount",
                fontSize = 10.sp,
                fontFamily = font,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                // Per-bundle control row. REUSES the same SelectionModeChips
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
                // NOT a LazyColumn. Bundles aren't typically huge enough
                // (tens of patches) to justify lazy rendering, and nesting
                // LazyColumns inside a LazyColumn is unsupported.
                if (bundle.patches.isEmpty() && searchActive) {
                    Text(
                        text = "No matches in this bundle",
                        fontSize = 11.sp,
                        fontFamily = font,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        bundle.patches.newestFirst(newInBundle).forEach { patch ->
                            PatchListItem(
                                patch = patch,
                                isSelected = selectedInBundle.contains(patch.uniqueId),
                                isNew = patch.uniqueId in newInBundle,
                                onToggle = { onTogglePatch(patch.uniqueId) },
                                // Bundle context is implicit from the box header
                                sourceName = null,
                                packageName = packageName,
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

@Composable
private fun RunInfoDialog(info: RunInfo, onDismiss: () -> Unit) {
    val font = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    Dialog(onDismissRequest = onDismiss) {
        MorpheDialogSurface(
            modifier = Modifier.widthIn(max = 520.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = "This run",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = font,
                color = MaterialTheme.colorScheme.onSurface,
            )

            RunInfoGroup(label = "App", color = accents.secondary, font = font) {
                RunInfoHeadline(
                    name = info.appName,
                    version = info.appVersion.takeIf { it.isNotBlank() }
                        ?.let { "v${it.removePrefix("v")}" },
                    accent = accents.secondary,
                    font = font,
                )
                RunInfoDetail(info.packageName, font)
                RunInfoDetail(info.apkFileName, font)
                RunInfoDetail(info.apkPath, font)
            }

            RunInfoGroup(
                label = if (info.bundles.size == 1) "Patch bundle" else "Patch bundles",
                color = accents.primary,
                font = font,
            ) {
                info.bundles.forEach { bundle ->
                    RunInfoHeadline(
                        name = bundle.name,
                        version = bundle.version?.let { "v${it.removePrefix("v")}" },
                        accent = accents.primary,
                        font = font,
                    )
                    RunInfoDetail(bundle.fileName, font)
                }
                if (info.bundles.isEmpty()) RunInfoDetail("No bundles resolved", font)
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                MorpheDialogButton("Close", accents.primary, filled = false, onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun RunInfoGroup(
    label: String,
    color: Color,
    font: FontFamily,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = font,
            color = color.copy(alpha = 0.85f),
        )
        content()
    }
}

@Composable
private fun RunInfoHeadline(name: String, version: String?, accent: Color, font: FontFamily) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            fontSize = 13.sp,
            fontFamily = font,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = version ?: "unknown",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = font,
            color = if (version != null) accent
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
        )
    }
}

@Composable
private fun RunInfoDetail(text: String, font: FontFamily) {
    Text(
        text = text,
        fontSize = 9.sp,
        fontFamily = font,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        lineHeight = 13.sp,
    )
}

/** New patches float to the top, the rest keep the bundle's own order. */
private fun List<Patch>.newestFirst(newIds: Set<String>): List<Patch> =
    if (newIds.isEmpty()) this else sortedByDescending { it.uniqueId in newIds }
