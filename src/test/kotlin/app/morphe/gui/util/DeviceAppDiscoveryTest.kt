/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import app.morphe.engine.installation.AdbCommandResult
import app.morphe.engine.installation.AdbCommandRunner
import app.morphe.gui.data.model.CompatiblePackage
import app.morphe.gui.data.model.Patch
import app.morphe.gui.data.model.SupportedApp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeviceAppDiscoveryTest {
    private val supported = listOf(
        SupportedApp("app.supported", "Supported", listOf("1.0"), recommendedVersion = "1.0"),
        SupportedApp("app.missing", "Missing", listOf("1.0"), recommendedVersion = "1.0"),
    )
    private val patches = listOf(
        Patch("Exact", compatiblePackages = listOf(CompatiblePackage("app.supported", versions = listOf("1.0")))),
        Patch("Universal", compatiblePackages = listOf(CompatiblePackage("app.supported"))),
    )

    @Test
    fun `installed listing preserves exact base path even when path contains equals`() {
        val parsed = DeviceAppDiscoveryService.parseInstalledPackageListings(
            "package:/data/app/~~abc==/app.pkg-xyz==/base.apk=app.pkg versionCode:7",
        ).getValue("app.pkg")
        assertEquals("/data/app/~~abc==/app.pkg-xyz==/base.apk", parsed.baseApkPath)
        assertEquals(7, parsed.versionCode)
    }

    @Test
    fun `package details capture complete cache identity and literal provenance inputs`() {
        val details = DeviceAppDiscoveryService.parsePackageDetails(
            """
            Package [app.pkg] (1):
              codePath=/data/app/app.pkg
              versionCode=7 minSdk=23
              lastUpdateTime=2026-09-08 12:00:00
              labelRes=0x7f010001 nonLocalizedLabel=Real App icon=0x0
            """.trimIndent(),
        ).getValue("app.pkg")
        assertEquals("/data/app/app.pkg/base.apk", details.baseApkPath)
        assertEquals(0x7f010001, details.labelRes)
        assertEquals("2026-09-08 12:00:00", details.lastUpdateTime)
        assertEquals("Real App", details.literalLabel)
    }

    @Test
    fun `discovers all packages but loads expensive details only for known apps`() {
        val runner = FakeRunner(
            installed = "package:app.supported versionCode:42\npackage:app.unrelated versionCode:7\n",
            dumps = mapOf("app.supported" to packageDump("app.supported", "1.0", "com.android.shell")),
        )
        val result = service(runner).discover("adb", "phone-a", supported, patches)

        assertEquals(listOf("app.supported", "app.unrelated"), result.apps.map { it.packageName })
        val known = result.apps.first { it.packageName == "app.supported" }
        val unknown = result.apps.first { it.packageName == "app.unrelated" }
        assertEquals(42, known.versionCode)
        assertEquals(DevicePatchability.PATCHABLE, known.patchability)
        assertEquals(DevicePatchSourceAvailability.AVAILABLE, known.patchSourceAvailability)
        assertEquals(listOf("Exact", "Universal"), known.patchNames)
        assertIs<DeviceUpdateOwner.DesktopManaged>(known.updateOwner)
        assertEquals(DevicePatchability.NO_PATCH_SOURCE, unknown.patchability)
        assertEquals(DevicePatchSourceAvailability.NONE, unknown.patchSourceAvailability)
        assertEquals("app.unrelated", unknown.displayName)
        assertTrue(unknown.sourceNames.isEmpty())
        assertIs<DeviceUpdateOwner.NotApplicable>(unknown.updateOwner)
        assertTrue(runner.commands.any { it.takeLast(3) == listOf("dumpsys", "package", "app.supported") })
        assertFalse(runner.commands.any { "app.unrelated" in it && "dumpsys" in it })
        assertReadOnly(runner.commands)
    }

    @Test
    fun `unknown app uses explicit Android label and keeps package identifier`() {
        val runner = FakeRunner(
            installed = "package:app.unrelated versionCode:7",
            labels = "Package [app.unrelated] (123):\n  applicationInfo=ApplicationInfo{1 app.unrelated}\n    labelRes=0x7f010001 nonLocalizedLabel=Deezer icon=0x0\n",
        )
        val app = service(runner).discover("adb", "phone", supported, patches).apps.single()
        assertEquals("Deezer", app.displayName)
        assertEquals("app.unrelated", app.packageName)
        assertEquals(1, runner.commands.count { it.takeLast(2) == listOf("dumpsys", "package") })
    }

    @Test
    fun `resource-only label falls back to package name without fabrication`() {
        val runner = FakeRunner(
            installed = "package:org.example.package versionCode:7",
            labels = "Package [org.example.package] (123):\n  labelRes=0x7f010001 nonLocalizedLabel=null icon=0x0\n",
        )
        val app = service(runner).discover("adb", "phone", supported, patches).apps.single()
        assertEquals("org.example.package", app.displayName)
    }

    @Test
    fun `only structured product metadata is trusted while inferred presentation is not an Android label`() {
        val runner = FakeRunner(installed = "package:trusted.pkg\npackage:inferred.pkg")
        val apps = service(runner).discover(
            "adb",
            "phone",
            listOf(
                SupportedApp(
                    "trusted.pkg", "Product Name", emptyList(), recommendedVersion = null,
                    trustedDisplayName = "Product Name",
                ),
                SupportedApp("inferred.pkg", "Prettified", emptyList(), recommendedVersion = null),
            ),
            emptyList(),
        ).apps.associateBy { it.packageName }
        assertEquals("Product Name", apps.getValue("trusted.pkg").displayName)
        assertEquals(AppLabelProvenance.TRUSTED_PRODUCT_METADATA, apps.getValue("trusted.pkg").labelProvenance)
        assertEquals("inferred.pkg", apps.getValue("inferred.pkg").displayName)
        assertEquals(AppLabelProvenance.PACKAGE_FALLBACK, apps.getValue("inferred.pkg").labelProvenance)
    }

    @Test
    fun `extractor retains provenance only for structured compatible-package names`() {
        val extracted = SupportedAppExtractor.extractSupportedApps(
            listOf(
                Patch(
                    "metadata",
                    compatiblePackages = listOf(
                        CompatiblePackage("named.pkg", displayName = "Named App"),
                        CompatiblePackage("plain.pkg"),
                    ),
                ),
            ),
        ).associateBy { it.packageName }
        assertEquals("Named App", extracted.getValue("named.pkg").trustedDisplayName)
        assertNull(extracted.getValue("plain.pkg").trustedDisplayName)
    }

    @Test
    fun `literal Android labels are serial isolated and refreshed from current metadata`() {
        val runner = LabelBySerialRunner()
        val discovery = service(runner)
        assertEquals("Phone A", discovery.discover("adb", "serial-a", supported, patches).apps.single().displayName)
        assertEquals("Phone B", discovery.discover("adb", "serial-b", supported, patches).apps.single().displayName)
        runner.label = "Phone A updated"
        assertEquals("Phone A updated", discovery.discover("adb", "serial-a", supported, patches).apps.single().displayName)
        discovery.invalidateLabel("serial-a", "app.unrelated")
        assertEquals("Phone A updated", discovery.discover("adb", "serial-a", supported, patches).apps.single().displayName)
    }

    @Test
    fun `system and user apps are classified independently of patchability`() {
        val runner = FakeRunner(
            installed = "package:app.supported versionCode:42\npackage:app.unrelated versionCode:7\n",
            systemInstalled = "package:app.supported\n",
            dumps = mapOf("app.supported" to packageDump("app.supported", "1.0", "com.android.shell")),
        )

        val apps = service(runner).discover("adb", "phone", supported, patches).apps.associateBy { it.packageName }

        assertEquals(InstalledAppType.SYSTEM, apps.getValue("app.supported").installedAppType)
        assertEquals(DevicePatchability.PATCHABLE, apps.getValue("app.supported").patchability)
        assertEquals(InstalledAppType.USER, apps.getValue("app.unrelated").installedAppType)
    }

    @Test
    fun `failed system classification remains unknown instead of guessing user app`() {
        val runner = FakeRunner(
            installed = "package:app.supported versionCode:42",
            systemExit = 1,
            dumps = mapOf("app.supported" to packageDump("app.supported", "1.0", "com.android.shell")),
        )

        val app = service(runner).discover("adb", "phone", supported, patches).apps.single()

        assertEquals(InstalledAppType.UNKNOWN, app.installedAppType)
    }

    @Test
    fun `known but incompatible version is reported`() {
        val runner = FakeRunner(
            installed = "package:app.supported versionCode:99",
            dumps = mapOf("app.supported" to packageDump("app.supported", "2.0", null)),
        )
        val exactOnly = patches.filter { it.name == "Exact" }
        val app = service(runner).discover("adb", "phone", supported, exactOnly).apps.single()
        assertEquals(DevicePatchability.INCOMPATIBLE_VERSION, app.patchability)
        assertTrue(app.patchNames.isEmpty())
        assertIs<DeviceUpdateOwner.NoOwner>(app.updateOwner)
    }

    @Test
    fun `missing version is not falsely confirmed`() {
        val runner = FakeRunner(
            installed = "package:app.supported versionCode:42",
            dumps = mapOf("app.supported" to packageDump("app.supported", null, "app.morphe.manager")),
        )
        val app = service(runner).discover("adb", "phone", supported, patches).apps.single()
        assertEquals(DevicePatchability.VERSION_NOT_CONFIRMED, app.patchability)
        assertIs<DeviceUpdateOwner.MorpheManager>(app.updateOwner)
    }

    @Test
    fun `foreign owner and capability failures remain distinct`() {
        val foreign = FakeRunner(
            installed = "package:app.supported",
            dumps = mapOf("app.supported" to packageDump("app.supported", "1.0", "store.owner")),
        )
        assertEquals(
            DeviceUpdateOwner.Other("store.owner"),
            service(foreign).discover("adb", "one", supported, patches).apps.single().updateOwner,
        )

        val failed = FakeRunner(
            installed = "package:app.supported",
            capability = AdbCommandResult(1, "denied"),
            dumps = mapOf("app.supported" to packageDump("app.supported", "1.0", null)),
        )
        assertIs<DeviceUpdateOwner.Unavailable>(
            service(failed).discover("adb", "two", supported, patches).apps.single().updateOwner,
        )
    }

    @Test
    fun `unsupported ownership capability produces no migration warning`() {
        val runner = FakeRunner(
            installed = "package:app.supported",
            capability = AdbCommandResult(0, "Package manager help"),
            dumps = mapOf("app.supported" to packageDump("app.supported", "1.0", null)),
        )
        assertIs<DeviceUpdateOwner.Unsupported>(
            service(runner).discover("adb", "phone", supported, patches).apps.single().updateOwner,
        )
    }

    @Test
    fun `single package detail failure stays unavailable without destroying discovery`() {
        val runner = FakeRunner(installed = "package:app.supported versionCode:42")
        val app = service(runner).discover("adb", "phone", supported, patches).apps.single()
        assertEquals(DevicePatchability.VERSION_NOT_CONFIRMED, app.patchability)
        assertIs<DeviceUpdateOwner.Unavailable>(app.updateOwner)
    }

    @Test
    fun `only supplied active source patches participate and source priority is preserved`() {
        val runner = FakeRunner(
            installed = "package:app.supported",
            dumps = mapOf("app.supported" to packageDump("app.supported", "1.0", "com.android.shell")),
        )
        val activePatch = patches.single { it.name == "Exact" }
        val app = service(runner).discover(
            "adb",
            "phone",
            supported,
            listOf(activePatch),
            mapOf("app.supported" to listOf("Priority source", "Fallback source")),
        ).apps.single()
        assertEquals(listOf("Exact"), app.patchNames)
        assertEquals(listOf("Priority source", "Fallback source"), app.sourceNames)
    }

    @Test
    fun `device snapshots retain their serial and discovery errors are controlled`() {
        val one = FakeRunner("package:app.supported", dumps = mapOf("app.supported" to packageDump("app.supported", "1.0", "com.android.shell")))
        val two = FakeRunner("package:app.supported", dumps = mapOf("app.supported" to packageDump("app.supported", "1.0", "app.morphe.manager")))
        val first = service(one).discover("adb", "serial-one", supported, patches)
        val second = service(two).discover("adb", "serial-two", supported, patches)
        assertEquals("serial-one", first.deviceSerial)
        assertEquals("serial-two", second.deviceSerial)
        assertTrue(first.apps.single().updateOwner != second.apps.single().updateOwner)

        val broken = FakeRunner("", installedExit = 1)
        val error = service(broken).discover("adb", "offline", supported, patches)
        assertTrue(error.apps.isEmpty())
        assertTrue(error.error!!.contains("installed packages"))
    }

    private fun service(runner: AdbCommandRunner) = DeviceAppDiscoveryService(
        runner,
        InstalledAppLabelResolver(
            cache = InstalledAppLabelCache(
                java.io.File(kotlin.io.path.createTempDirectory("morphe-discovery-test-").toFile(), "labels.json"),
            ),
        ),
    )

    private fun packageDump(pkg: String, version: String?, owner: String?): String = buildString {
        appendLine("Package [$pkg] (123):")
        version?.let { appendLine("  versionName=$it") }
        appendLine("  versionCode=42 minSdk=23")
        owner?.let { appendLine("  updateOwnerPackageName=$it") }
    }

    private fun assertReadOnly(commands: List<List<String>>) {
        val forbidden = setOf("install", "uninstall", "push", "pull", "clear", "enable", "disable")
        assertTrue(commands.all { command -> command.none { it in forbidden } })
    }

    private class FakeRunner(
        private val installed: String,
        private val installedExit: Int = 0,
        private val systemInstalled: String = "",
        private val systemExit: Int = 0,
        private val capability: AdbCommandResult = AdbCommandResult(0, "--update-ownership"),
        private val dumps: Map<String, String> = emptyMap(),
        private val labels: String = "",
    ) : AdbCommandRunner {
        val commands = mutableListOf<List<String>>()

        override fun run(command: List<String>, onOutput: (String) -> Unit): AdbCommandResult {
            commands += command
            return when {
                command.takeLast(5) == listOf("pm", "list", "packages", "-f", "--show-versioncode") ->
                    AdbCommandResult(installedExit, installed)
                command.takeLast(4) == listOf("pm", "list", "packages", "-s") ->
                    AdbCommandResult(systemExit, systemInstalled)
                command.takeLast(3) == listOf("cmd", "activity", "get-current-user") -> AdbCommandResult(0, "0")
                command.takeLast(2) == listOf("getprop", "persist.sys.locale") -> AdbCommandResult(0, "en-US")
                command.takeLast(2) == listOf("pm", "help") -> capability
                command.takeLast(2) == listOf("dumpsys", "package") -> AdbCommandResult(0, labels)
                command.size >= 3 && command[command.lastIndex - 2] == "dumpsys" -> {
                    val pkg = command.last()
                    dumps[pkg]?.let { AdbCommandResult(0, it) } ?: AdbCommandResult(1, "missing")
                }
                else -> error("Unexpected command: $command")
            }
        }
    }

    private class LabelBySerialRunner : AdbCommandRunner {
        var label = "Phone A"
        override fun run(command: List<String>, onOutput: (String) -> Unit): AdbCommandResult = when {
            command.takeLast(5) == listOf("pm", "list", "packages", "-f", "--show-versioncode") ->
                AdbCommandResult(0, "package:app.unrelated versionCode:7")
            command.takeLast(4) == listOf("pm", "list", "packages", "-s") -> AdbCommandResult(0, "")
            command.takeLast(3) == listOf("cmd", "activity", "get-current-user") -> AdbCommandResult(0, "0")
            command.takeLast(2) == listOf("getprop", "persist.sys.locale") -> AdbCommandResult(0, "en-US")
            command.takeLast(2) == listOf("dumpsys", "package") ->
                AdbCommandResult(0, "Package [app.unrelated] (123):\n nonLocalizedLabel=${if (command.getOrNull(2) == "serial-b") "Phone B" else label} icon=0x0")
            else -> error("Unexpected command: $command")
        }
    }
}
