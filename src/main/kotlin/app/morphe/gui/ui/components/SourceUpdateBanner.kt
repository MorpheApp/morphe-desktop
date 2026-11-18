/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.morphe.gui.data.model.PatchSource
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.screens.patches.components.selection.BundleIcon
import app.morphe.gui.util.AvatarLoader
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

enum class SourceUpdateStatus { Updating, Success }

@Immutable
data class SourceUpdateBannerState(
    val visible: Boolean = false,
    val status: SourceUpdateStatus = SourceUpdateStatus.Updating,
    val completed: Int = 0,
    val total: Int = 0,
    val activeSource: PatchSource? = null,
    val downloadFraction: Float? = null,
)

/** Live status banner reporting patch source resolution progress and completion. */
@Composable
fun SourceUpdateBanner(
    state: SourceUpdateBannerState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val downloadPct = state.downloadFraction?.takeIf { it > 0f && it < 1f }
    val countFraction = if (state.total > 1 && state.completed > 0) {
        state.completed.toFloat() / state.total
    } else {
        null
    }
    val ringProgress = downloadPct ?: countFraction

    val tone = when (state.status) {
        SourceUpdateStatus.Updating -> MorpheBannerTone.Neutral
        SourceUpdateStatus.Success -> MorpheBannerTone.Success
    }
    val title = stringResource(
        when (state.status) {
            SourceUpdateStatus.Updating -> Res.string.home_updating_sources
            SourceUpdateStatus.Success -> Res.string.home_update_success
        }
    )
    val subtitle = when (state.status) {
        SourceUpdateStatus.Updating -> state.activeSource?.name.orEmpty()
        SourceUpdateStatus.Success -> stringResource(Res.string.home_update_success_subtitle)
    }
    val badge = when {
        state.status != SourceUpdateStatus.Updating -> null
        state.total > 1 -> "${state.completed} / ${state.total}"
        downloadPct != null -> "${(downloadPct * 100).toInt()}%"
        else -> null
    }
    val countsSources = state.total > 1

    MorpheBanner(
        title = title,
        subtitle = subtitle,
        tone = tone,
        badge = badge,
        badgeKey = { if (countsSources) it else Unit },
        onDismiss = onDismiss.takeIf { state.status == SourceUpdateStatus.Success },
        modifier = modifier,
        leading = {
            Crossfade(targetState = state.status, label = "sourceUpdateLeading") { shown ->
                when (shown) {
                    SourceUpdateStatus.Updating -> MorpheBannerProgressRing(progress = ringProgress) {
                        val activeSource = state.activeSource
                        if (activeSource != null) {
                            val avatarUrl = remember(activeSource.id, activeSource.url, activeSource.type) {
                                AvatarLoader.resolveAvatarUrl(activeSource)
                            }
                            BundleIcon(
                                bundleName = activeSource.name,
                                sourceType = activeSource.type,
                                avatarUrl = avatarUrl,
                                modifier = Modifier.size(24.dp),
                            )
                        } else {
                            MorpheBannerRingIcon(MorpheIcons.Refresh)
                        }
                    }

                    SourceUpdateStatus.Success -> MorpheBannerDisc(
                        icon = MorpheIcons.Check,
                        tone = MorpheBannerTone.Success,
                    )
                }
            }
        },
    )
}
