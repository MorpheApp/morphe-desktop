/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.patches

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.HorizontalScrollbar
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.LocalGroupPatchesByCategory
import app.morphe.gui.data.model.Patch
import app.morphe.gui.data.repository.ConfigRepository
import app.morphe.gui.ui.components.ErrorDialog
import app.morphe.gui.ui.components.MorpheBadge
import app.morphe.gui.ui.components.MorpheBadgeTone
import app.morphe.gui.ui.components.getErrorType
import app.morphe.gui.ui.components.getFriendlyErrorMessage
import app.morphe.gui.ui.components.handCursor
import app.morphe.gui.ui.components.morpheScrollbarStyle
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.screens.patching.PatchingScreen
import app.morphe.gui.ui.screens.patches.components.selection.*
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.ui.theme.screenScrim
import app.morphe.morphe_desktop.generated.resources.*
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

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
            title = stringResource(Res.string.patch_selection_error_title),
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

    Column(modifier = Modifier.fillMaxSize().background(screenScrim)) {
        // ── Header bar ──
        PatchSelectionHeader(
            uiState = uiState,
            showCommandPreview = showCommandPreview,
            onToggleCommandPreview = { showCommandPreview = !showCommandPreview },
            continueOnError = continueOnError,
            onToggleContinueOnError = { continueOnError = !continueOnError },
            showRunInfo = showRunInfo,
            onShowRunInfo = { showRunInfo = true },
            onBackClick = { navigator.pop() },
            onRefreshStripLibsStatus = { viewModel.refreshStripLibsStatus() },
        )

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
                            text = stringResource(Res.string.patching_step_loading_patches),
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
                        text = if (uiState.bundles.isEmpty()) stringResource(Res.string.patch_selection_no_patches_found)
                               else stringResource(Res.string.patch_selection_no_source_patches_for_app),
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
                        text = stringResource(Res.string.patch_selection_no_search_matches),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        fontFamily = font,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            else -> {
                val eligibleBundles = remember(uiState.bundles) {
                    uiState.bundles.filter { it.patches.isNotEmpty() }
                }
                val filteredBundlesById = remember(uiState.filteredBundles) {
                    uiState.filteredBundles.associateBy { it.bundleId }
                }
                val hasMultipleBundles = eligibleBundles.size > 1
                val groupByCategory = LocalGroupPatchesByCategory.current.value
                val sectionState = rememberPatchSectionState()
                val coroutineScope = rememberCoroutineScope()
                val pagerState = rememberPagerState { eligibleBundles.size }

                val currentBundles = rememberUpdatedState(eligibleBundles)
                val currentFiltered = rememberUpdatedState(filteredBundlesById)
                LaunchedEffect(pagerState) {
                    snapshotFlow { currentFiltered.value }.collect { filteredMap ->
                        val bundles = currentBundles.value
                        val openBundle = bundles.getOrNull(pagerState.currentPage) ?: return@collect
                        val openHasResults = filteredMap[openBundle.bundleId]?.patches?.isNotEmpty() == true
                        if (openHasResults) return@collect

                        val firstWithResultsIndex = bundles.indexOfFirst { bundle ->
                            filteredMap[bundle.bundleId]?.patches?.isNotEmpty() == true
                        }
                        if (firstWithResultsIndex >= 0) {
                            pagerState.animateScrollToPage(firstWithResultsIndex)
                        }
                    }
                }

                val pageListStates = rememberSaveable(
                    eligibleBundles.size,
                    saver = listSaver(
                        save = { states ->
                            states.flatMap { listOf(it.firstVisibleItemIndex, it.firstVisibleItemScrollOffset) }
                        },
                        restore = { saved ->
                            saved.chunked(2).map { (index, offset) -> LazyListState(index, offset) }
                        }
                    )
                ) {
                    List(eligibleBundles.size) { LazyListState() }
                }

                val showBanner = uiState.stripLibsStatus !is StripLibsStatus.NoNativeLibs
                if (showBanner) {
                    StripLibsStatusBanner(
                        status = uiState.stripLibsStatus,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    // 1. Tab row (when multiple bundles are present)
                    if (hasMultipleBundles) {
                        val tabScrollState = rememberScrollState()

                        SecondaryScrollableTabRow(
                            selectedTabIndex = pagerState.currentPage.coerceIn(0, (eligibleBundles.size - 1).coerceAtLeast(0)),
                            scrollState = tabScrollState,
                            edgePadding = 0.dp,
                            divider = {},
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.primary
                        ) {
                            eligibleBundles.forEachIndexed { index, bundle ->
                                val bundleFiltered = filteredBundlesById[bundle.bundleId]?.patches.orEmpty()
                                val hasResults = bundleFiltered.isNotEmpty()
                                val enabledCount = uiState.selectedByBundle[bundle.bundleId]?.size ?: 0
                                val totalCount = bundle.patches.size
                                val isSelected = pagerState.currentPage == index

                                Tab(
                                    selected = isSelected,
                                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                                    modifier = Modifier.handCursor(),
                                    selectedContentColor = MaterialTheme.colorScheme.primary,
                                    unselectedContentColor = if (hasResults)
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            text = bundle.bundleName,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                            fontFamily = font,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        Spacer(modifier = Modifier.height(2.dp))

                                        MorpheBadge(
                                            text = "$enabledCount/$totalCount",
                                            tone = if (isSelected && hasResults) MorpheBadgeTone.Primary else MorpheBadgeTone.Neutral
                                        )
                                    }
                                }
                            }
                        }

                        if (tabScrollState.maxValue > 0) {
                            Spacer(Modifier.height(4.dp))
                            HorizontalScrollbar(
                                adapter = rememberScrollbarAdapter(tabScrollState),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                style = morpheScrollbarStyle()
                            )
                            Spacer(Modifier.height(4.dp))
                        }

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            thickness = 0.5.dp
                        )
                    } else {
                        val singleBundle = eligibleBundles.firstOrNull()
                        if (singleBundle != null) {
                            Row(
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = MorpheIcons.Source,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = singleBundle.bundleName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontFamily = font
                                )
                            }
                        }
                    }

                    // 2. Controls fixed below the tab row
                    val currentIndex = pagerState.currentPage
                    val currentBundle = eligibleBundles.getOrNull(currentIndex)
                    if (currentBundle != null) {
                        SelectionModeChips(
                            hasSavedSelection = uiState.savedSelectedByBundle?.containsKey(currentBundle.bundleId) == true,
                            activeMode = uiState.selectionModeFor(currentBundle.bundleId),
                            onApplySaved = { viewModel.applySavedDefaultsInBundle(currentBundle.bundleId) },
                            onApplyDefaults = { viewModel.applyPatchDefaultsInBundle(currentBundle.bundleId) },
                            onApplyAll = { viewModel.selectAllInBundle(currentBundle.bundleId) },
                            onApplyNone = { viewModel.deselectAllInBundle(currentBundle.bundleId) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }

                    // 3. Pager for the patch list
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { pageIndex ->
                            val bundle = eligibleBundles.getOrNull(pageIndex) ?: return@HorizontalPager
                            val bundleFiltered = filteredBundlesById[bundle.bundleId]?.patches.orEmpty()
                            val selectedInBundle = uiState.selectedByBundle[bundle.bundleId].orEmpty()
                            val newInBundle = uiState.newPatchesByBundle[bundle.bundleId].orEmpty()
                            val listState = pageListStates.getOrElse(pageIndex) { rememberLazyListState() }

                            if (bundleFiltered.isEmpty() && uiState.searchQuery.isNotBlank()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = stringResource(Res.string.patch_selection_no_matches_in_bundle),
                                        fontSize = 13.sp,
                                        fontFamily = font,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                val sortedPatches = remember(bundleFiltered, newInBundle) {
                                    bundleFiltered.newestFirst(newInBundle)
                                }
                                val universalTitle = stringResource(Res.string.patch_selection_group_universal)
                                val ungroupedTitle = stringResource(Res.string.patch_selection_group_ungrouped)
                                val groups = remember(sortedPatches, groupByCategory, selectedInBundle, universalTitle, ungroupedTitle) {
                                    buildPatchGroups(
                                        patches = sortedPatches,
                                        groupByCategory = groupByCategory,
                                        universalTitle = universalTitle,
                                        ungroupedTitle = ungroupedTitle,
                                        categoryOf = { it.category },
                                        isUniversal = { it.isUniversal },
                                        isEnabled = { it.uniqueId in selectedInBundle }
                                    )
                                }
                                val isSearching = uiState.searchQuery.isNotBlank()

                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    groups.forEach { group ->
                                        if (group.title != null) {
                                            val isExpanded = isSearching || sectionState.isExpanded(bundle.bundleId, group)
                                            item(key = "group_${bundle.bundleId}_${group.key}") {
                                                PatchGroupHeader(
                                                    title = group.title,
                                                    count = group.items.size,
                                                    isExpanded = isExpanded,
                                                    onToggle = if (isSearching) null else ({ sectionState.toggle(bundle.bundleId, group) }),
                                                    icon = group.icon,
                                                    selectedCount = group.selectedCount
                                                )
                                            }
                                            if (isExpanded) {
                                                items(
                                                    items = group.items,
                                                    key = { it.uniqueId }
                                                ) { patch ->
                                                    PatchListItem(
                                                        patch = patch,
                                                        isSelected = selectedInBundle.contains(patch.uniqueId),
                                                        isNew = patch.uniqueId in newInBundle,
                                                        onToggle = { viewModel.togglePatch(bundle.bundleId, patch.uniqueId) },
                                                        sourceName = null,
                                                        packageName = targetPackage,
                                                        patchOptionValues = uiState.patchOptionValues,
                                                        getOptionValue = { optionKey, default ->
                                                            viewModel.getOptionValue(patch.name, optionKey, default)
                                                        },
                                                        onOptionValueChange = { optionKey, value ->
                                                            viewModel.setOptionValue(patch.name, optionKey, value)
                                                        }
                                                    )
                                                }
                                            }
                                        } else {
                                            items(
                                                items = group.items,
                                                key = { it.uniqueId }
                                            ) { patch ->
                                                PatchListItem(
                                                    patch = patch,
                                                    isSelected = selectedInBundle.contains(patch.uniqueId),
                                                    isNew = patch.uniqueId in newInBundle,
                                                    onToggle = { viewModel.togglePatch(bundle.bundleId, patch.uniqueId) },
                                                    sourceName = null,
                                                    packageName = targetPackage,
                                                    patchOptionValues = uiState.patchOptionValues,
                                                    getOptionValue = { optionKey, default ->
                                                        viewModel.getOptionValue(patch.name, optionKey, default)
                                                    },
                                                    onOptionValueChange = { optionKey, value ->
                                                        viewModel.setOptionValue(patch.name, optionKey, value)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        val currentPageList = eligibleBundles.getOrNull(pagerState.currentPage)
                            ?.takeIf { filteredBundlesById[it.bundleId]?.patches?.isNotEmpty() == true }
                            ?.let { pageListStates.getOrNull(pagerState.currentPage) }
                        if (currentPageList != null) {
                            VerticalScrollbar(
                                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                                adapter = rememberScrollbarAdapter(currentPageList),
                                style = morpheScrollbarStyle()
                            )
                        }
                    }
                }

                // ── Bottom action bar ──
                PatchSelectionBottomBar(
                    selectedCount = uiState.selectedCount,
                    onPatchClick = {
                        val config = viewModel.createPatchConfig(continueOnError)
                        navigator.push(PatchingScreen(config))
                    },
                )
            }
        }
    }
}


/** New patches float to the top, the rest keep the bundle's own order. */
private fun List<Patch>.newestFirst(newIds: Set<String>): List<Patch> =
    if (newIds.isEmpty()) this else sortedByDescending { it.uniqueId in newIds }
