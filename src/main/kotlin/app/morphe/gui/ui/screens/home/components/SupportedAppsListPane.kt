/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.home.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.engine.model.PatchedAppRecord
import app.morphe.gui.data.model.SupportedApp
import app.morphe.gui.data.model.PatchSource
import app.morphe.gui.data.model.AppSortPreference
import app.morphe.gui.ui.components.RepositoryLinkText
import app.morphe.gui.ui.components.morpheScrollbarStyle
import app.morphe.gui.ui.components.MorpheTooltip
import app.morphe.gui.ui.components.TooltipText
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.screens.home.DeviceAppInfo
import app.morphe.gui.ui.screens.home.PatchedAppState
import app.morphe.gui.ui.screens.home.RecallUpdateInfo
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheDimens
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.ui.theme.MorpheAccentColors
import app.morphe.gui.ui.theme.MorpheCornerStyle
import app.morphe.gui.util.DeviceAppDiscoverySnapshot
import app.morphe.gui.util.DevicePatchability
import app.morphe.gui.util.DevicePatchSourceAvailability
import app.morphe.gui.util.DeviceUpdateOwner
import app.morphe.gui.util.DiscoveredDeviceApp
import app.morphe.gui.util.InstalledAppType
import app.morphe.gui.util.AppLabelIndexState
import app.morphe.gui.util.RepositoryLinks

// ============================================================================
// SUPPORTED APPS LIST PANE
// ============================================================================

/**
 * Vertical-list variant of the supported-apps display used in the side-by-side
 * layout. Search field at top, scrollable LazyColumn of [SupportedAppListRow]
 * below. Single-expand semantics. Clicking a row expands it and collapses any
 * previously-expanded one.
 */
