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
import app.morphe.gui.ui.components.morpheScrollbarStyle
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
import app.morphe.gui.ui.components.MorpheDropdown
import app.morphe.gui.ui.components.MorpheDropdownItem
import app.morphe.gui.ui.screens.home.HomeAppSortMode
import app.morphe.gui.ui.screens.home.comparator
import app.morphe.gui.ui.screens.home.sortKeys

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
    patchedStates: Map<String, PatchedAppState> = emptyMap(),
    patchedRecords: List<PatchedAppRecord> = emptyList(),
    deviceAppInfo: Map<String, DeviceAppInfo> = emptyMap(),
    updateInfoByPackage: Map<String, RecallUpdateInfo> = emptyMap(),
    onInstall: (String) -> Unit = {},
    installingPackage: String? = null,
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
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current

    var searchQuery by remember { mutableStateOf("") }
    var expandedPackage by remember { mutableStateOf<String?>(null) }

    val matching = if (searchQuery.isBlank()) supportedApps
    else supportedApps.filter {
        it.displayName.contains(searchQuery, ignoreCase = true) ||
        it.packageName.contains(searchQuery, ignoreCase = true)
    }
    val installedPackages = deviceAppInfo.filterValues { it.installed }.keys
    val patchedAtByPackage = patchedRecords.associate { it.packageName to it.patchedAt }
    val order = sortMode.comparator()
    val filtered = matching.sortedWith(
        compareBy(order) { it.sortKeys(patchedStates, installedPackages, patchedAtByPackage) }
    )
    val matchingRecords = if (searchQuery.isBlank()) patchedRecords
    else patchedRecords.filter {
        it.displayName.contains(searchQuery, ignoreCase = true) ||
        it.packageName.contains(searchQuery, ignoreCase = true)
    }
    val filteredRecords = matchingRecords.sortedWith(
        compareBy(order) { it.sortKeys(patchedStates, installedPackages) }
    )
    val activeCount = if (filter == AppListFilter.YOURS) patchedRecords.size else supportedApps.size

    // Collapse if the currently expanded app filters out.
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
        // ── On-open update notice: jumps to "Your apps" where each is badged ──
        val updateCount = patchedStates.values.count { it == PatchedAppState.PATCHED_WITH_UPDATES }
        if (filter == AppListFilter.ALL && updateCount > 0) {
            PatchedUpdatesBanner(updateCount) { onFilterChange(AppListFilter.YOURS) }
        }

        // ── Filter: ALL APPS · YOUR APPS ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
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

        // ── Search field ──
        if (activeCount > 4) {
            // Match the LazyColumn's right padding so the field aligns with cards.
            // Dp.Unspecified disables the default 340dp cap so the field fills
            // the pane width like the cards below it.
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
                onInstall = onInstall,
                installingPackage = installingPackage,
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
                                patchSourceNames = sourceNamesByPackage[app.packageName] ?: emptyList(),
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
                            "Filter apps…",
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
