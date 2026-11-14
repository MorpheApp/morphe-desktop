/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.morphe.gui.LocalIsPatching
import app.morphe.gui.LocalLanguageState
import app.morphe.gui.ui.components.settings.AdvancedSection
import app.morphe.gui.ui.components.settings.AppearanceSection
import app.morphe.gui.ui.components.settings.SettingsCategory
import app.morphe.gui.ui.components.settings.SettingsRailCategoryItem
import app.morphe.gui.ui.components.settings.SystemSection
import app.morphe.gui.ui.components.settings.rememberAdvancedSearchVisibility
import app.morphe.gui.ui.components.settings.rememberAppearanceSearchVisibility
import app.morphe.gui.ui.components.settings.rememberSettingsCardBorderColor
import app.morphe.gui.ui.components.settings.rememberSystemSearchVisibility
import app.morphe.gui.ui.icons.MorpheIcons
import app.morphe.gui.ui.screens.home.components.SimpleTopBar
import app.morphe.gui.ui.theme.Animations
import app.morphe.gui.ui.theme.ThemeStyle
import app.morphe.morphe_desktop.generated.resources.*
import org.jetbrains.compose.resources.stringResource

private enum class SettingsSubView {
    NONE,
    LANGUAGE,
    CHANGELOG,
    LICENSES,
}

