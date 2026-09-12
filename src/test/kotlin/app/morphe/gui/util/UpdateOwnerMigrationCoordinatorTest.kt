/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import app.morphe.engine.installation.AdbInstallException
import app.morphe.engine.installation.AdbMigrationRequiredException
import app.morphe.engine.installation.MigrationRequiredReason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class UpdateOwnerMigrationCoordinatorTest {
    private val request = UpdateOwnerMigrationRequest("failed-device", "app.pkg", "C:/patched.apk")

    @Test
    fun `only typed ownership conflicts create a migration request`() {
        val foreign = AdbMigrationRequiredException(
            "foreign.app",
            MigrationRequiredReason.ForeignOwner("store.owner"),
            "blocked",
        )
        val noOwner = AdbMigrationRequiredException(
            "ownerless.app",
            MigrationRequiredReason.NoOwner,
            "blocked",
        )

        assertEquals(
            UpdateOwnerMigrationRequest("serial", "foreign.app", "patched.apk"),
            migrationRequestOrNull(foreign, "serial", "patched.apk"),
        )
        assertEquals(
            UpdateOwnerMigrationRequest("serial", "ownerless.app", "patched.apk"),
            migrationRequestOrNull(noOwner, "serial", "patched.apk"),
        )
        assertEquals(null, migrationRequestOrNull(AdbInstallException("ordinary failure"), "serial", "patched.apk"))
    }

    @Test
    fun `confirmation flow uses exact device package and existing apk`() = runBlocking {
        val calls = mutableListOf<List<String>>()
        val changed = mutableListOf<Pair<String, String>>()
        val coordinator = UpdateOwnerMigrationCoordinator(
            isDeviceReady = { it == "failed-device" },
            uninstall = { pkg, serial -> calls += listOf("uninstall", serial, pkg); Result.success(Unit) },
            install = { apk, serial -> calls += listOf("install", serial, apk); Result.success(Unit) },
            onPackageStateChanged = { serial, pkg -> changed += serial to pkg },
        )
        assertIs<UpdateOwnerMigrationResult.Success>(coordinator.execute(request))
        assertEquals(
            listOf(
                listOf("uninstall", "failed-device", "app.pkg"),
                listOf("install", "failed-device", "C:/patched.apk"),
            ),
            calls,
        )
        assertEquals(listOf("failed-device" to "app.pkg"), changed)
    }

    @Test
    fun `missing original device performs no operation`() = runBlocking {
        var called = false
        val coordinator = UpdateOwnerMigrationCoordinator(
            isDeviceReady = { false },
            uninstall = { _, _ -> called = true; Result.success(Unit) },
            install = { _, _ -> called = true; Result.success(Unit) },
        )
        assertIs<UpdateOwnerMigrationResult.DeviceUnavailable>(coordinator.execute(request))
        assertTrue(!called)
    }

    @Test
    fun `uninstall failure prevents reinstall`() = runBlocking {
        var installed = false
        var stateChanged = false
        val coordinator = UpdateOwnerMigrationCoordinator(
            isDeviceReady = { true },
            uninstall = { _, _ -> Result.failure(IllegalStateException("uninstall denied")) },
            install = { _, _ -> installed = true; Result.success(Unit) },
            onPackageStateChanged = { _, _ -> stateChanged = true },
        )
        val result = assertIs<UpdateOwnerMigrationResult.UninstallFailed>(coordinator.execute(request))
        assertEquals("uninstall denied", result.message)
        assertTrue(!installed)
        assertTrue(!stateChanged)
    }

    @Test
    fun `reinstall failure states that uninstall already happened`() = runBlocking {
        val changed = mutableListOf<Pair<String, String>>()
        val coordinator = UpdateOwnerMigrationCoordinator(
            isDeviceReady = { true },
            uninstall = { _, _ -> Result.success(Unit) },
            install = { _, _ -> Result.failure(IllegalStateException("install rejected")) },
            onPackageStateChanged = { serial, pkg -> changed += serial to pkg },
        )
        val result = assertIs<UpdateOwnerMigrationResult.ReinstallFailed>(coordinator.execute(request))
        assertEquals("install rejected", result.message)
        assertEquals(listOf("failed-device" to "app.pkg"), changed)
    }
}
