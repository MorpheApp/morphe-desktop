/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import app.morphe.gui.data.constants.AppConstants
import app.morphe.gui.data.repository.PeopleRepository
import app.morphe.gui.data.repository.PeopleRepository.People
import app.morphe.gui.data.repository.PeopleRepository.Person
import app.morphe.gui.ui.components.settings.SettingsGroup
import app.morphe.gui.ui.components.settings.SettingsGroupBuilder
import app.morphe.gui.ui.components.settings.SettingsSection
import app.morphe.gui.ui.components.settings.SettingsSubViewHeader
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.MorpheMotion
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.util.AvatarLoader
import app.morphe.morphe_desktop.generated.resources.*
import io.ktor.client.HttpClient
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Which list of people behind Morphe a [PeoplePane] displays. */
enum class PeopleList {
    CONTRIBUTORS,
    SUPPORTERS,
}

private enum class ContributorPeriod(
    val label: StringResource,
    val contributors: (People) -> List<Person>,
) {
    ALL_TIME(Res.string.contributors_all_time, People::contributors),
    SIX_MONTHS(Res.string.contributors_6_months, People::contributors6Months),
    THREE_MONTHS(Res.string.contributors_3_months, People::contributors3Months),
}

private data class Project(
    val name: String,
    val organization: String,
    val url: String,
    val ownPicture: Boolean = false,
) {
    val person = Person(
        name = name,
        url = url,
        avatar = "https://${AppConstants.GITHUB_HOST}/$organization.png?size=96".takeIf { ownPicture },
    )
}

private val currentProject = Project(
    name = "Morphe",
    organization = AppConstants.MORPHE_GITHUB_ORG,
    url = "${AppConstants.DESKTOP_REPO_URL}/graphs/contributors",
    ownPicture = true,
)

private val priorProjects = listOf(
    Project(
        name = "ReVanced",
        organization = "ReVanced",
        url = "https://github.com/ReVanced/revanced-cli/graphs/contributors",
    ),
)

private val PersonCellWidth = 84.dp
private const val MIN_PERSON_COLUMNS = 3

private val AvatarSize = 56.dp
private val ProjectAvatarSize = 40.dp

private val AvatarRingWidth = 2.dp
private val AvatarRingGap = 2.dp
private val AvatarRingPulseSpread = 8.dp
private const val AVATAR_RING_PULSE_MILLIS = 2400
private const val AVATAR_RING_PULSE_ALPHA = 0.8f
private const val AVATAR_RING_WAVES = 2

private const val AVATAR_STAGGER_MILLIS = 30
private const val STAGGERED_ROWS = 8
private const val AVATAR_ENTRANCE_SCALE = 0.6f
private const val AVATAR_ANIMATION_MILLIS = 220

private val PersonRowGap = 12.dp
private val PersonColumnGap = 4.dp
private val PeriodDropdownWidth = 160.dp
private const val PERIOD_MOVE_WINDOW_NANOS = 600_000_000L
private const val PERIOD_MOVE_MAX_ROWS = 6
private val PeriodMoveSpring = spring<Float>(stiffness = 400f, dampingRatio = 0.8f)

