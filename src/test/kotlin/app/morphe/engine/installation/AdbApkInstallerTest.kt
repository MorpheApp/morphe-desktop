/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.installation

import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertIs

class AdbApkInstallerTest {
    private val apk = Files.createTempFile("morphe-installer-test", ".apk").toFile()
    private val commands = mutableListOf<List<String>>()

    @AfterTest
    fun cleanup() {
        apk.delete()
    }

    @Test
    fun `supported device uses update ownership and removes remote APK`() {
        val installer = installer { command ->
            when {
                command.takeLast(3) == listOf("shell", "pm", "help") -> ok("install [--update-ownership]")
                isPackageList(command) -> ok()
                "push" in command -> ok("1 file pushed")
                "install" in command -> ok("Success")
                "rm" in command -> ok()
                else -> error("Unexpected command: $command")
            }
        }

        val result = installer.install(request())

        assertEquals(AdbInstallMode.UPDATE_OWNERSHIP, result.getOrThrow())
        val install = commands.single { "install" in it }
        assertContains(install, "--update-ownership")
        assertTrue(commands.any { "push" in it })
        assertTrue(commands.last().contains("rm"))
    }

    @Test
    fun `unsupported device keeps legacy adb install arguments`() {
        val installer = installer { command ->
            when {
                command.takeLast(3) == listOf("shell", "pm", "help") -> ok("install [-r] [-d]")
                "install" in command -> ok("Success")
                else -> error("Unexpected command: $command")
            }
        }

        val result = installer.install(request(installerPackage = "org.fdroid.fdroid"))

        assertEquals(AdbInstallMode.LEGACY, result.getOrThrow())
        val install = commands.single { "install" in it }
        assertEquals(listOf("adb", "-s", "SERIAL", "install", "-r", "-d", "-i", "org.fdroid.fdroid", apk.absolutePath), install)
        assertFalse(commands.any { "push" in it || "rm" in it })
    }

    @Test
    fun `legacy attribution retry stays on captured serial`() {
        var installAttempts = 0
        val installer = installer { command ->
            when {
                command.takeLast(3) == listOf("shell", "pm", "help") -> ok("install [-r] [-d]")
                "install" in command -> {
                    installAttempts++
                    if (installAttempts == 1) AdbCommandResult(1, "attribution rejected") else ok("Success")
                }
                else -> error("Unexpected command: $command")
            }
        }

        val result = installer.install(request(installerPackage = "org.fdroid.fdroid"))

        assertEquals(AdbInstallMode.LEGACY, result.getOrThrow())
        val installs = commands.filter { "install" in it }
        assertEquals(2, installs.size)
        assertTrue(installs.all { it.take(3) == listOf("adb", "-s", "SERIAL") })
        assertTrue(installs.none { "OTHER" in it })
    }

    @Test
    fun `failed ownership install propagates error and still removes remote APK`() {
        val installer = installer { command ->
            when {
                command.takeLast(3) == listOf("shell", "pm", "help") -> ok("--update-ownership")
                isPackageList(command) -> ok()
                "push" in command -> ok("1 file pushed")
                "install" in command -> AdbCommandResult(1, "Failure [INSTALL_FAILED_TEST]")
                "rm" in command -> ok()
                else -> error("Unexpected command: $command")
            }
        }

        val result = installer.install(request())

        assertTrue(result.isFailure)
        assertContains(result.exceptionOrNull()?.message.orEmpty(), "INSTALL_FAILED_TEST")
        assertFalse(result.exceptionOrNull() is AdbMigrationRequiredException)
        assertTrue(commands.last().contains("rm"))
    }

    @Test
    fun `cleanup failure is reported after successful install`() {
        val installer = installer { command ->
            when {
                command.takeLast(3) == listOf("shell", "pm", "help") -> ok("--update-ownership")
                isPackageList(command) -> ok()
                "push" in command -> ok("1 file pushed")
                "install" in command -> ok("Success")
                "rm" in command -> AdbCommandResult(1, "permission denied")
                else -> error("Unexpected command: $command")
            }
        }

        val result = installer.install(request())

        assertTrue(result.isFailure)
        assertContains(result.exceptionOrNull()?.message.orEmpty(), "Failed to remove temporary APK")
    }

