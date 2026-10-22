/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.engine.model.SupportedApp
import app.morphe.engine.util.DownloadUrlResolver.openUrlAndFollowRedirects
import app.morphe.gui.ui.components.MorpheAppAvatar
import app.morphe.gui.ui.components.MorpheAvatarSize
import app.morphe.gui.ui.components.MorpheBadge
import app.morphe.gui.ui.components.MorpheBadgeTone
import app.morphe.gui.ui.components.cardChipInk
import app.morphe.gui.ui.components.MorpheCardChip
import app.morphe.gui.ui.components.LocalCardFills
import app.morphe.gui.ui.components.AppCard
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.screens.home.DeviceAppInfo
import app.morphe.gui.ui.screens.home.PatchedAppState
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** List row displaying a supported application with version chips and expandable details. */
@Composable
fun SupportedAppListRow(
    app: SupportedApp,
    onClick: () -> Unit = {},
    isExpanded: Boolean = false,
    /** Display names of patch sources targeting this package. */
    patchSourceNames: List<String> = emptyList(),
    patchedState: PatchedAppState = PatchedAppState.NEVER_PATCHED,
    /** Device installation status and version information, if available. */
    deviceInfo: DeviceAppInfo? = null,
    modifier: Modifier = Modifier,
) {
    val chipInk = cardChipInk
    val latestExperimental = app.experimentalVersions.firstOrNull()

    val cardFills = LocalCardFills.current

    AppCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = MorpheTheme.shapes.mediumDp,
        appIconColorHex = app.appIconColor,
        fill = cardFills[app.packageName],
        onClick = onClick,
        onCustomise = {
            cardFills.requestEdit(app.packageName, app.displayName, app.appIconColor)
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MorpheAppAvatar(
                name = app.displayName,
                size = MorpheAvatarSize.ROW,
                onGradient = true,
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.displayName,
                    style = MorpheTheme.typography.titleMedium.copy(color = Color.White),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = app.packageName,
                    style = MorpheTheme.typography.labelSmall.copy(
                        color = Color.White.copy(alpha = MorpheTheme.alphas.secondaryText)
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (patchedState != PatchedAppState.NEVER_PATCHED) {
                Spacer(Modifier.width(8.dp))
                PatchedStateBadge(patchedState)
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Fall back to general download URL when recommended version is null.
            VersionCardChip(
                channelLabel = stringResource(Res.string.version_label_latest_stable),
                version = app.recommendedVersion,
                downloadUrl = app.apkDownloadUrl,
                nullLabel = stringResource(Res.string.home_app_row_any),
            )
            VersionCardChip(
                channelLabel = stringResource(Res.string.home_app_row_latest_experimental),
                version = latestExperimental,
                downloadUrl = app.experimentalDownloadUrl,
                nullLabel = stringResource(Res.string.home_app_row_na),
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = DisclosureEnter,
            exit = DisclosureExit,
        ) {
            Column {
                ExpandedBody(
                    app = app,
                    patchSourceNames = patchSourceNames,
                    chipInk = chipInk,
                )
                deviceInfo?.let {
                    DeviceLine(
                        info = it,
                        mutedColor = Color.White.copy(alpha = 0.5f),
                        activeColor = Color.White,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}
}

/** Status badge displaying the current patching state of an application. */
@Composable
internal fun PatchedStateBadge(
    state: PatchedAppState,
    onGradient: Boolean = true,
) {
    val label = when (state) {
        PatchedAppState.PATCHED -> stringResource(Res.string.home_your_apps_status_patched)
        PatchedAppState.PATCHED_WITH_UPDATES -> stringResource(Res.string.home_app_row_update_available_badge)
        PatchedAppState.MODIFIED_EXTERNALLY -> stringResource(Res.string.home_app_row_modified_badge)
        PatchedAppState.APK_MISSING -> stringResource(Res.string.home_app_row_apk_missing_badge)
        PatchedAppState.NEVER_PATCHED -> return
    }
    if (onGradient) {
        MorpheBadge(
            text = label,
            containerColor = Color.White.copy(alpha = 0.20f),
            contentColor = Color.White,
            onGradient = false,
        )
    } else {
        MorpheBadge(
            text = label,
            tone = MorpheBadgeTone.Primary,
        )
    }
}

@Composable
private fun VersionCardChip(
    channelLabel: String,
    version: String?,
    downloadUrl: String?,
    nullLabel: String,
) {
    val uriHandler = LocalUriHandler.current
    val shown = version?.let { if (it.startsWith("v")) it else "v$it" } ?: nullLabel
    MorpheCardChip(
        text = "$channelLabel · $shown",
        icon = if (downloadUrl != null) MorpheIcons.OpenInNew else null,
        onClick = downloadUrl?.let {
            { openUrlAndFollowRedirects(it) { resolved -> uriHandler.openUri(resolved) } }
        },
    )
}

/** Expanded detail section displaying patch sources and version channels. */
@Composable
private fun ExpandedBody(
    app: SupportedApp,
    patchSourceNames: List<String>,
    chipInk: Color,
) {
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (patchSourceNames.isNotEmpty()) {
            SectionLabel(text = stringResource(Res.string.home_app_row_section_patches_from), color = chipInk)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                patchSourceNames.forEach { name ->
                    MorpheCardChip(text = name)
                }
            }
        }

        VersionChipSection(
            label = stringResource(Res.string.version_label_stable),
            color = chipInk,
            versions = app.supportedVersions,
            packageName = app.packageName,
            onOpenUrl = uriHandler::openUri,
        )

        VersionChipSection(
            label = stringResource(Res.string.version_label_experimental),
            color = chipInk,
            versions = app.experimentalVersions,
            packageName = app.packageName,
            onOpenUrl = uriHandler::openUri,
        )
    }
}

@Composable
private fun VersionChipSection(
    label: String,
    color: Color,
    versions: List<String>,
    packageName: String,
    onOpenUrl: (String) -> Unit,
    maxPills: Int = 16,
) {
    if (versions.isEmpty()) return
    SectionLabel(text = label, color = color)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        versions.take(maxPills).forEach { v ->
            val url = remember(v) { SupportedApp.getDownloadUrl(packageName, v) }
            MorpheCardChip(
                text = v,
                icon = if (url != null) MorpheIcons.OpenInNew else null,
                onClick = url?.let { { onOpenUrl(it) } },
            )
        }
        if (versions.size > maxPills) {
            Text(
                text = "+${versions.size - maxPills}",
                style = MorpheTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = MorpheTheme.alphas.secondaryText),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
internal fun SectionLabel(
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Text(
        text = text,
        style = MorpheTheme.typography.titleSmall,
        color = color,
    )
}