@Composable
fun PeoplePane(
    list: PeopleList,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: PaddingValues = PaddingValues(0.dp),
    peopleRepository: PeopleRepository = koinInject(),
) {
    val uriHandler = LocalUriHandler.current
    val layoutDirection = LocalLayoutDirection.current
    val people by peopleRepository.people.collectAsState()
    var unavailable by remember { mutableStateOf(false) }

    LaunchedEffect(peopleRepository) {
        unavailable = !peopleRepository.refresh()
    }

    val periods = people?.takeIf { list == PeopleList.CONTRIBUTORS }
        ?.let { loaded -> ContributorPeriod.entries.filter { it.contributors(loaded).isNotEmpty() } }
        .orEmpty()
    var pickedPeriod by rememberSaveable { mutableStateOf(ContributorPeriod.ALL_TIME) }
    val period = pickedPeriod.takeIf { it in periods } ?: ContributorPeriod.ALL_TIME

    val persons = when (list) {
        PeopleList.CONTRIBUTORS -> people?.let(period.contributors)
        PeopleList.SUPPORTERS -> people?.supporters
    }
    val shownPlaces = remember { ShownPlaces() }
    SideEffect {
        shownPlaces.places = persons.orEmpty().withIndex().associate { (place, person) -> person.url to place }
    }
    val periodChange = remember(period) { PeriodChange(shownPlaces.places) }

    val count = when (list) {
        PeopleList.CONTRIBUTORS -> persons?.size
        PeopleList.SUPPORTERS -> people?.supportersTotal
    }
    val titleText = stringResource(
        when (list) {
            PeopleList.CONTRIBUTORS -> Res.string.contributors
            PeopleList.SUPPORTERS -> Res.string.supporters
        }
    )
    val summary = stringResource(
        when (list) {
            PeopleList.CONTRIBUTORS -> Res.string.contributors_ranking
            PeopleList.SUPPORTERS -> Res.string.supporters_description
        }
    )
    val periodItems = periods.map { option ->
        MorpheDropdownItem(
            label = stringResource(option.label),
            selected = option == period,
            onClick = { pickedPeriod = option },
        )
    }
    val headerTitle = listOfNotNull(titleText, count?.takeIf { it > 0 }?.toString()).joinToString(" · ")

    val startPad = horizontalPadding.calculateStartPadding(layoutDirection)
    val endPad = horizontalPadding.calculateEndPadding(layoutDirection)

    Column(modifier = modifier.fillMaxSize()) {
        SettingsSubViewHeader(
            title = headerTitle,
            backDescription = stringResource(Res.string.settings_back_to_about),
            onBack = onBack,
            modifier = Modifier.padding(horizontalPadding),
            trailing = when {
                list == PeopleList.SUPPORTERS -> {
                    {
                        MorpheButton(
                            label = stringResource(Res.string.supporters_become),
                            icon = MorpheIcons.Favorite,
                            onClick = { uriHandler.openUri(AppConstants.DONATE_URL) },
                            variant = MorpheButtonVariant.PRIMARY,
                            size = MorpheButtonSize.COMPACT,
                        )
                    }
                }

                periodItems.size > 1 -> {
                    {
                        MorpheDropdown(
                            label = stringResource(period.label),
                            items = periodItems,
                            leadingIcon = MorpheIcons.DateRange,
                            modifier = Modifier.width(PeriodDropdownWidth),
                        )
                    }
                }

                else -> null
            },
        )

        Text(
            text = summary,
            style = MorpheTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = startPad, end = endPad, bottom = 12.dp),
        )

        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (people == null && !unavailable) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = MorpheTheme.colors.primary,
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 3.dp,
                    )
                }
            } else {
                val listState = rememberLazyListState()
                val availableWidth = (maxWidth - startPad - endPad).coerceAtLeast(0.dp)
                val columns = (availableWidth / PersonCellWidth).toInt().coerceAtLeast(MIN_PERSON_COLUMNS)

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(PersonRowGap),
                    contentPadding = PaddingValues(start = startPad, end = endPad, top = 4.dp, bottom = 24.dp),
                ) {
                    if (persons.isNullOrEmpty() && unavailable) {
                        item(key = "unavailable") {
                            UnavailableState(
                                message = stringResource(Res.string.people_unavailable),
                            )
                        }
                    }

                    peopleGrid(
                        people = persons.orEmpty(),
                        columns = columns,
                        onOpen = { uriHandler.openUri(it) },
                        periodChange = periodChange.takeIf { list == PeopleList.CONTRIBUTORS },
                    )

                    when (list) {
                        PeopleList.CONTRIBUTORS -> if (people != null || unavailable) {
                            item(key = "prior") {
                                PriorDevelopment(
                                    showCurrent = persons.isNullOrEmpty() && unavailable,
                                    onOpen = { uriHandler.openUri(it) },
                                )
                            }
                        }

                        PeopleList.SUPPORTERS -> {
                            val unlisted = (count ?: 0) - persons.orEmpty().size
                            if (!persons.isNullOrEmpty() && unlisted > 0) {
                                item(key = "unlisted") {
                                    SettingsGroup {
                                        actionItem(
                                            key = "more_supporters",
                                            icon = MorpheIcons.VolunteerActivism,
                                            title = stringResource(Res.string.people_more, unlisted.toString()),
                                            onClick = { uriHandler.openUri(AppConstants.SUPPORTERS_URL) },
                                            trailing = { ExternalLinkIcon() },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                VerticalScrollbar(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(end = 4.dp, bottom = 8.dp),
                    adapter = rememberScrollbarAdapter(listState),
                    style = MorpheScrollbarStyle(),
                )
            }
        }
    }
}

@Composable
private fun UnavailableState(message: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = MorpheIcons.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
            modifier = Modifier.size(30.dp),
        )
        Text(
            text = message,
            style = MorpheTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun LazyListScope.peopleGrid(
    people: List<Person>,
    columns: Int,
    onOpen: (String) -> Unit,
    periodChange: PeriodChange?,
) {
    val rows = people.chunked(columns)
    itemsIndexed(items = rows, key = { index, _ -> "row_$index" }) { index, row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(PersonColumnGap),
        ) {
            row.forEachIndexed { column, person ->
                PersonCell(
                    person = person,
                    entranceOrder = if (index < STAGGERED_ROWS) index + column else column,
                    onClick = { onOpen(person.url) },
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            periodChange
                                ?.let { Modifier.movesOnPeriodChange(it, person, index * columns + column, columns) }
                                ?: Modifier
                        ),
                )
            }
            repeat(columns - row.size) { Spacer(modifier = Modifier.weight(1f)) }
        }
    }
}

private class ShownPlaces {
    var places: Map<String, Int> = emptyMap()
}

private class PeriodChange(val places: Map<String, Int>) {
    val atNanos = System.nanoTime()
}

@Composable
private fun Modifier.movesOnPeriodChange(change: PeriodChange, person: Person, place: Int, columns: Int): Modifier {
    val from = change.places[person.url]
    val remaining = remember(change) {
        val moves = change.places.isNotEmpty() && from != place &&
            System.nanoTime() - change.atNanos < PERIOD_MOVE_WINDOW_NANOS
        Animatable(if (moves) 1f else 0f)
    }
    LaunchedEffect(change) { remaining.animateTo(0f, PeriodMoveSpring) }

    val mirrored = LocalLayoutDirection.current == LayoutDirection.Rtl
    val density = LocalDensity.current
    val rowGap = with(density) { PersonRowGap.toPx() }
    val columnGap = with(density) { PersonColumnGap.toPx() }
    return graphicsLayer {
        val left = remaining.value
        if (left == 0f) return@graphicsLayer
        if (from == null) {
            val shown = (1f - left).coerceIn(0f, 1f)
            alpha = shown
            scaleX = AVATAR_ENTRANCE_SCALE + (1f - AVATAR_ENTRANCE_SCALE) * shown
            scaleY = scaleX
        } else {
            val columnShift = from % columns - place % columns
            val rowShift = (from / columns - place / columns).coerceIn(-PERIOD_MOVE_MAX_ROWS, PERIOD_MOVE_MAX_ROWS)
            translationX = columnShift * (size.width + columnGap) * left * if (mirrored) -1 else 1
            translationY = rowShift * (size.height + rowGap) * left
        }
    }
}

@Composable
private fun PersonCell(
    person: Person,
    entranceOrder: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    var isTruncated by remember(person.name) { mutableStateOf(false) }

    val bgColor by animateColorAsState(
        targetValue = if (isHovered) {
            MaterialTheme.colorScheme.onSurface.copy(alpha = MorpheTheme.alphas.hover)
        } else {
            Color.Transparent
        },
        animationSpec = MorpheMotion.fastTween(),
        label = "personCellHover",
    )

    MorpheTooltip(
        text = person.name,
        enabled = isTruncated,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MorpheTheme.shapes.medium)
                .background(bgColor)
                .hoverable(interactionSource)
                .handCursor()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                )
                .padding(vertical = 10.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PersonAvatar(person = person, entranceOrder = entranceOrder)
            Text(
                text = person.name,
                style = MorpheTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { isTruncated = it.hasVisualOverflow },
            )
        }
    }
}

