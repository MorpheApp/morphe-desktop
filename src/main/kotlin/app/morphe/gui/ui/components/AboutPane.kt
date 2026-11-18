/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.engine.AppUpdateCheck
import app.morphe.engine.MorpheComponents
import app.morphe.gui.data.constants.AppConstants
import app.morphe.gui.data.repository.UpdateCheckRepository
import app.morphe.gui.ui.components.settings.SettingsSegmentCard
import app.morphe.gui.ui.components.settings.SettingsSubViewHeader
import app.morphe.gui.ui.components.settings.rememberSettingsCardBorderColor
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.Animations
import app.morphe.gui.ui.theme.MorpheMotion
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.ui.theme.outlineBorder
import app.morphe.gui.ui.theme.outlineStroke
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private data class AboutLink(
    val titleRes: StringResource,
    val icon: ImageVector,
    val url: String,
)

private val DonateLink = AboutLink(
    titleRes = Res.string.about_link_donate,
    icon = MorpheIcons.Favorite,
    url = AppConstants.DONATE_URL,
)

private val OfficialLinks = listOf(
    AboutLink(
        titleRes = Res.string.website,
        icon = MorpheIcons.Public,
        url = AppConstants.WEBSITE_URL,
    ),
    AboutLink(
        titleRes = Res.string.about_link_github,
        icon = MorpheIcons.Code,
        url = AppConstants.MORPHE_GITHUB_URL,
    ),
    AboutLink(
        titleRes = Res.string.about_link_reddit,
        icon = MorpheIcons.Forum,
        url = AppConstants.REDDIT_URL,
    ),
    AboutLink(
        titleRes = Res.string.about_link_translations,
        icon = MorpheIcons.Translate,
        url = AppConstants.TRANSLATE_URL,
    ),
)

private val LogoTileSize = 64.dp
private const val TILE_COLUMNS = 2

