/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.morphe.gui.data.repository.LanguageOption
import app.morphe.gui.data.repository.LanguageRepository
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.theme.LocalMorpheAccents
import app.morphe.gui.ui.theme.LocalMorpheCorners
import app.morphe.gui.ui.theme.LocalMorpheDimens
import app.morphe.gui.ui.theme.LocalMorpheFont
import app.morphe.gui.ui.theme.MorpheAccentColors
import app.morphe.gui.ui.theme.MorpheCornerStyle
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * Desktop modal dialog for picking the application language.
 * Follows the native Morphe Desktop dialog aesthetic.
 */
@Composable
fun LanguageDialog(
    currentLanguageCode: String,
    onLanguageSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    font: FontFamily = LocalMorpheFont.current,
    languageRepository: LanguageRepository = remember { LanguageRepository() }
) {
    val accents = LocalMorpheAccents.current
    val corners = LocalMorpheCorners.current
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)

    var searchQuery by remember { mutableStateOf("") }
    val filteredLanguages = remember(searchQuery, languageRepository, currentLanguageCode) {
        languageRepository.filterLanguages(searchQuery, currentLanguageCode)
    }

    val listState = rememberLazyListState()

    // Scroll to the currently selected language on initial load
    LaunchedEffect(currentLanguageCode) {
        val selectedIndex = filteredLanguages.indexOfFirst {
            it.code.equals(currentLanguageCode, ignoreCase = true) ||
                (currentLanguageCode.isBlank() && it.code == LanguageRepository.SYSTEM_CODE)
        }
        if (selectedIndex >= 0) {
            listState.scrollToItem(selectedIndex)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(corners.medium),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = stringResource(Res.string.settings_language_dialog_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = font,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .width(360.dp)
                    .heightIn(max = 420.dp)
            ) {
                // ── Search Input ──
                LanguageSearchField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    font = font,
                    accents = accents,
                    corners = corners,
                    borderColor = borderColor
                )

                Spacer(Modifier.height(8.dp))

                // ── Language List Container ──
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(corners.small))
                        .border(1.dp, borderColor, RoundedCornerShape(corners.small))
                        .padding(4.dp)
                ) {
                    if (filteredLanguages.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(Res.string.search_no_results),
                                fontFamily = font,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
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
                                    font = font,
                                    accents = accents,
                                    corners = corners,
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
                            style = morpheScrollbarStyle()
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(corners.small),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(Res.string.close),
                    fontFamily = font,
                    fontWeight = FontWeight.Medium,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    )
}

@Composable
private fun LanguageSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    font: FontFamily,
    accents: MorpheAccentColors,
    corners: MorpheCornerStyle,
    borderColor: Color,
    modifier: Modifier = Modifier
) {
    val dimens = LocalMorpheDimens.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val animatedBorderColor by animateColorAsState(
        targetValue = if (isFocused) accents.primary.copy(alpha = 0.5f) else borderColor,
        animationSpec = tween(150),
        label = "languageSearchBorder"
    )

    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        interactionSource = interactionSource,
        textStyle = TextStyle(
            fontFamily = font,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
            lineHeight = 14.sp,
            color = MaterialTheme.colorScheme.onSurface
        ),
        cursorBrush = SolidColor(accents.primary),
        modifier = modifier
            .fillMaxWidth()
            .height(dimens.controlHeight)
            .clip(RoundedCornerShape(corners.small))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .border(1.dp, animatedBorderColor, RoundedCornerShape(corners.small)),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = MorpheIcons.Search,
                    contentDescription = null,
                    tint = accents.primary.copy(alpha = 0.7f),
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.settings_search_languages_placeholder),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = font,
                            lineHeight = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        innerTextField()
                    }
                }
                if (query.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(corners.small))
                            .clickable { onQueryChange("") }
                            .padding(2.dp)
                    ) {
                        Icon(
                            imageVector = MorpheIcons.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun LanguageOptionRow(
    option: LanguageOption,
    isSelected: Boolean,
    font: FontFamily,
    accents: MorpheAccentColors,
    corners: MorpheCornerStyle,
    onClick: () -> Unit
) {
    val hoverInteraction = remember { MutableInteractionSource() }
    val isHovered by hoverInteraction.collectIsHoveredAsState()

    val rowBgColor by animateColorAsState(
        targetValue = when {
            isSelected -> accents.primary.copy(alpha = 0.14f)
            isHovered -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
            else -> Color.Transparent
        },
        animationSpec = tween(120),
        label = "languageRowBg"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corners.small))
            .background(rowBgColor)
            .hoverable(hoverInteraction)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = option.flag,
            fontSize = 18.sp,
            modifier = Modifier.padding(end = 10.dp)
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            val isSystem = option.code == LanguageRepository.SYSTEM_CODE
            
            Text(
                text = if (isSystem) stringResource(Res.string.settings_theme_system) else option.displayName,
                fontFamily = font,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) accents.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (isSystem) stringResource(Res.string.settings_theme_system) else option.nativeName,
                fontFamily = font,
                fontSize = 10.sp,
                fontWeight = FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (isSelected) {
            Icon(
                imageVector = MorpheIcons.Check,
                contentDescription = null,
                tint = accents.primary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