@Composable
private fun PersonAvatar(
    person: Person,
    entranceOrder: Int,
    size: Dp = AvatarSize,
    httpClient: HttpClient = koinInject(),
) {
    val megaColor = MorpheTheme.colors.warning
    // Cells are reused positionally, so a period change hands this slot a different person.
    // Start from the memory cache so an already loaded picture shows on the first frame
    // instead of fading in again.
    val cachedPicture = remember(person.avatar) { person.avatar?.let(AvatarLoader::cachedAvatar) }
    var picture by remember(person.avatar) { mutableStateOf(cachedPicture) }
    LaunchedEffect(person.avatar) {
        if (picture == null) {
            picture = person.avatar?.let { AvatarLoader.loadAvatar(it, httpClient) }
        }
    }
    val pictureAlpha = remember(person.avatar) { Animatable(if (cachedPicture != null) 1f else 0f) }
    LaunchedEffect(picture) {
        pictureAlpha.animateTo(if (picture != null) 1f else 0f, tween(AVATAR_ANIMATION_MILLIS))
    }
    var entered by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val entrance by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(
            durationMillis = AVATAR_ANIMATION_MILLIS,
            delayMillis = entranceOrder * AVATAR_STAGGER_MILLIS,
            easing = EaseOutBack,
        ),
        label = "avatarEntrance",
    )

    Box(
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                val scale = AVATAR_ENTRANCE_SCALE + (1f - AVATAR_ENTRANCE_SCALE) * entrance
                scaleX = scale
                scaleY = scale
                alpha = entrance.coerceIn(0f, 1f)
            }
            .then(
                if (person.mega) {
                    Modifier
                        .pulsingRing(megaColor)
                        .padding(AvatarRingWidth + AvatarRingGap)
                } else {
                    Modifier
                }
            )
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center,
    ) {
        if (pictureAlpha.value < 1f) {
            Text(
                text = person.name.take(1).uppercase(),
                style = MorpheTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MorpheTheme.colors.primary,
            )
        }
        picture?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = pictureAlpha.value },
            )
        }
    }
}

