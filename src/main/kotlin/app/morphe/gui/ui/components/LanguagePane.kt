/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.data.constants.AppConstants
import app.morphe.gui.data.repository.LanguageOption
import app.morphe.gui.data.repository.LanguageRepository
import app.morphe.gui.ui.components.settings.SettingsSubViewHeader
import app.morphe.gui.ui.components.settings.rememberSettingsCardBorderColor
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.MorpheMotion
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.gui.ui.theme.outlineBorder
import app.morphe.gui.ui.theme.outlineStroke
import app.morphe.morphe_desktop.generated.resources.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Inline app language picker sub-view rendered inside Settings' detail pane. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LanguagePane(
    currentLanguageCode: String,
    onLanguageSelected: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: PaddingValues = PaddingValues(0.dp),
    languageRepository: LanguageRepository = koinInject(),
) {
    val uriHandler = LocalUriHandler.current
    val layoutDirection = LocalLayoutDirection.current
    val systemLabel = stringResource(Res.string.settings_theme_system)
    var searchQuery by remember { mutableStateOf("") }

    val allLanguages by produceState(
        initialValue = languageRepository.getCachedLanguages(currentLanguageCode) ?: emptyList(),
        key1 = currentLanguageCode,
    ) {
        value = withContext(Dispatchers.Default) {
            languageRepository.filterLanguages("", currentLanguageCode)
        }
    }
    val filteredLanguages = remember(searchQuery, allLanguages, currentLanguageCode) {
        when {
            allLanguages.isEmpty() -> emptyList()
            searchQuery.isBlank() -> allLanguages
            else -> languageRepository.filterLanguages(searchQuery, currentLanguageCode)
        }
    }

    val gridState = rememberLazyGridState()
    var hasScrolledToSelection by remember { mutableStateOf(false) }

    LaunchedEffect(currentLanguageCode, allLanguages) {
        if (!hasScrolledToSelection && searchQuery.isBlank() && allLanguages.isNotEmpty()) {
            val selectedIndex = allLanguages.indexOfFirst { option ->
                isLanguageSelected(option.code, currentLanguageCode)
            }
            if (selectedIndex >= 6) {
                gridState.scrollToItem(selectedIndex)
            }
            hasScrolledToSelection = true
        }
    }

    val startPad = horizontalPadding.calculateStartPadding(layoutDirection)
    val endPad = horizontalPadding.calculateEndPadding(layoutDirection)

    Column(modifier = modifier.fillMaxSize()) {
        SettingsSubViewHeader(
            title = stringResource(Res.string.settings_language_dialog_title),
            backDescription = stringResource(Res.string.back),
            onBack = onBack,
            modifier = Modifier.padding(horizontalPadding),
            trailing = {
                MorpheButton(
                    label = stringResource(Res.string.settings_language_translate_title),
                    icon = MorpheIcons.Translate,
                    onClick = { uriHandler.openUri("${AppConstants.WEBSITE_URL}/translate") },
                    variant = MorpheButtonVariant.OUTLINED,
                    size = MorpheButtonSize.COMPACT,
                )
            },
        )

        SlimSearchField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = stringResource(Res.string.settings_search_languages_placeholder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = startPad, end = endPad, bottom = 12.dp),
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                allLanguages.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        LoadingIndicator(
                            modifier = Modifier.size(38.dp),
                            color = MorpheTheme.colors.primary,
                        )
                    }
                }
                filteredLanguages.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                    ) {
                        Icon(
                            imageVector = MorpheIcons.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(32.dp),
                        )
                        Text(
                            text = stringResource(Res.string.settings_language_search_no_results),
                            style = MorpheTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 220.dp),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = startPad,
                            top = 4.dp,
                            end = endPad,
                            bottom = 24.dp,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(
                            items = filteredLanguages,
                            key = { it.code },
                        ) { languageOption ->
                            LanguageOptionCard(
                                option = languageOption,
                                systemLabel = systemLabel,
                                isSelected = isLanguageSelected(languageOption.code, currentLanguageCode),
                                onClick = { onLanguageSelected(languageOption.code) },
                            )
                        }
                    }

                    VerticalScrollbar(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .padding(end = 4.dp, bottom = 8.dp),
                        adapter = rememberScrollbarAdapter(gridState),
                        style = MorpheScrollbarStyle(),
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageOptionCard(
    option: LanguageOption,
    systemLabel: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val isSystem = option.code == LanguageRepository.SYSTEM_CODE
    val primaryLabel = if (isSystem) systemLabel else option.displayName
    val secondaryLabel = if (isSystem) systemLabel else option.nativeName
    val badgeBorderColor by animateColorAsState(
        targetValue = if (isSelected) MorpheTheme.colors.primary else rememberSettingsCardBorderColor(),
        animationSpec = MorpheMotion.fastTween(),
        label = "languageBadgeBorder",
    )

    LanguageCardContainer(
        selected = isSelected,
        onClick = onClick,
        modifier = Modifier.semantics { selected = isSelected },
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(MorpheTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surface)
                .outlineBorder(badgeBorderColor, MorpheTheme.shapes.small),
            contentAlignment = Alignment.Center,
        ) {
            if (isSystem) {
                Icon(
                    imageVector = MorpheIcons.Language,
                    contentDescription = null,
                    tint = MorpheTheme.colors.primary,
                    modifier = Modifier.size(22.dp),
                )
            } else {
                Text(
                    text = option.flag,
                    fontSize = 20.sp,
                    lineHeight = 20.sp,
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = primaryLabel,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = secondaryLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LanguageCardContainer(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val baseContainer = MaterialTheme.colorScheme.surfaceColorAtElevation(if (selected) 5.dp else 3.dp)
    val targetContainer = when {
        isPressed -> MaterialTheme.colorScheme.surfaceColorAtElevation(8.dp)
        isHovered -> MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)
        else -> baseContainer
    }
    val containerColor by animateColorAsState(
        targetValue = targetContainer,
        animationSpec = MorpheMotion.fastTween(),
        label = "languageCardContainer",
    )

    val baseBorder = rememberSettingsCardBorderColor()
    val targetBorder = when {
        selected -> MorpheTheme.colors.primary
        isPressed -> lerp(baseBorder, MorpheTheme.colors.primary, 0.55f)
        isHovered -> lerp(baseBorder, MorpheTheme.colors.primary, 0.35f)
        else -> baseBorder
    }
    val borderColor by animateColorAsState(
        targetValue = targetBorder,
        animationSpec = MorpheMotion.fastTween(),
        label = "languageCardBorder",
    )

    val shape = MorpheTheme.shapes.medium

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .hoverable(interactionSource)
            .handCursor()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            ),
        shape = shape,
        color = containerColor,
        border = outlineStroke(borderColor, enabled = selected || MorpheTheme.outlines),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

private fun isLanguageSelected(optionCode: String, currentLanguageCode: String): Boolean =
    optionCode.equals(currentLanguageCode, ignoreCase = true) ||
        (currentLanguageCode.isBlank() && optionCode == LanguageRepository.SYSTEM_CODE)
