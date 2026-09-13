/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.home

import app.morphe.engine.model.PatchedAppRecord
import app.morphe.gui.data.model.SupportedApp

enum class HomeAppSortMode(val label: String, val description: String) {
    RECOMMENDED("Recommended", "Patched apps first, then installed, then by name"),
    NAME_ASC("Name (A to Z)", "Alphabetical"),
    NAME_DESC("Name (Z to A)", "Reverse alphabetical"),
    UPDATES_FIRST("Patch updates first", "Apps with a patch update first, then recommended"),
    RECENTLY_PATCHED("Recently patched", "Most recently patched first");

    companion object {
        fun fromPreference(value: String?): HomeAppSortMode =
            entries.firstOrNull { it.name == value } ?: RECOMMENDED
    }
}

data class HomeSortKeys(
    val displayName: String,
    val packageName: String,
    val isPatched: Boolean,
    val isInstalled: Boolean,
    val hasPatchUpdate: Boolean,
    val patchedAt: Long,
)

fun SupportedApp.sortKeys(
    states: Map<String, PatchedAppState>,
    installed: Set<String>,
    patchedAt: Map<String, Long>,
): HomeSortKeys {
    val state = states[packageName]
    return HomeSortKeys(
        displayName = displayName,
        packageName = packageName,
        isPatched = state != null && state != PatchedAppState.NEVER_PATCHED,
        isInstalled = packageName in installed,
        hasPatchUpdate = state == PatchedAppState.PATCHED_WITH_UPDATES,
        patchedAt = patchedAt[packageName] ?: Long.MIN_VALUE,
    )
}

fun PatchedAppRecord.sortKeys(
    states: Map<String, PatchedAppState>,
    installed: Set<String>,
): HomeSortKeys = HomeSortKeys(
    displayName = displayName,
    packageName = packageName,
    isPatched = true,
    isInstalled = packageName in installed,
    hasPatchUpdate = states[packageName] == PatchedAppState.PATCHED_WITH_UPDATES,
    patchedAt = patchedAt,
)

private val recommended: Comparator<HomeSortKeys> =
    compareByDescending<HomeSortKeys> { it.isPatched }
        .thenByDescending { it.isInstalled }
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.displayName }
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.packageName }

fun HomeAppSortMode.comparator(): Comparator<HomeSortKeys> = when (this) {
    HomeAppSortMode.RECOMMENDED -> recommended
    HomeAppSortMode.NAME_ASC ->
        compareBy<HomeSortKeys, String>(String.CASE_INSENSITIVE_ORDER) { it.displayName }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.packageName }
    HomeAppSortMode.NAME_DESC ->
        compareBy<HomeSortKeys, String>(String.CASE_INSENSITIVE_ORDER) { it.displayName }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.packageName }
            .reversed()
    HomeAppSortMode.UPDATES_FIRST ->
        compareByDescending<HomeSortKeys> { it.hasPatchUpdate }.then(recommended)
    HomeAppSortMode.RECENTLY_PATCHED ->
        compareByDescending<HomeSortKeys> { it.patchedAt }.then(recommended)
}