@Composable
fun AboutPane(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: PaddingValues = PaddingValues(0.dp),
    updateCheckRepository: UpdateCheckRepository = koinInject(),
) {
    val uriHandler = LocalUriHandler.current
    val layoutDirection = LocalLayoutDirection.current
    var updateCheck by remember { mutableStateOf<AppUpdateCheck?>(null) }
    var peopleList by remember { mutableStateOf<PeopleList?>(null) }

    LaunchedEffect(updateCheckRepository) {
        updateCheck = updateCheckRepository.checkUpdate()
    }

    AnimatedContent(
        targetState = peopleList,
        modifier = modifier.fillMaxSize(),
        transitionSpec = { Animations.screenEnter togetherWith Animations.screenExit },
        label = "aboutPeopleTransition",
    ) { targetList ->
        if (targetList != null) {
            PeoplePane(
                list = targetList,
                onBack = { peopleList = null },
                modifier = Modifier.fillMaxSize(),
                horizontalPadding = horizontalPadding,
            )
        } else {
            val startPad = horizontalPadding.calculateStartPadding(layoutDirection)
            val endPad = horizontalPadding.calculateEndPadding(layoutDirection)
            val scrollState = rememberScrollState()

            Column(modifier = Modifier.fillMaxSize()) {
                SettingsSubViewHeader(
                    title = stringResource(Res.string.settings_section_about),
                    backDescription = stringResource(Res.string.back),
                    onBack = onBack,
                    modifier = Modifier.padding(horizontalPadding),
                )

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(start = startPad, end = endPad, top = 4.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        AboutHeader()

                        VersionCard(
                            updateCheck = updateCheck,
                            onUpdateClick = { url -> uriHandler.openUri(url) },
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = stringResource(Res.string.about_links),
                                style = MorpheTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )

                            AboutTile(
                                title = stringResource(DonateLink.titleRes),
                                icon = DonateLink.icon,
                                accented = true,
                                onClick = { uriHandler.openUri(DonateLink.url) },
                                modifier = Modifier.fillMaxWidth(),
                            )

                            OfficialLinks.chunked(TILE_COLUMNS).forEach { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    row.forEach { link ->
                                        AboutTile(
                                            title = stringResource(link.titleRes),
                                            icon = link.icon,
                                            onClick = { uriHandler.openUri(link.url) },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    repeat(TILE_COLUMNS - row.size) { Spacer(modifier = Modifier.weight(1f)) }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                AboutTile(
                                    title = stringResource(Res.string.contributors),
                                    icon = MorpheIcons.Group,
                                    onClick = { peopleList = PeopleList.CONTRIBUTORS },
                                    modifier = Modifier.weight(1f),
                                )
                                AboutTile(
                                    title = stringResource(Res.string.supporters),
                                    icon = MorpheIcons.VolunteerActivism,
                                    onClick = { peopleList = PeopleList.SUPPORTERS },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }

                    VerticalScrollbar(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .padding(end = 4.dp, bottom = 8.dp),
                        adapter = rememberScrollbarAdapter(scrollState),
                        style = MorpheScrollbarStyle(),
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutHeader() {
    val logoShape = MorpheTheme.shapes.large
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(LogoTileSize)
                .clip(logoShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                .outlineBorder(MaterialTheme.colorScheme.outlineVariant, logoShape),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(Res.drawable.morphe_logo),
                contentDescription = stringResource(Res.string.morphe_logo_content_description),
                modifier = Modifier.fillMaxSize(),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(Res.string.app_name),
                style = MorpheTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(Res.string.about_component),
                style = MorpheTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun VersionCard(
    updateCheck: AppUpdateCheck?,
    onUpdateClick: (String) -> Unit,
) {
    val unknownVer = stringResource(Res.string.unknown)
    val displayVersion = AppConstants.APP_VERSION.removePrefix("v")

    SettingsSegmentCard(
        contentPadding = PaddingValues(16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(Res.string.version),
                    style = MorpheTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (AppConstants.IS_DEV_BUILD) {
                    MorpheBadge(
                        text = stringResource(Res.string.changelog_badge_prerelease),
                        tone = MorpheBadgeTone.Warning,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = displayVersion,
                    style = MorpheTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(
                        Res.string.about_version_patcher_library,
                        MorpheComponents.patcherVersion ?: unknownVer,
                        MorpheComponents.libraryVersion ?: unknownVer,
                    ),
                    style = MorpheTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (updateCheck is AppUpdateCheck.UpToDate) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                Text(
                    text = stringResource(Res.string.about_up_to_date),
                    style = MorpheTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            if (updateCheck is AppUpdateCheck.Available) {
                val info = updateCheck.info
                val updateVersion = "v${info.latestVersion.removePrefix("v")}"
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = MorpheIcons.Download,
                        contentDescription = null,
                        tint = MorpheTheme.colors.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource(Res.string.about_update_available, updateVersion),
                        style = MorpheTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MorpheTheme.colors.primary,
                        modifier = Modifier.weight(1f),
                    )
                    UpdateActionBadge(
                        label = stringResource(Res.string.home_action_update),
                        onClick = { onUpdateClick(info.downloadLink) },
                    )
                }
            }
        }
    }
}

@Composable
private fun UpdateActionBadge(
    label: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val badgeShape = if (MorpheTheme.corners.isSharp) MorpheTheme.shapes.extraSmall else RoundedCornerShape(percent = 50)

    Box(
        modifier = Modifier
            .clip(badgeShape)
            .hoverable(interactionSource)
            .handCursor()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
    ) {
        MorpheBadge(
            text = label,
            tone = MorpheBadgeTone.Primary,
            variant = if (isHovered) MorpheBadgeVariant.FILLED else MorpheBadgeVariant.SUBTLE,
        )
    }
}

@Composable
private fun AboutTile(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accented: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val shape = MorpheTheme.shapes.medium
    val primary = MorpheTheme.colors.primary

    val targetContainer = if (accented) {
        when {
            isPressed -> primary.copy(alpha = 0.24f)
            isHovered -> primary.copy(alpha = 0.18f)
            else -> primary.copy(alpha = 0.12f)
        }
    } else {
        when {
            isPressed -> MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp)
            isHovered -> MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)
            else -> MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
        }
    }
    val containerColor by animateColorAsState(
        targetValue = targetContainer,
        animationSpec = MorpheMotion.fastTween(),
        label = "aboutTileContainer",
    )

    val baseBorder = if (accented) {
        primary.copy(alpha = 0.28f)
    } else {
        rememberSettingsCardBorderColor()
    }
    val targetBorder = when {
        isPressed -> lerp(baseBorder, primary, 0.55f)
        isHovered -> lerp(baseBorder, primary, 0.35f)
        else -> baseBorder
    }
    val borderColor by animateColorAsState(
        targetValue = targetBorder,
        animationSpec = MorpheMotion.fastTween(),
        label = "aboutTileBorder",
    )

    Surface(
        modifier = modifier
            .clip(shape)
            .hoverable(interactionSource)
            .handCursor()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        shape = shape,
        color = containerColor,
        border = outlineStroke(borderColor),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 44.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = title,
                style = MorpheTheme.typography.labelLarge.copy(
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
