/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.engine.model.PatchedAppRecord
import app.morphe.engine.model.SupportedApp
import app.morphe.gui.LocalShowMoreAppsCard
import app.morphe.gui.data.constants.AppConstants
import app.morphe.gui.data.repository.ConfigRepository
import app.morphe.gui.ui.components.MorpheButton
import app.morphe.gui.ui.components.MorpheButtonSize
import app.morphe.gui.ui.components.MorpheButtonVariant
import app.morphe.gui.ui.components.MorpheChoiceChip
import app.morphe.gui.ui.components.MorphePanel
import app.morphe.gui.ui.components.MorpheScrollbarStyle
import app.morphe.gui.ui.components.SlimSearchField
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.screens.home.ApkInfo
import app.morphe.gui.ui.screens.home.DeviceAppInfo
import app.morphe.gui.ui.screens.home.PatchedAppState
import app.morphe.gui.ui.screens.home.RecallUpdateInfo
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.ui.theme.outlineBorder
import app.morphe.gui.ui.theme.outlineStroke
import app.morphe.gui.ui.theme.panelFill
import app.morphe.gui.util.AppIconSource
import app.morphe.gui.util.DeviceMonitor
import app.morphe.gui.util.rememberAppIcon
import app.morphe.gui.ui.components.MorpheDropdown
import app.morphe.gui.ui.components.MorpheDropdownItem
import app.morphe.morphe_desktop.generated.resources.*
import java.io.File
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Pane displaying a searchable, sortable list of supported applications. */
@Composable
internal fun SupportedAppsListPane(
    supportedApps: List<SupportedApp>,
    patchedStates: Map<String, PatchedAppState> = emptyMap(),
    patchedRecords: List<PatchedAppRecord> = emptyList(),
    deviceAppInfo: Map<String, DeviceAppInfo> = emptyMap(),
    updateInfoByPackage: Map<String, RecallUpdateInfo> = emptyMap(),
    selectedApk: ApkInfo? = null,
    onShowDetail: (PatchedAppRecord) -> Unit = {},
    filter: AppListFilter = AppListFilter.ALL,
    onFilterChange: (AppListFilter) -> Unit = {},
    sourceNamesByPackage: Map<String, List<String>>,
    isLoading: Boolean,
    loadError: String?,
    onRetry: () -> Unit,
    onManageSources: () -> Unit = {},
    sortMode: HomeAppSortMode = HomeAppSortMode.RECOMMENDED,
    onSortModeChange: (HomeAppSortMode) -> Unit = {},
    isSimple: Boolean = false,
    onPatchOutdatedAll: () -> Unit = {},
    onBatchPatchSelected: (List<String>) -> Unit = {},
    hasPendingBatchQueue: Boolean = false,
    isBatchRunning: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }
    var expandedPackage by remember { mutableStateOf<String?>(null) }
    var showCommunityNoticeDialog by remember { mutableStateOf(false) }
    var isSelecting by remember { mutableStateOf(false) }
    var selectedPackages by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(isBatchRunning) {
        if (isBatchRunning && isSelecting) {
            isSelecting = false
            selectedPackages = emptySet()
        }
    }

    val configRepo: ConfigRepository = koinInject()
    val uriHandler = LocalUriHandler.current
    val coroutineScope = rememberCoroutineScope()
    val showMoreAppsCard = LocalShowMoreAppsCard.current.value

    val matching = remember(searchQuery, supportedApps) {
        if (searchQuery.isBlank()) supportedApps
        else supportedApps.filter {
            it.displayName.contains(searchQuery, ignoreCase = true) ||
            it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }
    val installedPackages = remember(deviceAppInfo) {
        deviceAppInfo.filterValues { it.installed }.keys
    }
    val patchedAtByPackage = remember(patchedRecords) {
        patchedRecords.associate { it.packageName to it.patchedAt }
    }
    val inputApkByPackage = remember(patchedRecords) {
        patchedRecords.associate { it.packageName to File(it.inputApkPath) }
    }
    val deviceId = DeviceMonitor.state.collectAsState().value.selectedDevice?.takeIf { it.isReady }?.id
    val order = remember(sortMode) { sortMode.comparator() }
    val filtered = remember(matching, order, patchedStates, installedPackages, patchedAtByPackage) {
        matching.sortedWith(
            compareBy(order) { it.sortKeys(patchedStates, installedPackages, patchedAtByPackage) }
        )
    }
    val matchingRecords = remember(searchQuery, patchedRecords) {
        if (searchQuery.isBlank()) patchedRecords
        else patchedRecords.filter {
            it.displayName.contains(searchQuery, ignoreCase = true) ||
            it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }
    val filteredRecords = remember(matchingRecords, order, patchedStates, installedPackages) {
        matchingRecords.sortedWith(
            compareBy(order) { it.sortKeys(patchedStates, installedPackages) }
        )
    }
    val activeCount = if (filter == AppListFilter.YOURS) patchedRecords.size else supportedApps.size

    // Collapse selection if the expanded app no longer matches filters.
    LaunchedEffect(searchQuery, filtered) {
        if (expandedPackage != null && filtered.none { it.packageName == expandedPackage }) {
            expandedPackage = null
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        val updateCount = patchedStates.values.count { it == PatchedAppState.PATCHED_WITH_UPDATES }
        if (filter == AppListFilter.ALL && updateCount > 0) {
            PatchedUpdatesBanner(
                count = updateCount,
                onView = { onFilterChange(AppListFilter.YOURS) },
                onPatchAll = if (isBatchRunning) null else onPatchOutdatedAll,
            )
        }

        val visiblePackages = remember(filter, filtered, filteredRecords) {
            if (filter == AppListFilter.YOURS) {
                filteredRecords.map { it.packageName }
            } else {
                filtered.map { it.packageName }
            }
        }
        val toggleSelection: (String) -> Unit = { pkg ->
            selectedPackages = if (pkg in selectedPackages) selectedPackages - pkg else selectedPackages + pkg
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(end = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AppListFilterChips(
                filter = filter,
                onSelect = onFilterChange,
                allCount = supportedApps.size,
                yourCount = patchedRecords.size,
                modifier = Modifier.weight(1f),
            )
            if (activeCount > 0 && !isBatchRunning) {
                MorpheChoiceChip(
                    text = stringResource(Res.string.batch_home_multiselect_toggle),
                    active = isSelecting,
                    icon = MorpheIcons.Checklist,
                    onClick = {
                        isSelecting = !isSelecting
                        if (!isSelecting) {
                            selectedPackages = emptySet()
                        } else {
                            expandedPackage = null
                        }
                    },
                )
            }
            MorpheDropdown(
                label = sortMode.label,
                items = HomeAppSortMode.entries.map { mode ->
                    MorpheDropdownItem(mode.label) { onSortModeChange(mode) }
                },
                modifier = Modifier.width(160.dp),
            )
        }

        if (activeCount > 4) {
            Box(modifier = Modifier.fillMaxWidth().padding(end = 12.dp)) {
                SlimSearchField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = stringResource(Res.string.filter_apps_hint),
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (filter == AppListFilter.YOURS) {
            YourAppsListBody(
                patchedRecords = patchedRecords,
                filteredRecords = filteredRecords,
                searchQuery = searchQuery,
                patchedStates = patchedStates,
                deviceAppInfo = deviceAppInfo,
                updateInfoByPackage = updateInfoByPackage,
                appIconColorByPackage = supportedApps.associate { it.packageName to (it.appIconColor ?: "") }.filterValues { it.isNotEmpty() },
                formFactorByPackage = supportedApps.mapNotNull { app -> app.formFactor?.let { app.packageName to it } }.toMap(),
                onShowDetail = onShowDetail,
                selectionMode = isSelecting,
                selectedPackages = selectedPackages,
                onToggleSelect = toggleSelection,
                modifier = Modifier.weight(1f, fill = false),
            )
        } else when {
            isLoading -> {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    repeat(4) { idx ->
                        SkeletonAppRow(
                            staggerOffsetMs = idx * 120,
                        )
                    }
                }
            }
            loadError != null -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.home_list_load_failed),
                        style = MorpheTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = MorpheTheme.colors.error,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = loadError,
                        style = MorpheTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MorpheButton(
                            onClick = onRetry,
                            label = stringResource(Res.string.retry),
                            variant = MorpheButtonVariant.OUTLINED,
                            size = MorpheButtonSize.COMPACT,
                        )
                        // Provide access to source management to resolve broken bundles.
                        MorpheButton(
                            onClick = onManageSources,
                            label = stringResource(Res.string.home_header_manage_sources_button),
                            variant = MorpheButtonVariant.OUTLINED,
                            size = MorpheButtonSize.COMPACT,
                        )
                    }
                }
            }
            filtered.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (searchQuery.isBlank()) stringResource(Res.string.home_list_no_supported_apps)
                               else stringResource(Res.string.home_list_no_apps_match, searchQuery),
                        style = MorpheTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                val listState = rememberLazyListState()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(items = filtered, key = { it.packageName }) { app ->
                            SupportedAppListRow(
                                app = app,
                                isExpanded = !isSelecting && expandedPackage == app.packageName,
                                isSimple = isSimple,
                                onClick = {
                                    if (isSelecting) {
                                        toggleSelection(app.packageName)
                                    } else {
                                        expandedPackage = if (expandedPackage == app.packageName) null
                                        else app.packageName
                                    }
                                },
                                patchSourceNames = sourceNamesByPackage[app.packageName] ?: emptyList(),
                                patchedState = patchedStates[app.packageName] ?: PatchedAppState.NEVER_PATCHED,
                                deviceInfo = deviceAppInfo[app.packageName],
                                icon = rememberAppIcon(
                                    listOfNotNull(
                                        selectedApk?.takeIf { it.packageName == app.packageName }?.let { AppIconSource.Apk(File(it.filePath)) },
                                        inputApkByPackage[app.packageName]?.let { AppIconSource.Apk(it) },
                                        deviceId?.let { AppIconSource.Device(it, app.packageName) },
                                    ),
                                    packageName = app.packageName,
                                ),
                                selectionMode = isSelecting,
                                isSelected = app.packageName in selectedPackages,
                            )
                        }
                        if (showMoreAppsCard && searchQuery.isBlank() && !isSelecting) {
                            item(key = "more_apps_card") {
                                MoreAppsCard(
                                    onClick = {
                                        coroutineScope.launch {
                                            val config = configRepo.loadConfig()
                                            if (config.communityNoticeAcknowledged) {
                                                uriHandler.openUri(AppConstants.COMMUNITY_PATCHES_URL)
                                            } else {
                                                showCommunityNoticeDialog = true
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                    Box(
                        modifier = Modifier.matchParentSize(),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        VerticalScrollbar(
                            modifier = Modifier.fillMaxHeight(),
                            adapter = rememberScrollbarAdapter(listState),
                            style = MorpheScrollbarStyle(),
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = isSelecting,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            val allVisibleSelected = visiblePackages.isNotEmpty() && visiblePackages.all { it in selectedPackages }
            MorphePanel(
                color = panelFill,
                border = outlineStroke(MorpheTheme.colors.primary.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, end = 12.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = pluralStringResource(Res.plurals.batch_selected_count, selectedPackages.size, selectedPackages.size),
                        style = MorpheTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (visiblePackages.isNotEmpty()) {
                        MorpheButton(
                            label = stringResource(
                                if (allVisibleSelected) Res.string.batch_home_multiselect_deselect_all
                                else Res.string.batch_home_multiselect_select_all
                            ),
                            variant = MorpheButtonVariant.GHOST,
                            size = MorpheButtonSize.COMPACT,
                            onClick = {
                                selectedPackages = if (allVisibleSelected) {
                                    selectedPackages - visiblePackages.toSet()
                                } else {
                                    selectedPackages + visiblePackages
                                }
                            },
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    MorpheButton(
                        label = stringResource(Res.string.cancel),
                        variant = MorpheButtonVariant.GHOST,
                        size = MorpheButtonSize.COMPACT,
                        onClick = {
                            isSelecting = false
                            selectedPackages = emptySet()
                        },
                    )
                    MorpheButton(
                        label = stringResource(
                            if (hasPendingBatchQueue) Res.string.batch_home_multiselect_add
                            else Res.string.batch_home_multiselect_patch,
                            selectedPackages.size,
                        ),
                        icon = MorpheIcons.PlaylistAdd,
                        variant = MorpheButtonVariant.FILLED,
                        size = MorpheButtonSize.COMPACT,
                        enabled = selectedPackages.isNotEmpty(),
                        onClick = {
                            val chosen = selectedPackages.toList()
                            isSelecting = false
                            selectedPackages = emptySet()
                            onBatchPatchSelected(chosen)
                        },
                    )
                }
            }
        }
    }

    if (showCommunityNoticeDialog) {
        CommunityPatchesNoticeDialog(
            onDismiss = { showCommunityNoticeDialog = false },
            onConfirm = {
                showCommunityNoticeDialog = false
                coroutineScope.launch {
                    configRepo.setCommunityNoticeAcknowledged(true)
                    uriHandler.openUri(AppConstants.COMMUNITY_PATCHES_URL)
                }
            }
        )
    }
}

/** Loading skeleton matching [SupportedAppListRow] dimensions. */
@Composable
internal fun SkeletonAppRow(
    staggerOffsetMs: Int,
) {
    val infinite = rememberInfiniteTransition(label = "skeletonPulse")
    val alpha by infinite.animateFloat(
        initialValue = 0.06f,
        targetValue = 0.16f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, delayMillis = staggerOffsetMs),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )
    val baseColor = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
    val cardBg = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
    val outline = MaterialTheme.colorScheme.outline.copy(alpha = 0.10f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MorpheTheme.shapes.medium)
            .background(cardBg)
            .outlineBorder(outline, MorpheTheme.shapes.medium)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(MorpheTheme.shapes.small)
                    .background(baseColor),
            )
            Spacer(Modifier.width(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(
                    modifier = Modifier
                        .height(10.dp)
                        .width(140.dp)
                        .clip(MorpheTheme.shapes.small)
                        .background(baseColor),
                )
                Box(
                    modifier = Modifier
                        .height(8.dp)
                        .width(180.dp)
                        .clip(MorpheTheme.shapes.small)
                        .background(baseColor.copy(alpha = alpha * 0.6f)),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier
                    .height(20.dp)
                    .width(110.dp)
                    .clip(MorpheTheme.shapes.small)
                    .background(baseColor),
            )
            Box(
                modifier = Modifier
                    .height(20.dp)
                    .width(130.dp)
                    .clip(MorpheTheme.shapes.small)
                    .background(baseColor.copy(alpha = alpha * 0.7f)),
            )
        }
    }
}
