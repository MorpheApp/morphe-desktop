/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.patches.components.selection

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.morphe.gui.data.model.PatchSourceType
import app.morphe.gui.ui.components.handCursor
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.screens.patches.BundlePatches
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.util.AvatarLoader
import app.morphe.morphe_desktop.generated.resources.*
import io.ktor.client.HttpClient
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject

/** Pill shape for patch bundle selector tabs. */
private val PillShape = RoundedCornerShape(50)

/**
 * Patch bundle selector pill, displaying bundle avatar/icon, name, and selected/total patch counts.
 */
@Composable
fun PatchBundlePill(
    bundle: BundlePatches,
    selectedCount: Int,
    totalCount: Int,
    isSelected: Boolean,
    hasResults: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val containerColor by animateColorAsState(
        targetValue = when {
            isSelected -> MorpheTheme.colors.primary.copy(alpha = 0.18f)
            isHovered -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        },
        label = "bundle_pill_container"
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            isSelected -> MorpheTheme.colors.primary.copy(alpha = 0.55f)
            isHovered -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
        },
        label = "bundle_pill_border"
    )

    Row(
        modifier = modifier
            .clip(PillShape)
            .border(1.dp, borderColor, PillShape)
            .background(containerColor, PillShape)
            .hoverable(interactionSource)
            .handCursor()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        BundleIcon(
            bundleName = bundle.bundleName,
            sourceType = bundle.sourceType,
            avatarUrl = bundle.avatarUrl,
            modifier = Modifier.size(24.dp)
        )

        Column {
            Text(
                text = bundle.bundleName,
                style = if (isSelected) MorpheTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        else MorpheTheme.typography.bodyMedium,
                color = if (hasResults) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "$selectedCount/$totalCount",
                style = MorpheTheme.typography.labelSmall,
                color = if (isSelected) MorpheTheme.colors.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * 24dp circular icon disc for patch bundles (Morphe logo for default, avatar/glyph for 3rd-party).
 */
@Composable
fun BundleIcon(
    bundleName: String,
    sourceType: PatchSourceType?,
    avatarUrl: String?,
    modifier: Modifier = Modifier,
) {
    val isDefault = sourceType == PatchSourceType.DEFAULT && avatarUrl.isNullOrBlank()

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = if (isDefault) Color.White else MaterialTheme.colorScheme.surfaceVariant
    ) {
        when {
            isDefault -> {
                Image(
                    painter = painterResource(Res.drawable.morphe_logo),
                    contentDescription = bundleName,
                    modifier = Modifier.fillMaxSize().padding(2.dp)
                )
            }
            !avatarUrl.isNullOrBlank() -> {
                RemoteAvatar(
                    url = avatarUrl,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = when (sourceType) {
                                PatchSourceType.GITLAB -> MorpheIcons.Language
                                PatchSourceType.LOCAL -> MorpheIcons.Source
                                else -> MorpheIcons.Github
                            },
                            contentDescription = bundleName,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
            sourceType == PatchSourceType.LOCAL -> {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = MorpheIcons.Source,
                        contentDescription = bundleName,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            else -> {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = when (sourceType) {
                            PatchSourceType.GITLAB -> MorpheIcons.Language
                            else -> MorpheIcons.Github
                        },
                        contentDescription = bundleName,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

/**
 * Loads and displays an avatar image from [url], rendering [fallback] while loading or on failure.
 */
@Composable
fun RemoteAvatar(
    url: String,
    modifier: Modifier = Modifier,
    fallback: @Composable () -> Unit = {},
) {
    val httpClient: HttpClient = koinInject()
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(url) {
        bitmap = AvatarLoader.loadAvatar(url, httpClient)
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(CircleShape)
        )
    } else {
        fallback()
    }
}
