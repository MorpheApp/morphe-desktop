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
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.engine.config.EngineConfig
import app.morphe.engine.config.EngineConfigRepository
import app.morphe.engine.model.PatchMetadata
import app.morphe.gui.LocalGroupPatchesByCategory
import app.morphe.gui.LocalNavController
import app.morphe.gui.PatchSelectionParams
import app.morphe.gui.PatchingScreenRoute
import app.morphe.gui.navigateComplex
import app.morphe.gui.ui.components.MorpheBadge
import app.morphe.gui.ui.components.MorpheBadgeTone
import app.morphe.gui.ui.components.MorpheScrollbarStyle
import app.morphe.gui.ui.components.ScreenErrorDialog
import app.morphe.gui.ui.components.handCursor
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.screens.patches.components.selection.*
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.ui.theme.screenScrim
import app.morphe.morphe_desktop.generated.resources.*
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun PatchSelectionScreen(
    params: PatchSelectionParams,
    viewModel: PatchSelectionViewModel = koinViewModel {
        val effectiveList = params.patchesFilePaths.takeIf { it.isNotEmpty() } ?: listOf(params.patchesFilePath)
        parametersOf(
            params.apkPath, params.apkName, params.patchesFilePath, params.packageName, params.apkArchitectures,
            effectiveList, params.patchSourceNames, params.initialSelectionByBundle, params.initialPatchOptions,
            params.apkVersion,
        )
    }
) {
    PatchSelectionScreenContent(viewModel = viewModel)
}

@Composable
fun PatchSelectionScreenContent(viewModel: PatchSelectionViewModel) {
    val primaryColor = MorpheTheme.colors.primary
    val navController = LocalNavController.current
    val engineConfigRepository: EngineConfigRepository = koinInject()
    val uiState by viewModel.uiState.collectAsState()
    val targetPackage = viewModel.targetPackage()

    val engineConfig by produceState<EngineConfig?>(initialValue = null) {
        value = engineConfigRepository.loadConfig()
    }

    ScreenErrorDialog(
        error = uiState.error,
        title = stringResource(Res.string.patch_selection_error_title),
        onDismiss = { viewModel.clearError() },
        onRetry = { viewModel.loadPatches() }
    )

    var cleanMode by rememberSaveable { mutableStateOf(false) }
    var showCommandPreview by rememberSaveable { mutableStateOf(false) }
    var continueOnError by rememberSaveable { mutableStateOf(false) }

    var showRunInfo by remember { mutableStateOf(false) }
    if (showRunInfo) {
        val info = remember(uiState.bundles) { viewModel.runInfo() }
        RunInfoDialog(info = info, onDismiss = { showRunInfo = false })
    }

    Column(modifier = Modifier.fillMaxSize().background(screenScrim)) {
        PatchSelectionHeader(
            uiState = uiState,
            showCommandPreview = showCommandPreview,
            onToggleCommandPreview = { showCommandPreview = !showCommandPreview },
            continueOnError = continueOnError,
            onToggleContinueOnError = { continueOnError = !continueOnError },
            showRunInfo = showRunInfo,
            onShowRunInfo = { showRunInfo = true },
            onBackClick = { navController.popBackStack() },
            onRefreshStripLibsStatus = { viewModel.refreshStripLibsStatus() },
        )

        if (!uiState.isLoading && uiState.bundles.isNotEmpty()) {
            val commandPreview = remember(
                uiState.selectedByBundle,
                uiState.patchOptionValues,
                uiState.stripLibsStatus,
                cleanMode,
                continueOnError,
                engineConfig
            ) {
                viewModel.getCommandPreview(
                    cleanMode = cleanMode,
                    continueOnError = continueOnError,
                    keystorePath = engineConfig?.resolvedKeystorePath()?.absolutePath,
                    keystorePassword = engineConfig?.keystorePassword,
                    keystoreAlias = engineConfig?.keystoreAlias,
                    keystoreEntryPassword = engineConfig?.keystoreEntryPassword
                )
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
                            color = primaryColor,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = stringResource(Res.string.patching_step_loading_patches),
                            style = MorpheTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // No loaded bundles have patches compatible with this app.
            !uiState.isLoading && uiState.bundles.all { it.patches.isEmpty() } -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (uiState.bundles.isEmpty()) stringResource(Res.string.patch_selection_no_patches_found)
                               else stringResource(Res.string.patch_selection_no_source_patches_for_app),
                        style = MorpheTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // All non-empty bundles filtered out by search query.
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
                        style = MorpheTheme.typography.bodyMedium,
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
                                            style = if (isSelected) MorpheTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                                    else MorpheTheme.typography.bodyMedium,
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
                                style = MorpheScrollbarStyle()
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
                                    tint = primaryColor,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = singleBundle.bundleName,
                                    style = MorpheTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = primaryColor,
                                )
                            }
                        }
                    }

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
                                        style = MorpheTheme.typography.bodyMedium,
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
                                        val headerTitle = group.title
                                        val isExpanded = headerTitle == null || isSearching || sectionState.isExpanded(bundle.bundleId, group)
                                        if (headerTitle != null) {
                                            item(key = "group_${bundle.bundleId}_${group.key}") {
                                                PatchGroupHeader(
                                                    title = headerTitle,
                                                    count = group.items.size,
                                                    isExpanded = isExpanded,
                                                    onToggle = if (isSearching) null else ({ sectionState.toggle(bundle.bundleId, group) }),
                                                    icon = group.icon,
                                                    selectedCount = group.selectedCount
                                                )
                                            }
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
                                style = MorpheScrollbarStyle()
                            )
                        }
                    }
                }

                PatchSelectionBottomBar(
                    selectedCount = uiState.selectedCount,
                    onPatchClick = {
                        val config = viewModel.createPatchConfig(continueOnError)
                        navController.navigateComplex(PatchingScreenRoute, config)
                    },
                )
            }
        }
    }
}


/** New patches float to the top, the rest keep the bundle's own order. */
private fun List<PatchMetadata>.newestFirst(newIds: Set<String>): List<PatchMetadata> =
    if (newIds.isEmpty()) this else sortedByDescending { it.uniqueId in newIds }
