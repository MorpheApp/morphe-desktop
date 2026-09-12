/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.screens.home.components

import app.morphe.engine.model.PatchedAppRecord
import app.morphe.gui.data.model.SupportedApp
import app.morphe.gui.data.model.AppSortPreference
import app.morphe.gui.util.DevicePatchability
import app.morphe.gui.util.DevicePatchSourceAvailability
import app.morphe.gui.util.DeviceUpdateOwner
import app.morphe.gui.util.AppLabelProvenance
import app.morphe.gui.util.DiscoveredDeviceApp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppListSortingTest {
    @Test
    fun `H1 pending package fallback is immediately searchable within installed filters`() {
        val unknown = DiscoveredDeviceApp(
            packageName = "de.exaring.waipu",
            displayName = "de.exaring.waipu",
            versionCode = 11147233,
            versionName = null,
            patchability = DevicePatchability.NO_PATCH_SOURCE,
            patchNames = emptyList(),
            sourceNames = emptyList(),
            updateOwner = DeviceUpdateOwner.NotApplicable,
            patchSourceAvailability = DevicePatchSourceAvailability.NONE,
        )
        assertEquals(app.morphe.gui.util.AppLabelResolutionState.PENDING, unknown.labelResolutionState)
        assertEquals(AppLabelProvenance.PACKAGE_FALLBACK, unknown.labelProvenance)
        val known = unknown.copy(
            packageName = "com.facebook.katana",
            displayName = "com.facebook.katana",
            patchSourceAvailability = DevicePatchSourceAvailability.AVAILABLE,
        )
        val apps = listOf(known, unknown)
        listOf("de.exaring.waipu", "exaring", "waipu").forEach { query ->
            listOf(DeviceAppsFilter.ALL, DeviceAppsFilter.NO_PATCH_SOURCE).forEach { filter ->
                assertEquals(listOf(unknown), filterDeviceApps(apps, filter, query))
            }
            // The current GUI's default Known subfilter excludes unknown apps,
            // independently of their package match and label-resolution state.
            assertTrue(filterDeviceApps(apps, DeviceAppsFilter.KNOWN, query).isEmpty())
        }
        assertEquals(listOf(known), filterDeviceApps(apps, DeviceAppsFilter.ALL, "com.facebook.katana"))
        assertEquals(listOf(known), filterDeviceApps(apps, DeviceAppsFilter.KNOWN, "com.facebook.katana"))
    }

    @Test
    fun `H1 exact partial package and resolved app names remain searchable`() {
        val facebook = device("com.facebook.katana", "Facebook", 1)
        val heatIt = device("de.ka.kamedi.heat_it", "heat it", 2).copy(
            labelProvenance = AppLabelProvenance.CACHE_OF_ANDROID_RESOURCE,
        )
        val apps = listOf(facebook, heatIt)

        listOf("com.facebook.katana", "facebook").forEach { query ->
            assertEquals(listOf(facebook), filterDeviceApps(apps, DeviceAppsFilter.KNOWN, query))
        }
        listOf("de.ka.kamedi.heat_it", "kamedi", "heat it").forEach { query ->
            assertEquals(listOf(heatIt), filterDeviceApps(apps, DeviceAppsFilter.KNOWN, query))
        }
    }

    @Test
    fun `every app view defaults to name ascending`() {
        AppListFilter.entries.forEach { filter ->
            assertEquals(
                AppSortState(AppSortCriterion.NAME, AppSortDirection.ASCENDING),
                defaultSortState(filter),
            )
        }
    }

    @Test
    fun `saved sort choice is restored for its view`() {
        assertEquals(
            AppSortState(AppSortCriterion.LAST_PATCHED, AppSortDirection.DESCENDING),
            resolveSortState(
                AppListFilter.YOURS,
                AppSortPreference("LAST_PATCHED", "DESCENDING"),
            ),
        )
    }

    @Test
    fun `invalid or unavailable saved choices fall back to name ascending`() {
        val expected = AppSortState(AppSortCriterion.NAME, AppSortDirection.ASCENDING)
        assertEquals(
            expected,
            resolveSortState(AppListFilter.ALL, AppSortPreference("LAST_PATCHED", "DESCENDING")),
        )
        assertEquals(
            expected,
            resolveSortState(AppListFilter.DEVICE, AppSortPreference("NAME", "SIDEWAYS")),
        )
    }

    @Test
    fun `supported apps sort by display name in either direction`() {
        val apps = listOf(supported("pkg.z", "zeta"), supported("pkg.a", "Alpha"))

        assertEquals(
            listOf("Alpha", "zeta"),
            sortSupportedApps(apps, AppSortState(AppSortCriterion.NAME, AppSortDirection.ASCENDING))
                .map { it.displayName },
        )
        assertEquals(
            listOf("zeta", "Alpha"),
            sortSupportedApps(apps, AppSortState(AppSortCriterion.NAME, AppSortDirection.DESCENDING))
                .map { it.displayName },
        )
    }

    @Test
    fun `every app view supports package sorting`() {
        val state = AppSortState(AppSortCriterion.PACKAGE, AppSortDirection.ASCENDING)
        assertEquals(listOf("a.pkg", "z.pkg"), sortSupportedApps(
            listOf(supported("z.pkg", "Alpha"), supported("a.pkg", "Zulu")), state,
        ).map { it.packageName })
        assertEquals(listOf("a.pkg", "z.pkg"), sortPatchedApps(
            listOf(patched("z.pkg", "Alpha", 1), patched("a.pkg", "Zulu", 2)), state,
        ).map { it.packageName })
        assertEquals(listOf("a.pkg", "z.pkg"), sortDeviceApps(
            listOf(device("z.pkg", "Alpha", 1), device("a.pkg", "Zulu", 2)), state,
        ).map { it.packageName })
    }

    @Test
    fun `history sorts by patch timestamp`() {
        val apps = listOf(patched("old.pkg", "Old", 10), patched("new.pkg", "New", 20))
        val sorted = sortPatchedApps(
            apps,
            AppSortState(AppSortCriterion.LAST_PATCHED, AppSortDirection.DESCENDING),
        )

        assertEquals(listOf("new.pkg", "old.pkg"), sorted.map { it.packageName })
    }

    @Test
    fun `device apps sort numerically by installed version code`() {
        val apps = listOf(device("new.pkg", "New", 200), device("old.pkg", "Old", 10))

        assertEquals(
            listOf(10L, 200L),
            sortDeviceApps(
                apps,
                AppSortState(AppSortCriterion.INSTALLED_VERSION, AppSortDirection.ASCENDING),
            ).map { it.versionCode },
        )
    }

    @Test
    fun `device total count represents all discovered installed apps`() {
        val apps = listOf(device("one.pkg", "One", 1), device("two.pkg", "Two", 2, DevicePatchSourceAvailability.NONE))
        assertEquals(2, deviceAppsTotalCount(apps))
        assertEquals(1, filterDeviceApps(apps, DeviceAppsFilter.KNOWN, "").size)
        assertEquals(1, filterDeviceApps(apps, DeviceAppsFilter.NO_PATCH_SOURCE, "").size)
        assertEquals(2, filterDeviceApps(apps, DeviceAppsFilter.ALL, "").size)
    }

    @Test
    fun `installed version text never calls a missing version name unknown installation`() {
        assertEquals("Installed: 8.173.3 (80005957)", installedVersionLabel("8.173.3", 80005957))
        assertEquals("Installed version code: 9002103", installedVersionLabel(null, 9002103))
        assertEquals("Installed: 1.2", installedVersionLabel("1.2", null))
        assertEquals("Installed: unknown", installedVersionLabel(null, null))
    }

    @Test
    fun `device filters keep unknown apps out of the default view`() {
        val known = device("known.pkg", "Known", 1)
        val unknown = device(
            "unknown.pkg",
            "Unknown",
            2,
            DevicePatchSourceAvailability.NONE,
        )
        val apps = listOf(known, unknown)

        assertEquals(listOf("known.pkg"), filterDeviceApps(apps, DeviceAppsFilter.KNOWN, "").map { it.packageName })
        assertEquals(listOf("unknown.pkg"), filterDeviceApps(apps, DeviceAppsFilter.NO_PATCH_SOURCE, "").map { it.packageName })
        assertEquals(listOf("known.pkg", "unknown.pkg"), filterDeviceApps(apps, DeviceAppsFilter.ALL, "").map { it.packageName })
    }

    @Test
    fun `device search composes with filters and package identity stays distinct`() {
        val apps = listOf(
            device("one.pkg", "Same label", 2),
            device("two.pkg", "Same label", 1, DevicePatchSourceAvailability.NONE),
        )

        assertEquals(
            listOf("two.pkg"),
            filterDeviceApps(apps, DeviceAppsFilter.NO_PATCH_SOURCE, "two").map { it.packageName },
        )
        assertEquals(2, filterDeviceApps(apps, DeviceAppsFilter.ALL, "Same label").size)
    }

    @Test
    fun `unresolved inferred text is neither searched nor used as a real name sort key`() {
        val unresolved = device("a.package", "Pretty Alias", 1).copy(
            labelProvenance = AppLabelProvenance.PACKAGE_FALLBACK,
        )
        val resolved = device("z.package", "Actual", 1)
        assertTrue(filterDeviceApps(listOf(unresolved), DeviceAppsFilter.ALL, "Pretty").isEmpty())
        assertEquals(
            listOf("a.package", "z.package"),
            sortDeviceApps(
                listOf(resolved, unresolved),
                AppSortState(AppSortCriterion.NAME, AppSortDirection.ASCENDING),
            ).map { it.packageName },
        )
    }

    @Test
    fun `equal trustworthy names use package as deterministic tie breaker`() {
        val apps = listOf(device("z.pkg", "Same", 1), device("a.pkg", "Same", 2))
        assertEquals(
            listOf("a.pkg", "z.pkg"),
            sortDeviceApps(
                apps,
                AppSortState(AppSortCriterion.NAME, AppSortDirection.ASCENDING),
            ).map { it.packageName },
        )
    }

    @Test
    fun `sorting applies in every device filter mode`() {
        val apps = listOf(
            device("z.known", "Zulu", 3),
            device("a.unknown", "Alpha", 2, DevicePatchSourceAvailability.NONE),
            device("b.unknown", "Beta", 1, DevicePatchSourceAvailability.NONE),
        )
        val sort = AppSortState(AppSortCriterion.NAME, AppSortDirection.ASCENDING)

        DeviceAppsFilter.entries.forEach { filter ->
            val result = sortDeviceApps(filterDeviceApps(apps, filter, ""), sort)
            assertEquals(result.map { it.displayName }.sorted(), result.map { it.displayName })
        }
    }

    private fun supported(packageName: String, displayName: String) = SupportedApp(
        packageName = packageName,
        displayName = displayName,
        supportedVersions = emptyList(),
        recommendedVersion = null,
    )

    private fun patched(packageName: String, displayName: String, patchedAt: Long) = PatchedAppRecord(
        packageName = packageName,
        displayName = displayName,
        apkVersion = "1",
        inputApkPath = "input.apk",
        outputApkPath = "output.apk",
        patchedAt = patchedAt,
        patchedWithMorpheVersion = "1.15.0",
    )

    private fun device(
        packageName: String,
        displayName: String,
        versionCode: Long,
        availability: DevicePatchSourceAvailability = DevicePatchSourceAvailability.AVAILABLE,
    ) = DiscoveredDeviceApp(
        packageName = packageName,
        displayName = displayName,
        versionCode = versionCode,
        versionName = versionCode.toString(),
        patchability = DevicePatchability.PATCHABLE,
        patchNames = emptyList(),
        sourceNames = emptyList(),
        updateOwner = DeviceUpdateOwner.DesktopManaged,
        patchSourceAvailability = availability,
        labelProvenance = AppLabelProvenance.ANDROID_RESOURCE,
    )
}
