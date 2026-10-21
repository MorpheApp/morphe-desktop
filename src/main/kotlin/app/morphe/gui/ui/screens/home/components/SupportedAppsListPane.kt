/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.home.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.morphe.engine.model.PatchedAppRecord
import app.morphe.engine.model.SupportedApp
import app.morphe.gui.ui.components.MorpheButton
import app.morphe.gui.ui.components.MorpheButtonSize
import app.morphe.gui.ui.components.MorpheButtonVariant
import app.morphe.gui.ui.components.MorpheScrollbarStyle
import app.morphe.gui.ui.components.SlimSearchField
import app.morphe.gui.ui.screens.home.DeviceAppInfo
import app.morphe.gui.ui.screens.home.PatchedAppState
import app.morphe.gui.ui.screens.home.RecallUpdateInfo
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.ui.components.MorpheDropdown
import app.morphe.gui.ui.components.MorpheDropdownItem
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** Pane displaying a searchable, sortable list of supported applications. */
@Composable
internal fun SupportedAppsListPane(
    supportedApps: List<SupportedApp>,
    patchedStates: Map<String, PatchedAppState> = emptyMap(),
    patchedRecords: List<PatchedAppRecord> = emptyList(),
    deviceAppInfo: Map<String, DeviceAppInfo> = emptyMap(),
    updateInfoByPackage: Map<String, RecallUpdateInfo> = emptyMap(),
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
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }
    var expandedPackage by remember { mutableStateOf<String?>(null) }

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

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
      val paneMaxHeight = maxHeight
      Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .align(Alignment.TopCenter),
      ) {
        val updateCount = patchedStates.values.count { it == PatchedAppState.PATCHED_WITH_UPDATES }
        if (filter == AppListFilter.ALL && updateCount > 0) {
            PatchedUpdatesBanner(updateCount) { onFilterChange(AppListFilter.YOURS) }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(end = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppListFilterChips(
                filter = filter,
                onSelect = onFilterChange,
                allCount = supportedApps.size,
                yourCount = patchedRecords.size,
                modifier = Modifier.weight(1f),
            )
            MorpheDropdown(
                label = sortMode.label,
                items = HomeAppSortMode.entries.map { mode ->
                    MorpheDropdownItem(mode.label) { onSortModeChange(mode) }
                },
                modifier = Modifier.width(170.dp),
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
                onShowDetail = onShowDetail,
                paneMaxHeight = paneMaxHeight,
                showSearch = activeCount > 4,
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
                val headerSearchAllowance =
                    if (supportedApps.size > 4) 68.dp else 22.dp
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(
                            max = (paneMaxHeight - headerSearchAllowance)
                                .coerceAtLeast(120.dp)
                        ),
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(items = filtered, key = { it.packageName }) { app ->
                            SupportedAppListRow(
                                app = app,
                                isExpanded = expandedPackage == app.packageName,
                                onClick = {
                                    expandedPackage = if (expandedPackage == app.packageName) null
                                                      else app.packageName
                                },
                                patchSourceNames = sourceNamesByPackage[app.packageName] ?: emptyList(),
                                patchedState = patchedStates[app.packageName] ?: PatchedAppState.NEVER_PATCHED,
                                deviceInfo = deviceAppInfo[app.packageName],
                            )
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
      }
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
            .border(1.dp, outline, MorpheTheme.shapes.medium)
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