    @Test
    fun `failed capability probe aborts without installing`() {
        val installer = installer { command ->
            when {
                command.takeLast(3) == listOf("shell", "pm", "help") ->
                    AdbCommandResult(1, "device offline")
                else -> error("Unexpected command: $command")
            }
        }

        val result = installer.install(request())

        assertTrue(result.isFailure)
        assertContains(result.exceptionOrNull()?.message.orEmpty(), "device offline")
        assertEquals(1, commands.size)
        assertFalse(commands.any { "install" in it || "push" in it })
    }

    @Test
    fun `installed app owned by adb shell can be updated`() {
        val installer = ownershipInstaller(owner = "com.android.shell")

        val result = installer.install(request())

        assertEquals(AdbInstallMode.UPDATE_OWNERSHIP, result.getOrThrow())
        assertTrue(commands.any { "push" in it })
        assertContains(commands.single { "install" in it }, "--update-ownership")
    }

    @Test
    fun `installed app owned by Morphe Manager is blocked before push`() {
        assertForeignOwnerBlocked("app.morphe.manager")
    }

    @Test
    fun `installed app owned by another package is blocked before push`() {
        assertForeignOwnerBlocked("com.android.vending")
    }

    @Test
    fun `installed app without update owner is blocked before push`() {
        val installer = ownershipInstaller(owner = null)

        val result = installer.install(request())

        assertTrue(result.isFailure)
        val migration = assertIs<AdbMigrationRequiredException>(result.exceptionOrNull())
        assertIs<MigrationRequiredReason.NoOwner>(migration.reason)
        assertContains(result.exceptionOrNull()?.message.orEmpty(), "cannot be migrated")
        assertNoInstallOrPush()
    }

    @Test
    fun `failed owner probe aborts before push`() {
        val installer = installer { command ->
            when {
                command.takeLast(3) == listOf("shell", "pm", "help") -> ok("--update-ownership")
                isPackageList(command) -> ok("package:test.package")
                isPackageDump(command) -> AdbCommandResult(1, "device offline")
                else -> error("Unexpected command: $command")
            }
        }

        val result = installer.install(request())

        assertTrue(result.isFailure)
        assertContains(result.exceptionOrNull()?.message.orEmpty(), "stopped to protect update ownership")
        assertNoInstallOrPush()
    }

    private fun request(installerPackage: String? = null) = AdbInstallRequest(
        adbPath = "adb",
        apk = apk,
        deviceSerial = "SERIAL",
        installerPackage = installerPackage,
    )

    private fun installer(response: (List<String>) -> AdbCommandResult) = AdbApkInstaller(
        runner = AdbCommandRunner { command, _ ->
            commands += command
            response(command)
        },
        remoteName = { "fixed.apk" },
        packageNameReader = { "test.package" },
    )

    private fun ownershipInstaller(owner: String?) = installer { command ->
        when {
            command.takeLast(3) == listOf("shell", "pm", "help") -> ok("--update-ownership")
            isPackageList(command) -> ok("package:test.package")
            isPackageDump(command) -> ok(
                """
                Packages:
                  Package [test.package] (123abc):
                    versionName=1.0
                    ${owner?.let { "updateOwnerPackageName=$it" }.orEmpty()}
                """.trimIndent()
            )
            "push" in command -> ok("1 file pushed")
            "install" in command -> ok("Success")
            "rm" in command -> ok()
            else -> error("Unexpected command: $command")
        }
    }

    private fun assertForeignOwnerBlocked(owner: String) {
        val result = ownershipInstaller(owner).install(request())
        assertTrue(result.isFailure)
        val migration = assertIs<AdbMigrationRequiredException>(result.exceptionOrNull())
        assertEquals(MigrationRequiredReason.ForeignOwner(owner), migration.reason)
        assertContains(result.exceptionOrNull()?.message.orEmpty(), owner)
        assertNoInstallOrPush()
    }

    private fun assertNoInstallOrPush() {
        assertFalse(commands.any { "install" in it || "push" in it })
    }

    private fun isPackageList(command: List<String>) = command.takeLast(5) ==
        listOf("shell", "pm", "list", "packages", "test.package")

    private fun isPackageDump(command: List<String>) = command.takeLast(3) ==
        listOf("dumpsys", "package", "test.package")

    private fun ok(output: String = "") = AdbCommandResult(0, output)
}
