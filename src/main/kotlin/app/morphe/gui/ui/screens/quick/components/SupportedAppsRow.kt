/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.quick.components

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.engine.model.SupportedApp
import app.morphe.engine.util.DownloadUrlResolver.openUrlAndFollowRedirects
import app.morphe.gui.ui.components.*
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.*
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

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
    val uriHandler = LocalUriHandler.current
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(Res.string.quick_patch_supported_apps_title),
            style = MorpheTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = stringResource(Res.string.quick_patch_supported_apps_subtitle),
            style = MorpheTheme.typography.bodySmall,
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
                        color = MorpheTheme.colors.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(Res.string.quick_patch_supported_apps_loading),
                        style = MorpheTheme.typography.labelSmall,
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
                        style = MorpheTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    MorpheButton(
                        label = stringResource(Res.string.retry),
                        size = MorpheButtonSize.COMPACT,
                        variant = MorpheButtonVariant.OUTLINED,
                        onClick = onRetry
                    )
                    // Allow switching sources if retrying a broken source fails.
                    Spacer(modifier = Modifier.width(6.dp))
                    MorpheButton(
                        label = stringResource(Res.string.quick_patch_supported_apps_change_source),
                        size = MorpheButtonSize.COMPACT,
                        variant = MorpheButtonVariant.OUTLINED,
                        onClick = onManageSources
                    )
                }
            }
            else -> {
                var searchQuery by remember { mutableStateOf("") }
                val filteredApps = if (searchQuery.isBlank()) supportedApps
                else supportedApps.filter {
                    it.displayName.contains(searchQuery, ignoreCase = true) ||
                    it.packageName.contains(searchQuery, ignoreCase = true)
                }

                if (supportedApps.size > 4) {
                    SlimSearchField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = stringResource(Res.string.filter_apps_hint),
                        maxWidth = 260.dp,
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
                            style = MorpheTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                    return@Column
                }

                val useScrolling = filteredApps.size > 4
                val cardsScrollState = rememberScrollState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (useScrolling) Modifier.horizontalScroll(cardsScrollState) else Modifier)
                        .height(IntrinsicSize.Max)
                        // Use detectTapGestures instead of clickable to avoid swallowing scroll events.
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { focusManager.clearFocus() })
                        },
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val cardFills = LocalCardFills.current
                    filteredApps.forEach { app ->
                        val url = app.apkDownloadUrl

                        AppCard(
                            modifier = Modifier
                                .then(
                                    if (useScrolling) Modifier.width(170.dp)
                                    else Modifier.weight(1f)
                                )
                                .fillMaxHeight(),
                            cornerRadius = MorpheTheme.corners.small,
                            appIconColorHex = app.appIconColor,
                            fill = cardFills[app.packageName],
                            interactive = false,
                            onCustomise = {
                                cardFills.requestEdit(
                                    app.packageName,
                                    app.displayName,
                                    app.appIconColor,
                                )
                            },
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
                                    style = MorpheTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
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
                                    style = MorpheTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.6f)
                                )

                                if (url != null) {
                                    val versionToDisplay = if (isExperimental) {
                                        app.experimentalVersions.firstOrNull()
                                    } else {
                                        app.recommendedVersion
                                    }
                                    MorpheCardChip(
                                        text = versionToDisplay?.let { "v$it" } ?: stringResource(Res.string.download),
                                        icon = MorpheIcons.OpenInNew,
                                        tooltip = versionToDisplay?.let { "v$it" },
                                    ) {
                                        openUrlAndFollowRedirects(url) { resolved ->
                                            uriHandler.openUri(resolved)
                                        }
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
                        style = MorpheScrollbarStyle()
                    )
                }
            }
        }
    }
}
