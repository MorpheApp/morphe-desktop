/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.quick.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.data.model.SupportedApp
import app.morphe.gui.ui.components.morpheScrollbarStyle
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.screens.home.components.AppCard
import app.morphe.gui.ui.theme.*
import app.morphe.gui.util.DownloadUrlResolver.openUrlAndFollowRedirects
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

// ============================================================================
// SUPPORTED APPS ROW
// ============================================================================

@Composable
internal fun SupportedAppsRow(
    supportedApps: List<SupportedApp>,
    isLoading: Boolean,
    loadError: String? = null,
    isDefaultSource: Boolean = true,
    useExperimentalVersions: Boolean = false,
    onRetry: () -> Unit = {},
    onManageSources: () -> Unit = {},
) {
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current
    val accents = LocalMorpheAccents.current
    val uriHandler = LocalUriHandler.current
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(Res.string.quick_patch_supported_apps_title),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = font,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = stringResource(Res.string.quick_patch_supported_apps_subtitle),
            fontFamily = font,
            fontSize = 12.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(max = 500.dp)
                .padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        when {
            isLoading -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = accents.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(Res.string.quick_patch_supported_apps_loading),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        fontFamily = font,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            loadError != null || supportedApps.isEmpty() -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = loadError ?: stringResource(Res.string.quick_patch_supported_apps_load_error),
                        fontSize = 11.sp,
                        fontFamily = font,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    ErrorActionPill(text = stringResource(Res.string.retry), onClick = onRetry)
                    // Retrying a source that is itself broken (bad URL, corrupt .mpp,
                    // needs a newer patcher) loops forever, so the picker has to be
                    // reachable from the error itself and not only from the header badge.
                    Spacer(modifier = Modifier.width(6.dp))
                    ErrorActionPill(text = stringResource(Res.string.quick_patch_supported_apps_change_source), onClick = onManageSources)
                }
            }
            else -> {
                // Search bar for many apps
                var searchQuery by remember { mutableStateOf("") }
                val filteredApps = if (searchQuery.isBlank()) supportedApps
                else supportedApps.filter {
                    it.displayName.contains(searchQuery, ignoreCase = true) ||
                    it.packageName.contains(searchQuery, ignoreCase = true)
                }

                if (supportedApps.size > 4) {
                    val muted = MaterialTheme.colorScheme.onSurfaceVariant
                    val searchInteraction = remember { MutableInteractionSource() }
                    val isSearchFocused by searchInteraction.collectIsFocusedAsState()
                    val searchBorder by animateColorAsState(
                        if (isSearchFocused) MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                        else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                        animationSpec = tween(150)
                    )

                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        interactionSource = searchInteraction,
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = font,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(accents.primary),
                        modifier = Modifier
                            .widthIn(max = 260.dp)
                            .fillMaxWidth()
                            .height(32.dp)
                            .clip(RoundedCornerShape(corners.small))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .border(1.dp, searchBorder, RoundedCornerShape(corners.small)),
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
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            stringResource(Res.string.filter_apps_hint),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Normal,
                                            fontFamily = font,
                                            color = muted.copy(alpha = 0.4f)
                                        )
                                    }
                                    innerTextField()
                                }
                                if (searchQuery.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(RoundedCornerShape(corners.small))
                                            .clickable { searchQuery = "" },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            MorpheIcons.Clear,
                                            contentDescription = stringResource(Res.string.clear),
                                            tint = muted.copy(alpha = 0.5f),
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (filteredApps.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(Res.string.quick_patch_supported_apps_no_matches),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = font,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                    return@Column
                }

                // Horizontal scrolling cards
                val useScrolling = filteredApps.size > 4
                val cardsScrollState = rememberScrollState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (useScrolling) Modifier.horizontalScroll(cardsScrollState) else Modifier)
                        .height(IntrinsicSize.Max)
                        // detectTapGestures (not .clickable) so scroll-wheel /
                        // two-finger gestures over this Row aren't swallowed.
                        // .clickable wraps the modifier chain in a pointer-input
                        // node that consumes scroll events on Linux/Skiko,
                        // breaking both the inner horizontalScroll and the
                        // outer page-level verticalScroll. Taps still clear
                        // the search-bar focus.
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { focusManager.clearFocus() })
                        },
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    filteredApps.forEach { app ->
                        val url = app.apkDownloadUrl

                        AppCard(
                            modifier = Modifier
                                .then(
                                    if (useScrolling) Modifier.width(170.dp)
                                    else Modifier.weight(1f)
                                )
                                .fillMaxHeight(),
                            cornerRadius = corners.small,
                            appIconColorHex = app.appIconColor,
                            interactive = false
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight()
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = app.displayName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = font,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (!isDefaultSource) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }

                                val hasExperimental = app.experimentalVersions.isNotEmpty()
                                val isExperimental = useExperimentalVersions && hasExperimental
                                val recommendedVersionText = if (app.recommendedVersion != null) {
                                    if (isExperimental) stringResource(Res.string.version_label_experimental) else stringResource(Res.string.version_label_stable)
                                } else {
                                    stringResource(Res.string.quick_patch_supported_apps_version_any)
                                }
                                
                                Text(
                                    text = recommendedVersionText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal,
                                    fontFamily = font,
                                    color = Color.White.copy(alpha = 0.6f)
                                )

                                if (url != null) {
                                    val pillInteraction = remember { MutableInteractionSource() }
                                    val isPillHovered by pillInteraction.collectIsHoveredAsState()
                                    val pillBg by animateColorAsState(
                                        if (isPillHovered) Color.White.copy(alpha = 0.26f)
                                        else Color.White.copy(alpha = 0.20f),
                                        animationSpec = tween(150)
                                    )

                                    Row(
                                        modifier = Modifier
                                            .hoverable(pillInteraction)
                                            .clip(RoundedCornerShape(corners.small))
                                            .background(pillBg, RoundedCornerShape(corners.small))
                                            .pointerHoverIcon(PointerIcon.Hand)
                                            .clickable {
                                                openUrlAndFollowRedirects(url) { resolved ->
                                                    uriHandler.openUri(resolved)
                                                }
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        val versionToDisplay = if (isExperimental) {
                                            app.experimentalVersions.firstOrNull()
                                        } else {
                                            app.recommendedVersion
                                        }
                                        
                                        val downloadText = stringResource(Res.string.update_banner_download_button)
                                        Text(
                                            text = versionToDisplay?.let { "v$it" } ?: downloadText,
                                            fontSize = 11.sp,
                                            fontFamily = font,
                                            color = Color.White,
                                            fontWeight = FontWeight.Normal
                                        )
                                        Icon(
                                            imageVector = MorpheIcons.OpenInNew,
                                            contentDescription = downloadText,
                                            tint = Color.White,
                                            modifier = Modifier.size(10.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (useScrolling && cardsScrollState.maxValue > 0) {
                    Spacer(Modifier.height(6.dp))
                    HorizontalScrollbar(
                        adapter = rememberScrollbarAdapter(cardsScrollState),
                        modifier = Modifier.fillMaxWidth(),
                        style = morpheScrollbarStyle()
                    )
                }
            }
        }
    }
}

// ============================================================================
// ERROR ACTION PILL
// ============================================================================

/** Outlined action used in the patch-load error row. Matches the quiet, pill
 *  style of this screen rather than a Material button. */
@Composable
private fun ErrorActionPill(
    text: String,
    onClick: () -> Unit,
) {
    val corners = LocalMorpheCorners.current
    val font = LocalMorpheFont.current
    val hover = remember { MutableInteractionSource() }
    val isHovered by hover.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .hoverable(hover)
            .clip(RoundedCornerShape(corners.small))
            .border(
                1.dp,
                MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = if (isHovered) 0.3f else 0.12f
                ),
                RoundedCornerShape(corners.small)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            fontFamily = font,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