@Composable
fun SettingsPage(
    showBackHeader: Boolean = false,
    onBack: () -> Unit = {},
    openChangelogToken: Int = 0,
    modifier: Modifier = Modifier,
) {
    val settings = LocalSettingsState.current
    val isPatching by LocalIsPatching.current
    val currentLanguage = LocalLanguageState.current.current

    var selectedCategory by remember { mutableStateOf(SettingsCategory.APPEARANCE) }
    var searchQuery by remember { mutableStateOf("") }
    var settingsSubView by remember { mutableStateOf(SettingsSubView.NONE) }

    val detailScroll = rememberScrollState()

    LaunchedEffect(openChangelogToken) {
        if (openChangelogToken > 0) {
            searchQuery = ""
            selectedCategory = SettingsCategory.SYSTEM
            settingsSubView = SettingsSubView.CHANGELOG
        }
    }

    LaunchedEffect(selectedCategory, searchQuery) {
        detailScroll.scrollTo(0)
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (showBackHeader) {
            key(currentLanguage) {
                SimpleTopBar(
                    title = stringResource(Res.string.settings_dialog_title),
                    onBack = {
                        if (settingsSubView != SettingsSubView.NONE) {
                            settingsSubView = SettingsSubView.NONE
                        } else {
                            onBack()
                        }
                    },
                    showSettingsButton = false,
                )
            }
        }

        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            key(currentLanguage) {
                Column(
                    modifier = Modifier
                        .width(228.dp)
                        .fillMaxHeight()
                        .padding(start = 20.dp, end = 14.dp, top = 16.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SlimSearchField(
                        value = searchQuery,
                        onValueChange = { updated ->
                            searchQuery = updated
                            if (updated.isNotBlank() && settingsSubView != SettingsSubView.NONE) {
                                settingsSubView = SettingsSubView.NONE
                            }
                        },
                        placeholder = stringResource(Res.string.settings_search_placeholder),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        SettingsCategory.entries.forEach { category ->
                            SettingsRailCategoryItem(
                                label = stringResource(category.titleRes),
                                icon = category.icon,
                                selected = searchQuery.isBlank() && selectedCategory == category,
                                onClick = {
                                    searchQuery = ""
                                    settingsSubView = SettingsSubView.NONE
                                    selectedCategory = category
                                },
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .padding(vertical = 16.dp)
                    .background(rememberSettingsCardBorderColor().copy(alpha = 0.5f)),
            )

            val trimmedQuery = searchQuery.trim()
            val activeSubView = if (trimmedQuery.isEmpty()) settingsSubView else SettingsSubView.NONE

            AnimatedContent(
                targetState = activeSubView,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                transitionSpec = { Animations.screenEnter togetherWith Animations.screenExit },
                label = "settingsSubViewTransition",
            ) { targetSubView ->
                when (targetSubView) {
                    SettingsSubView.LANGUAGE -> SettingsSubViewBox {
                        LanguagePane(
                            currentLanguageCode = currentLanguage,
                            onLanguageSelected = { code ->
                                settings.setLanguage(code)
                                settingsSubView = SettingsSubView.NONE
                            },
                            onBack = { settingsSubView = SettingsSubView.NONE },
                            modifier = Modifier.fillMaxSize(),
                            horizontalPadding = PaddingValues(horizontal = 24.dp),
                        )
                    }
                    SettingsSubView.CHANGELOG -> SettingsSubViewBox {
                        key(currentLanguage) {
                            ChangelogPane(
                                onBack = { settingsSubView = SettingsSubView.NONE },
                                modifier = Modifier.fillMaxSize(),
                                horizontalPadding = PaddingValues(horizontal = 24.dp),
                            )
                        }
                    }
                    SettingsSubView.LICENSES -> SettingsSubViewBox {
                        key(currentLanguage) {
                            LicensesPane(
                                onBack = { settingsSubView = SettingsSubView.NONE },
                                modifier = Modifier.fillMaxSize(),
                                horizontalPadding = PaddingValues(horizontal = 24.dp),
                            )
                        }
                    }
                    SettingsSubView.NONE -> key(currentLanguage) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(detailScroll)
                                    .padding(horizontal = 24.dp, vertical = 16.dp),
                                contentAlignment = Alignment.TopStart,
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(24.dp),
                                ) {
                                    if (trimmedQuery.isEmpty()) {
                                        Text(
                                            text = stringResource(selectedCategory.titleRes),
                                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )

                                        when (selectedCategory) {
                                            SettingsCategory.APPEARANCE -> AppearanceSection(
                                                settings = settings,
                                                onOpenLanguage = { settingsSubView = SettingsSubView.LANGUAGE },
                                            )
                                            SettingsCategory.ADVANCED -> AdvancedSection(
                                                settings = settings,
                                                isPatching = isPatching,
                                                lockExpertMode = showBackHeader,
                                            )
                                            SettingsCategory.SYSTEM -> SystemSection(
                                                settings = settings,
                                                isPatching = isPatching,
                                                onOpenChangelog = { settingsSubView = SettingsSubView.CHANGELOG },
                                                onOpenLicenses = { settingsSubView = SettingsSubView.LICENSES },
                                            )
                                        }
                                    } else {
                                        val appearanceVis = rememberAppearanceSearchVisibility(
                                            query = trimmedQuery,
                                            isMonochrome = settings.themeState.style == ThemeStyle.MONOCHROME,
                                        )
                                        val advancedVis = rememberAdvancedSearchVisibility(trimmedQuery)
                                        val systemVis = rememberSystemSearchVisibility(trimmedQuery)

                                        val anyMatch = appearanceVis.any || advancedVis.any || systemVis.any

                                        if (!anyMatch) {
                                            SearchNoResultsState(
                                                modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
                                            )
                                        } else {
                                            if (appearanceVis.any) {
                                                SearchCategoryGroup(SettingsCategory.APPEARANCE) {
                                                    AppearanceSection(
                                                        settings = settings,
                                                        onOpenLanguage = {
                                                            searchQuery = ""
                                                            selectedCategory = SettingsCategory.APPEARANCE
                                                            settingsSubView = SettingsSubView.LANGUAGE
                                                        },
                                                        searchQuery = trimmedQuery,
                                                        visibility = appearanceVis,
                                                    )
                                                }
                                            }
                                            if (advancedVis.any) {
                                                SearchCategoryGroup(SettingsCategory.ADVANCED) {
                                                    AdvancedSection(
                                                        settings = settings,
                                                        isPatching = isPatching,
                                                        lockExpertMode = showBackHeader,
                                                        searchQuery = trimmedQuery,
                                                        visibility = advancedVis,
                                                    )
                                                }
                                            }
                                            if (systemVis.any) {
                                                SearchCategoryGroup(SettingsCategory.SYSTEM) {
                                                    SystemSection(
                                                        settings = settings,
                                                        isPatching = isPatching,
                                                        onOpenChangelog = {
                                                            searchQuery = ""
                                                            selectedCategory = SettingsCategory.SYSTEM
                                                            settingsSubView = SettingsSubView.CHANGELOG
                                                        },
                                                        onOpenLicenses = {
                                                            searchQuery = ""
                                                            selectedCategory = SettingsCategory.SYSTEM
                                                            settingsSubView = SettingsSubView.LICENSES
                                                        },
                                                        searchQuery = trimmedQuery,
                                                        visibility = systemVis,
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }

                            VerticalScrollbar(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .fillMaxHeight()
                                    .padding(end = 6.dp, top = 8.dp, bottom = 8.dp),
                                adapter = rememberScrollbarAdapter(detailScroll),
                                style = MorpheScrollbarStyle(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSubViewBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(top = 16.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        content()
    }
}

@Composable
private fun SearchCategoryGroup(
    category: SettingsCategory,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(category.titleRes),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        content()
    }
}

@Composable
private fun SearchNoResultsState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = MorpheIcons.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.size(32.dp),
        )
        Text(
            text = stringResource(Res.string.settings_search_no_results),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