@Composable
private fun Modifier.pulsingRing(color: Color): Modifier {
    val pulse by rememberInfiniteTransition(label = "avatarRing").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = AVATAR_RING_PULSE_MILLIS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "avatarRingPulse",
    )
    return drawBehind {
        val width = AvatarRingWidth.toPx()
        val radius = (size.minDimension - width) / 2
        repeat(AVATAR_RING_WAVES) { wave ->
            val progress = (pulse + wave.toFloat() / AVATAR_RING_WAVES) % 1f
            drawCircle(
                color = color,
                radius = radius + AvatarRingPulseSpread.toPx() * progress,
                alpha = AVATAR_RING_PULSE_ALPHA * (1f - progress),
                style = Stroke(width),
            )
        }
        drawCircle(color = color, radius = radius, style = Stroke(width))
    }
}

@Composable
private fun PriorDevelopment(
    showCurrent: Boolean,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (showCurrent) {
            SettingsGroup {
                projectItem(project = currentProject, entranceOrder = 0, onOpen = onOpen)
            }
        }
        SettingsSection(
            title = stringResource(Res.string.credits_prior_development),
            icon = MorpheIcons.History,
        ) {
            SettingsGroup {
                priorProjects.forEachIndexed { index, project ->
                    projectItem(project = project, entranceOrder = index, onOpen = onOpen)
                }
            }
        }
    }
}

private fun SettingsGroupBuilder.projectItem(
    project: Project,
    entranceOrder: Int,
    onOpen: (String) -> Unit,
) {
    actionItem(
        key = project.name,
        leadingContent = {
            PersonAvatar(
                person = project.person,
                entranceOrder = entranceOrder,
                size = ProjectAvatarSize,
            )
        },
        title = project.name,
        description = "${AppConstants.GITHUB_HOST}/${project.organization}",
        onClick = { onOpen(project.url) },
        trailing = { ExternalLinkIcon() },
    )
}

@Composable
private fun ExternalLinkIcon() {
    Icon(
        imageVector = MorpheIcons.OpenInNew,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(16.dp),
    )
}
