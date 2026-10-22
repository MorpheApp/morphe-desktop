/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.VerticalScrollbar
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.morphe.gui.data.repository.LanguageOption
import app.morphe.gui.data.repository.LanguageRepository
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.MorpheMotion
import app.morphe.gui.ui.theme.MorpheTheme
import app.morphe.morphe_desktop.generated.resources.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/** Modal dialog for selecting the application language. */
@Composable
fun LanguageDialog(
    currentLanguageCode: String,
    onLanguageSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    languageRepository: LanguageRepository = koinInject()
) {
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = MorpheTheme.alphas.borderMuted)

    var searchQuery by remember { mutableStateOf("") }
    val filteredLanguages by produceState(
        initialValue = languageRepository.getCachedLanguages(currentLanguageCode) ?: emptyList(),
        key1 = searchQuery,
        key2 = currentLanguageCode
    ) {
        value = withContext(Dispatchers.Default) {
            languageRepository.filterLanguages(searchQuery, currentLanguageCode)
        }
    }

    val listState = rememberLazyListState()

    // Scroll to selected language on initial load.
    LaunchedEffect(currentLanguageCode, filteredLanguages) {
        val selectedIndex = filteredLanguages.indexOfFirst {
            it.code.equals(currentLanguageCode, ignoreCase = true) ||
                    (currentLanguageCode.isBlank() && it.code == LanguageRepository.SYSTEM_CODE)
        }
        if (selectedIndex >= 0) {
            listState.scrollToItem(selectedIndex)
        }
    }

    val windowSize = LocalWindowInfo.current.containerSize
    val density = LocalDensity.current
    val dialogWidth = with(density) { (windowSize.width * 0.72f).toDp() }
        .coerceIn(560.dp, 900.dp)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        MorpheDialogSurface(
            modifier = Modifier.width(dialogWidth),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = stringResource(Res.string.settings_language_dialog_title),
                style = MorpheTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
            ) {
                SlimSearchField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = stringResource(Res.string.settings_search_languages_placeholder),
                )

                Spacer(Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(MorpheTheme.shapes.small)
                        .border(1.dp, borderColor, MorpheTheme.shapes.small)
                        .padding(4.dp)
                ) {
                    if (filteredLanguages.isEmpty()) {
                        if (searchQuery.isNotBlank()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(Res.string.settings_language_search_no_results),
                                    style = MorpheTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(28.dp),
                                    strokeWidth = 2.5.dp,
                                    color = MorpheTheme.colors.primary,
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(end = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(
                                items = filteredLanguages,
                                key = { it.code }
                            ) { languageOption ->
                                val isSelected = languageOption.code.equals(currentLanguageCode, ignoreCase = true) ||
                                        (currentLanguageCode.isBlank() && languageOption.code == LanguageRepository.SYSTEM_CODE)

                                LanguageOptionRow(
                                    option = languageOption,
                                    isSelected = isSelected,
                                    onClick = {
                                        onLanguageSelected(languageOption.code)
                                        onDismiss()
                                    }
                                )
                            }
                        }

                        VerticalScrollbar(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .padding(vertical = 2.dp),
                            adapter = rememberScrollbarAdapter(listState),
                            style = MorpheScrollbarStyle()
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                MorpheButton(
                    label = stringResource(Res.string.close),
                    onClick = onDismiss,
                    variant = MorpheButtonVariant.OUTLINED,
                    size = MorpheButtonSize.COMPACT,
                )
            }
        }
    }
}

@Composable
private fun LanguageOptionRow(
    option: LanguageOption,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()

    val rowBgColor by animateColorAsState(
        targetValue = when {
            isSelected -> MorpheTheme.colors.primary.copy(alpha = 0.14f)
            isHovered -> MaterialTheme.colorScheme.onSurface.copy(alpha = MorpheTheme.alphas.hover)
            else -> Color.Transparent
        },
        animationSpec = MorpheMotion.fastTween(),
        label = "languageRowBg"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MorpheTheme.shapes.small)
            .background(rowBgColor)
            .hoverable(hoverInteraction)
            .handCursor()
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = option.flag,
            style = MorpheTheme.typography.titleLarge,
            modifier = Modifier.padding(end = 10.dp)
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            val isSystem = option.code == LanguageRepository.SYSTEM_CODE

            Text(
                text = if (isSystem) stringResource(Res.string.settings_theme_system) else option.displayName,
                style = if (isSelected) MorpheTheme.typography.labelMedium else MorpheTheme.typography.labelSmall,
                color = if (isSelected) MorpheTheme.colors.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (isSystem) stringResource(Res.string.settings_theme_system) else option.nativeName,
                style = MorpheTheme.typography.labelExtraSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (isSelected) {
            Icon(
                imageVector = MorpheIcons.Check,
                contentDescription = null,
                tint = MorpheTheme.colors.primary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