@Composable
internal fun SupportedAppsListPane(
    supportedApps: List<SupportedApp>,
    currentSources: List<PatchSource>,
    patchedStates: Map<String, PatchedAppState> = emptyMap(),
    patchedRecords: List<PatchedAppRecord> = emptyList(),
    deviceAppInfo: Map<String, DeviceAppInfo> = emptyMap(),
    deviceDiscovery: DeviceAppDiscoverySnapshot? = null,
    deviceDisplayName: String? = null,
    onRefreshDeviceApps: () -> Unit = {},
    onVisibleDevicePackages: (String, List<String>) -> Unit = { _, _ -> },
    onImportDeviceApp: (DiscoveredDeviceApp) -> Unit = {},
    importingDevicePackage: String? = null,
    deviceImportStatus: String? = null,
    updateInfoByPackage: Map<String, RecallUpdateInfo> = emptyMap(),
    onRepatch: (String) -> Unit = {},
    onForget: (String) -> Unit = {},
    onUpdate: (String) -> Unit = {},
    onInstall: (String) -> Unit = {},
    installingPackage: String? = null,
    onUninstall: (String) -> Unit = {},
    uninstallingPackage: String? = null,
    onShowDetail: (PatchedAppRecord) -> Unit = {},
    filter: AppListFilter = AppListFilter.ALL,
    onFilterChange: (AppListFilter) -> Unit = {},
    sortPreferences: Map<String, AppSortPreference> = emptyMap(),
    onSortPreferenceChange: (String, AppSortPreference) -> Unit = { _, _ -> },
    sourcesByPackage: Map<String, List<PatchSource>>,
    isLoading: Boolean,
    loadError: String?,
    onRetry: () -> Unit,
    onManageSources: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current

    var searchQuery by remember { mutableStateOf("") }
    var expandedPackage by remember { mutableStateOf<String?>(null) }
    var deviceAppsFilter by remember { mutableStateOf(DeviceAppsFilter.KNOWN) }
    val sortState = resolveSortState(filter, sortPreferences[filter.name])
    val onSortChange: (AppSortState) -> Unit = { updated ->
        onSortPreferenceChange(filter.name, updated.toPreference())
    }

    val filtered = sortSupportedApps(if (searchQuery.isBlank()) supportedApps else supportedApps.filter {
        it.displayName.contains(searchQuery, ignoreCase = true) ||
        it.packageName.contains(searchQuery, ignoreCase = true)
    }, sortState)
    val filteredRecords = sortPatchedApps(if (searchQuery.isBlank()) patchedRecords else patchedRecords.filter {
        it.displayName.contains(searchQuery, ignoreCase = true) ||
        it.packageName.contains(searchQuery, ignoreCase = true)
    }, sortState)
    val allDeviceApps = deviceDiscovery?.apps.orEmpty()
    val filteredDeviceApps = sortDeviceApps(
        filterDeviceApps(allDeviceApps, deviceAppsFilter, searchQuery),
        sortState,
    )

    // Collapse if the currently expanded app filters out.
    LaunchedEffect(searchQuery, filtered) {
        if (expandedPackage != null && filtered.none { it.packageName == expandedPackage }) {
            expandedPackage = null
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
      val paneMaxHeight = maxHeight
      Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            // Keep navigation, search and sorting pinned to the top. Centering
            // the height-dependent column made the controls jump whenever a
            // search, connection or loading state changed the list body.
            .align(Alignment.TopStart),
      ) {
        // ── On-open update notice: jumps to "Your apps" where each is badged ──
        val updateCount = patchedStates.values.count { it == PatchedAppState.PATCHED_WITH_UPDATES }
        if (filter == AppListFilter.ALL && updateCount > 0) {
            PatchedUpdatesBanner(updateCount) { onFilterChange(AppListFilter.YOURS) }
        }

        // ── Filter: ALL APPS · YOUR APPS ──
        AppListFilterChips(
            filter = filter,
            onSelect = onFilterChange,
            allCount = supportedApps.size,
            yourCount = patchedRecords.size,
            deviceCount = deviceAppsTotalCount(allDeviceApps),
        )

        // ── Search field ──
        Box(modifier = Modifier.fillMaxWidth().padding(end = 12.dp)) {
            SlimSearchField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                font = font,
                corners = corners,
                accents = accents,
                maxWidth = Dp.Unspecified,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (filter == AppListFilter.DEVICE) {
            DeviceAppFilterControls(
                apps = allDeviceApps,
                filter = deviceAppsFilter,
                onFilterChange = { deviceAppsFilter = it },
            )
        }

        AppSortControls(
            filter = filter,
            state = sortState,
            onStateChange = onSortChange,
            font = font,
            corners = corners,
            accents = accents,
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (filter == AppListFilter.DEVICE) {
            DeviceAppsListBody(
                snapshot = deviceDiscovery,
                deviceDisplayName = deviceDisplayName,
                filteredApps = filteredDeviceApps,
                searchQuery = searchQuery,
                onRefresh = onRefreshDeviceApps,
                onVisiblePackages = onVisibleDevicePackages,
                onImport = onImportDeviceApp,
                importingPackage = importingDevicePackage,
                importStatus = deviceImportStatus,
                sourcesByPackage = sourcesByPackage,
                paneMaxHeight = paneMaxHeight,
                showSearch = true,
            )
        } else if (filter == AppListFilter.YOURS) {
            YourAppsListBody(
                patchedRecords = patchedRecords,
                currentSources = currentSources,
                filteredRecords = filteredRecords,
                searchQuery = searchQuery,
                patchedStates = patchedStates,
                deviceAppInfo = deviceAppInfo,
                updateInfoByPackage = updateInfoByPackage,
                appIconColorByPackage = supportedApps.associate { it.packageName to (it.appIconColor ?: "") }.filterValues { it.isNotEmpty() },
                onShowDetail = onShowDetail,
                onRepatch = onRepatch,
                onUpdate = onUpdate,
                onForget = onForget,
                onInstall = onInstall,
                installingPackage = installingPackage,
                onUninstall = onUninstall,
                uninstallingPackage = uninstallingPackage,
                paneMaxHeight = paneMaxHeight,
                showSearch = true,
            )
        } else when {
            isLoading -> {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    repeat(4) { idx ->
                        SkeletonAppRow(
                            corners = corners,
                            // Slight stagger: each row pulses 120ms after the previous
                            // so the skeleton list feels alive instead of lock-step.
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
                        text = "Load failed",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = font,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = loadError,
                        fontSize = 11.sp,
                        fontFamily = font,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onRetry,
                            shape = RoundedCornerShape(corners.small),
                        ) {
                            Text(
                                "Retry",
                                fontFamily = font,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
                        // Always offer a way to the source manager here: when a bundle is
                        // broken (e.g. needs a newer patcher), fixing it means removing or
                        // re-pointing that source, so it must be reachable from the error.
                        OutlinedButton(
                            onClick = onManageSources,
                            shape = RoundedCornerShape(corners.small),
                        ) {
                            Text(
                                "Manage sources",
                                fontFamily = font,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
            filtered.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (searchQuery.isBlank()) "No supported apps"
                               else "No apps match \"$searchQuery\"",
                        fontSize = 13.sp,
                        fontFamily = font,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                val listState = rememberLazyListState()
                // Cap the list at the pane's available height (minus a header
                // + optional search allowance) so it scrolls when there are
                // many apps but wraps tight + lets the Column center when few.
                // Tight estimate: header ~22dp, search field (only shown when
                // >4 apps) ~46dp. Anything over-budgeted leaves dead space
                // above the list when content fills, so be precise.
                val headerSearchAllowance =
                    if (supportedApps.size > 4) 106.dp else 60.dp
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
                        // Scrollbar is 6dp wide and sits at the Box's right edge.
                        // 6 (scrollbar width) + 6 (visible gap) = 12dp keeps content
                        // fully clear of the scrollbar with breathing room.
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
                                patchSources = sourcesByPackage[app.packageName] ?: emptyList(),
                                patchedState = patchedStates[app.packageName] ?: PatchedAppState.NEVER_PATCHED,
                                deviceInfo = deviceAppInfo[app.packageName],
                            )
                        }
                    }
                    // Wrap the scrollbar in a matchParentSize Box so it
                    // tracks the LazyColumn's wrapped height WITHOUT forcing
                    // the outer Box to fill its heightIn(max=…) cap. Then
                    // align CenterEnd + wrap width to keep it pinned at the
                    // right edge at its natural 6dp thickness.
                    Box(
                        modifier = Modifier.matchParentSize(),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        VerticalScrollbar(
                            modifier = Modifier.fillMaxHeight(),
                            adapter = rememberScrollbarAdapter(listState),
                            style = morpheScrollbarStyle(),
                        )
                    }
                }
            }
        }
      }
    }
}

@Composable
private fun AppSortControls(
    filter: AppListFilter,
    state: AppSortState,
    onStateChange: (AppSortState) -> Unit,
    font: FontFamily,
    corners: MorpheCornerStyle,
    accents: MorpheAccentColors,
) {
    var menuExpanded by remember(filter) { mutableStateOf(false) }
    val dimens = LocalMorpheDimens.current
    val directionLabel = if (state.direction == AppSortDirection.ASCENDING) {
        "Sort ascending"
    } else {
        "Sort descending"
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            MorpheTooltip("Choose sort criterion") {
                Row(
                    modifier = Modifier
                        .height(dimens.controlHeight)
                        .clip(RoundedCornerShape(corners.small))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
                            RoundedCornerShape(corners.small),
                        )
                        .clickable { menuExpanded = true }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "Sort: ${state.criterion.label}",
                        fontFamily = font,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Icon(
                        MorpheIcons.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                shape = RoundedCornerShape(corners.small),
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                availableSortCriteria(filter).forEach { criterion ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                criterion.label,
                                fontFamily = font,
                                fontSize = 11.sp,
                                color = if (criterion == state.criterion) accents.primary
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        },
                        onClick = {
                            onStateChange(state.copy(criterion = criterion))
                            menuExpanded = false
                        },
                    )
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        MorpheTooltip(directionLabel) {
            IconButton(
                onClick = {
                    onStateChange(
                        state.copy(
                            direction = if (state.direction == AppSortDirection.ASCENDING) {
                                AppSortDirection.DESCENDING
                            } else {
                                AppSortDirection.ASCENDING
                            },
                        ),
                    )
                },
                modifier = Modifier.size(dimens.controlHeight),
            ) {
                Icon(
                    imageVector = if (state.direction == AppSortDirection.ASCENDING) {
                        MorpheIcons.KeyboardArrowUp
                    } else {
                        MorpheIcons.KeyboardArrowDown
                    },
                    contentDescription = directionLabel,
                    modifier = Modifier.size(18.dp),
                    tint = accents.primary,
                )
            }
        }
    }
}

@Composable
private fun DeviceAppFilterControls(
    apps: List<app.morphe.gui.util.DiscoveredDeviceApp>,
    filter: DeviceAppsFilter,
    onFilterChange: (DeviceAppsFilter) -> Unit,
) {
    val font = LocalMorpheFont.current
    val corners = LocalMorpheCorners.current
    val accents = LocalMorpheAccents.current
    val knownCount = apps.count { it.patchSourceAvailability == DevicePatchSourceAvailability.AVAILABLE }
    val unknownCount = apps.size - knownCount
    Row(
        modifier = Modifier.fillMaxWidth().padding(end = 12.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip("Known", knownCount.takeIf { it > 0 }, filter == DeviceAppsFilter.KNOWN, accents.primary, font, corners.small) {
            onFilterChange(DeviceAppsFilter.KNOWN)
        }
        FilterChip("No patch source", unknownCount.takeIf { it > 0 }, filter == DeviceAppsFilter.NO_PATCH_SOURCE, accents.primary, font, corners.small) {
            onFilterChange(DeviceAppsFilter.NO_PATCH_SOURCE)
        }
        FilterChip("All installed", apps.size.takeIf { it > 0 }, filter == DeviceAppsFilter.ALL, accents.primary, font, corners.small) {
            onFilterChange(DeviceAppsFilter.ALL)
        }
    }
}

@Composable
private fun DeviceAppsListBody(
    snapshot: DeviceAppDiscoverySnapshot?,
    deviceDisplayName: String?,
    filteredApps: List<app.morphe.gui.util.DiscoveredDeviceApp>,
    searchQuery: String,
    onRefresh: () -> Unit,
    onVisiblePackages: (String, List<String>) -> Unit,
    onImport: (DiscoveredDeviceApp) -> Unit,
    importingPackage: String?,
    importStatus: String?,
    sourcesByPackage: Map<String, List<PatchSource>>,
    paneMaxHeight: Dp,
    showSearch: Boolean,
) {
    val font = LocalMorpheFont.current
    val corners = LocalMorpheCorners.current
    val accents = LocalMorpheAccents.current
    val dimens = LocalMorpheDimens.current
    if (snapshot != null && snapshot.labelIndexState != AppLabelIndexState.COMPLETE) {
        Text(
            when (snapshot.labelIndexState) {
                AppLabelIndexState.INDEXING ->
                    "Indexing app names… ${snapshot.indexedLabelCount}/${snapshot.apps.size}"
                AppLabelIndexState.INCOMPLETE_RETRYABLE ->
                    "App-name index incomplete — Refresh retries unavailable labels."
                else -> "App-name index pending…"
            },
            modifier = Modifier.fillMaxWidth().padding(end = 12.dp, bottom = 6.dp),
            fontFamily = font,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(end = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            MorpheTooltip(snapshot?.deviceSerial?.let { "ADB serial: $it" } ?: "No ready device selected") {
                Text(
                    deviceDisplayName ?: "No ready device selected",
                    fontFamily = font,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        MorpheTooltip(TooltipText.DEVICE_REFRESH) {
            TextButton(
                onClick = onRefresh,
                enabled = snapshot != null && snapshot.isRefreshing.not(),
                modifier = Modifier.height(dimens.controlHeight),
                contentPadding = PaddingValues(horizontal = dimens.controlHorizontalPadding, vertical = 0.dp),
            ) {
                Icon(MorpheIcons.Refresh, contentDescription = "Refresh device apps", modifier = Modifier.size(dimens.iconInControl))
                Spacer(Modifier.width(5.dp))
                Text(if (snapshot?.isRefreshing == true) "Refreshing…" else "Refresh", fontSize = 10.sp)
            }
        }
    }
    when {
        snapshot == null -> DeviceDiscoveryMessage("Connect and select an authorized ADB device.")
        snapshot.isRefreshing && snapshot.apps.isEmpty() -> DeviceDiscoveryMessage("Reading installed apps…")
        snapshot.error != null -> DeviceDiscoveryMessage(snapshot.error)
        filteredApps.isEmpty() -> DeviceDiscoveryMessage(
            if (searchQuery.isBlank()) "No installed apps match this filter."
            else "No device apps match \"$searchQuery\"."
        )
        else -> {
            val listState = rememberLazyListState()
            LaunchedEffect(snapshot.deviceSerial, listState, filteredApps) {
                snapshotFlow {
                    listState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String }
                }.collect { packages -> onVisiblePackages(snapshot.deviceSerial, packages) }
            }
            val headerAllowance = if (showSearch) 158.dp else 112.dp
            Box(Modifier.fillMaxWidth().heightIn(max = (paneMaxHeight - headerAllowance).coerceAtLeast(120.dp))) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        Column(
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(corners.medium))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.14f), RoundedCornerShape(corners.medium))
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(app.displayName, fontFamily = font, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            if (app.displayName != app.packageName) {
                                Text(app.packageName, fontFamily = font, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                installedVersionLabel(app.versionName, app.versionCode),
                                fontFamily = font,
                                fontSize = 10.sp,
                            )
                            Text(devicePatchabilityLabel(app.patchability, app.patchNames), fontFamily = font, fontSize = 10.sp)
                            Text(
                                installedAppTypeLabel(app.installedAppType),
                                fontFamily = font,
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (app.patchSourceAvailability == DevicePatchSourceAvailability.AVAILABLE) {
                                MorpheTooltip(TooltipText.ownership(app.updateOwner)) {
                                    Text(
                                        deviceOwnerLabel(app.updateOwner),
                                        fontFamily = font,
                                        fontSize = 10.sp,
                                        color = when (app.updateOwner) {
                                            DeviceUpdateOwner.DesktopManaged, DeviceUpdateOwner.Unsupported -> MaterialTheme.colorScheme.primary
                                            DeviceUpdateOwner.NotApplicable,
                                            is DeviceUpdateOwner.Unavailable -> MaterialTheme.colorScheme.onSurfaceVariant
                                            else -> MaterialTheme.colorScheme.error
                                        },
                                    )
                                }
                            }
                            val appSources = sourcesByPackage[app.packageName].orEmpty()
                            if (appSources.isNotEmpty()) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("Sources:", fontFamily = font, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    appSources.forEach { source ->
                                        RepositoryLinkText(
                                            text = source.name,
                                            link = RepositoryLinks.resolve(source),
                                            fontSize = 9.sp,
                                            fontFamily = font,
                                        )
                                    }
                                }
                            }
                            if (app.patchability == DevicePatchability.PATCHABLE && app.versionCode != null) {
                                val importing = importingPackage == app.packageName
                                MorpheTooltip(TooltipText.DEVICE_IMPORT) {
                                    OutlinedButton(
                                        onClick = { onImport(app) },
                                        enabled = importingPackage == null,
                                        modifier = Modifier.height(dimens.controlHeight),
                                        contentPadding = PaddingValues(horizontal = dimens.controlHorizontalPadding, vertical = 0.dp),
                                    ) {
                                        if (importing) {
                                            CircularProgressIndicator(Modifier.size(13.dp), strokeWidth = 2.dp)
                                            Spacer(Modifier.width(6.dp))
                                        }
                                        Text(
                                            if (importing) importStatus ?: "Importing…" else "Use installed app",
                                            fontFamily = font,
                                            fontSize = 10.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                VerticalScrollbar(
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(listState),
                    style = morpheScrollbarStyle(),
                )
            }
        }
    }
}

@Composable
private fun DeviceDiscoveryMessage(message: String) {
    Box(Modifier.fillMaxWidth().padding(top = 24.dp, end = 12.dp), contentAlignment = Alignment.Center) {
        Text(
            message,
            fontSize = 11.sp,
            fontFamily = LocalMorpheFont.current,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private fun devicePatchabilityLabel(status: DevicePatchability, patchNames: List<String>): String = when (status) {
    DevicePatchability.PATCHABLE -> "Patches available: ${patchNames.joinToString().ifBlank { "Yes" }}"
    DevicePatchability.VERSION_NOT_CONFIRMED -> "Patches available · version not confirmed"
    DevicePatchability.INCOMPATIBLE_VERSION -> "No compatible patch for installed version"
    DevicePatchability.NO_PATCH_SOURCE -> "No patch source"
}

private fun installedAppTypeLabel(type: InstalledAppType): String = when (type) {
    InstalledAppType.USER -> "Installed type: User app"
    InstalledAppType.SYSTEM -> "Installed type: System app"
    InstalledAppType.UNKNOWN -> "Installed type: Unknown"
}

internal fun installedVersionLabel(versionName: String?, versionCode: Long?): String = when {
    versionName != null && versionCode != null -> "Installed: $versionName ($versionCode)"
    versionName != null -> "Installed: $versionName"
    versionCode != null -> "Installed version code: $versionCode"
    else -> "Installed: unknown"
}

private fun deviceOwnerLabel(owner: DeviceUpdateOwner): String = when (owner) {
    DeviceUpdateOwner.DesktopManaged -> "Ownership: Morphe Desktop ✓"
    DeviceUpdateOwner.MorpheManager -> "Ownership: Morphe Manager · migration required"
    is DeviceUpdateOwner.Other -> "Ownership: ${owner.packageName} · migration required"
    DeviceUpdateOwner.NoOwner -> "Ownership: No update owner · migration required"
    DeviceUpdateOwner.Unsupported -> "Ownership: Not applicable on this device"
    DeviceUpdateOwner.NotApplicable -> "Ownership: Not applicable"
    is DeviceUpdateOwner.Unavailable -> "Ownership: Status unavailable"
}

/**
 * Slim, elongated search field used when third-party patches are loaded.
 * Built on BasicTextField so we can drop below the 56dp minimum height that
 * Material 3's OutlinedTextField enforces internally. Visually mirrors the
 * default OutlinedTextField (border, leading search icon, trailing clear,
 * font placeholder), just thinner and wider.
 */
@Composable
internal fun SlimSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    font: FontFamily,
    corners: MorpheCornerStyle,
    accents: MorpheAccentColors,
    maxWidth: Dp = 340.dp,
) {
    val dimens = LocalMorpheDimens.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderColor by animateColorAsState(
        if (isFocused) MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = tween(150),
        label = "slimSearchBorder"
    )

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        interactionSource = interactionSource,
        textStyle = MaterialTheme.typography.bodySmall.copy(
            fontFamily = font,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface
        ),
        cursorBrush = SolidColor(accents.primary),
        modifier = Modifier
            .widthIn(max = maxWidth)
            .fillMaxWidth()
            .height(dimens.controlHeight)
            .clip(RoundedCornerShape(corners.small))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .border(1.dp, borderColor, RoundedCornerShape(corners.small)),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    MorpheIcons.Search,
                    contentDescription = null,
                    tint = muted.copy(alpha = 0.55f),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            "Search apps or package names…",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = font,
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
                            MorpheIcons.Clear,
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

/** Loading skeleton. Ghost row that mimics SupportedAppListRow's shape. */
@Composable
internal fun SkeletonAppRow(
    corners: MorpheCornerStyle,
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
            .clip(RoundedCornerShape(corners.medium))
            .background(cardBg)
            .border(1.dp, outline, RoundedCornerShape(corners.medium))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Row 1: avatar + name/package bars
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(corners.small))
                    .background(baseColor),
            )
            Spacer(Modifier.width(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(
                    modifier = Modifier
                        .height(10.dp)
                        .width(140.dp)
                        .clip(RoundedCornerShape(corners.small))
                        .background(baseColor),
                )
                Box(
                    modifier = Modifier
                        .height(8.dp)
                        .width(180.dp)
                        .clip(RoundedCornerShape(corners.small))
                        .background(baseColor.copy(alpha = alpha * 0.6f)),
                )
            }
        }
        // Row 2: chip placeholders
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier
                    .height(20.dp)
                    .width(110.dp)
                    .clip(RoundedCornerShape(corners.small))
                    .background(baseColor),
            )
            Box(
                modifier = Modifier
                    .height(20.dp)
                    .width(130.dp)
                    .clip(RoundedCornerShape(corners.small))
                    .background(baseColor.copy(alpha = alpha * 0.7f)),
            )
        }
    }
}
