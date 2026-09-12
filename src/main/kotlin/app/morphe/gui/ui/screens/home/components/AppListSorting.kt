/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.home.components

import app.morphe.engine.model.PatchedAppRecord
import app.morphe.gui.data.model.AppSortPreference
import app.morphe.gui.data.model.SupportedApp
import app.morphe.gui.util.DiscoveredDeviceApp
import app.morphe.gui.util.DevicePatchSourceAvailability
import app.morphe.gui.util.AppLabelProvenance
import java.util.Locale

internal enum class AppSortCriterion(val label: String) {
    NAME("Name"),
    PACKAGE("Package"),
    LAST_PATCHED("Last patched"),
    INSTALLED_VERSION("Installed version"),
}

internal enum class AppSortDirection {
    ASCENDING,
    DESCENDING,
}

internal data class AppSortState(
    val criterion: AppSortCriterion,
    val direction: AppSortDirection,
)

internal enum class DeviceAppsFilter {
    KNOWN,
    NO_PATCH_SOURCE,
    ALL,
}

internal fun availableSortCriteria(filter: AppListFilter): List<AppSortCriterion> = when (filter) {
    AppListFilter.ALL -> listOf(AppSortCriterion.NAME, AppSortCriterion.PACKAGE)
    AppListFilter.YOURS -> listOf(
        AppSortCriterion.NAME,
        AppSortCriterion.PACKAGE,
        AppSortCriterion.LAST_PATCHED,
    )
    AppListFilter.DEVICE -> listOf(
        AppSortCriterion.NAME,
        AppSortCriterion.PACKAGE,
        AppSortCriterion.INSTALLED_VERSION,
    )
}

internal fun defaultSortState(@Suppress("UNUSED_PARAMETER") filter: AppListFilter): AppSortState =
    AppSortState(AppSortCriterion.NAME, AppSortDirection.ASCENDING)

internal fun resolveSortState(
    filter: AppListFilter,
    preference: AppSortPreference?,
): AppSortState {
    val criterion = preference?.criterion
        ?.let { runCatching { AppSortCriterion.valueOf(it) }.getOrNull() }
        ?.takeIf { it in availableSortCriteria(filter) }
        ?: return defaultSortState(filter)
    val direction = preference.direction
        .let { runCatching { AppSortDirection.valueOf(it) }.getOrNull() }
        ?: return defaultSortState(filter)
    return AppSortState(criterion, direction)
}

internal fun AppSortState.toPreference(): AppSortPreference = AppSortPreference(
    criterion = criterion.name,
    direction = direction.name,
)

internal fun sortSupportedApps(apps: List<SupportedApp>, state: AppSortState): List<SupportedApp> =
    apps.sortedFor(state) {
        when (state.criterion) {
            AppSortCriterion.PACKAGE -> normalized(it.packageName)
            else -> normalized(it.displayName)
        }
    }

internal fun sortPatchedApps(apps: List<PatchedAppRecord>, state: AppSortState): List<PatchedAppRecord> =
    when (state.criterion) {
        AppSortCriterion.LAST_PATCHED -> apps.sortedFor(state) { it.patchedAt }
        AppSortCriterion.PACKAGE -> apps.sortedFor(state) { normalized(it.packageName) }
        else -> apps.sortedFor(state) { normalized(it.displayName) }
    }

internal fun sortDeviceApps(apps: List<DiscoveredDeviceApp>, state: AppSortState): List<DiscoveredDeviceApp> =
    when (state.criterion) {
        AppSortCriterion.INSTALLED_VERSION -> apps.sortedWithDirection(state,
            compareBy<DiscoveredDeviceApp> { it.versionCode ?: Long.MIN_VALUE }
                .thenBy { normalized(it.packageName) },
        )
        AppSortCriterion.PACKAGE -> apps.sortedFor(state) { normalized(it.packageName) }
        else -> apps.sortedWithDirection(state,
            compareBy<DiscoveredDeviceApp> { normalized(it.trustworthyLabelOrPackage()) }
                .thenBy { normalized(it.packageName) },
        )
    }

internal fun filterDeviceApps(
    apps: List<DiscoveredDeviceApp>,
    filter: DeviceAppsFilter,
    searchQuery: String,
): List<DiscoveredDeviceApp> = apps.filter { app ->
    val matchesFilter = when (filter) {
        DeviceAppsFilter.KNOWN ->
            app.patchSourceAvailability == DevicePatchSourceAvailability.AVAILABLE
        DeviceAppsFilter.NO_PATCH_SOURCE ->
            app.patchSourceAvailability == DevicePatchSourceAvailability.NONE
        DeviceAppsFilter.ALL -> true
    }
    matchesFilter && (
        searchQuery.isBlank() ||
            (app.hasTrustworthyLabel() && app.displayName.contains(searchQuery, ignoreCase = true)) ||
            app.packageName.contains(searchQuery, ignoreCase = true)
        )
}

internal fun deviceAppsTotalCount(apps: List<DiscoveredDeviceApp>): Int = apps.size

private fun normalized(value: String): String = value.lowercase(Locale.ROOT)

internal fun DiscoveredDeviceApp.hasTrustworthyLabel(): Boolean = labelProvenance in setOf(
    AppLabelProvenance.TRUSTED_PRODUCT_METADATA,
    AppLabelProvenance.ANDROID_LITERAL,
    AppLabelProvenance.ANDROID_RESOURCE,
    AppLabelProvenance.CACHE_OF_ANDROID_RESOURCE,
)

private fun DiscoveredDeviceApp.trustworthyLabelOrPackage(): String =
    displayName.takeIf { hasTrustworthyLabel() } ?: packageName

private fun <T, K : Comparable<K>> List<T>.sortedFor(
    state: AppSortState,
    selector: (T) -> K,
): List<T> {
    val comparator = compareBy<T>(selector)
    return if (state.direction == AppSortDirection.ASCENDING) sortedWith(comparator)
    else sortedWith(comparator.reversed())
}

private fun <T> List<T>.sortedWithDirection(state: AppSortState, comparator: Comparator<T>): List<T> =
    sortedWith(if (state.direction == AppSortDirection.ASCENDING) comparator else comparator.reversed())
