/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import java.io.File
import java.util.concurrent.CancellationException
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InstalledAppLabelsTest {
    private fun compressedWire(bytes: ByteArray): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        output.write('G'.code)
        java.util.zip.GZIPOutputStream(output).use { it.write(bytes) }
        return output.toByteArray()
    }

    @Test
    fun `compressed and raw resource transport preserve exact bytes`() {
        val bytes = ByteArray(65536) { (it % 251).toByte() }
        listOf(compressedWire(bytes), byteArrayOf('R'.code.toByte()) + bytes).forEach { wire ->
            val result = ProcessInstalledAppDeviceClient.decodeResourceTransfer(DeviceEntryResult(0, wire, ""))
            assertEquals(0, result.exitCode)
            assertTrue(bytes.contentEquals(result.stdout))
        }
    }

    @Test
    fun `resource transport rejects corrupt truncated empty oversized and failed streams`() {
        val valid = compressedWire(ByteArray(100) { 42 })
        val corrupt = valid.copyOf().apply { this[lastIndex - 4] = (this[lastIndex - 4].toInt() xor 1).toByte() }
        listOf(byteArrayOf(), byteArrayOf('X'.code.toByte()), byteArrayOf('R'.code.toByte()),
            valid.copyOf(valid.size - 4), corrupt, compressedWire(byteArrayOf())).forEach {
            val result = ProcessInstalledAppDeviceClient.decodeResourceTransfer(DeviceEntryResult(0, it, ""))
            assertEquals(1, result.exitCode)
            assertTrue(result.stdout.isEmpty())
        }
        assertEquals(1, ProcessInstalledAppDeviceClient.decodeResourceTransfer(DeviceEntryResult(0, valid, ""), 99).exitCode)
        assertEquals(1, ProcessInstalledAppDeviceClient.decodeResourceTransfer(
            DeviceEntryResult(0, byteArrayOf('R'.code.toByte()) + ByteArray(100), ""), 99).exitCode)
        val failed = ProcessInstalledAppDeviceClient.decodeResourceTransfer(DeviceEntryResult(17, valid, "unzip failed"))
        assertEquals(17, failed.exitCode)
        assertEquals("unzip failed", failed.stderr)
        assertTrue(failed.stdout.isEmpty())
    }

    @Test
    fun `compressed resource command preserves target safety and direct manifest transport`() {
        val calls = mutableListOf<List<String>>()
        val client = ProcessInstalledAppDeviceClient { command ->
            calls += command
            DeviceEntryResult(0, compressedWire(byteArrayOf(1, 2, 3)), "")
        }
        client.readApkEntry("adb", "serial-a", identity().baseApkPath, "resources.arsc")
        assertEquals(listOf("adb", "-s", "serial-a", "exec-out"), calls.single().take(4))
        val command = calls.single().last()
        assertTrue(command.contains("set -o pipefail"))
        assertTrue(command.contains("command -v gzip"))
        assertTrue(command.contains("gzip -1c"))
        assertTrue(command.contains("else printf R; unzip -p '/data/app/app.pkg/base.apk' resources.arsc"))
        client.readApkEntry("adb", "serial-b", identity().baseApkPath, "AndroidManifest.xml")
        assertEquals(listOf("adb", "-s", "serial-b", "exec-out", "unzip", "-p",
            identity().baseApkPath, "AndroidManifest.xml"), calls.last())
        assertNull(client.readManifestBatch("adb", "serial-b", listOf(identity().baseApkPath)))
        assertEquals(2, calls.size)
        assertFailsWith<IllegalArgumentException> {
            client.readApkEntry("adb", "serial-a", "/data/app/a';touch /bad.apk", "resources.arsc")
        }
        assertEquals(2, calls.size)
    }

    @Test
    fun `parsed manifest names and absent labels survive restart without device reads`() {
        listOf("Real app name", null).forEach { literal ->
            val file = tempFile()
            val inventory = identity().copy(labelRes = null)
            val first = resolver(FakeDevice(), file, InstalledAppLabelResolver.ParsedManifest(literal, null))
            first.resolve("adb", inventory, "de-DE")
            val device = FakeDevice()
            val restarted = resolver(device, file, InstalledAppLabelResolver.ParsedManifest(literal, null))
            restarted.prune("serial-a", 0, setOf(inventory))
            val outcome = restarted.resolve("adb", inventory, "de-DE")
            if (literal == null) assertIs<InstalledAppLabelOutcome.Terminal>(outcome)
            else assertEquals(literal, assertIs<InstalledAppLabelOutcome.Resolved>(outcome).label.value)
            assertTrue(device.entries.isEmpty())
            restarted.invalidate("serial-a", "app.pkg")
            restarted.resolve("adb", inventory, "de-DE")
            assertEquals(1, device.entries.size)
        }
    }

    @Test
    fun `persistent manifest metadata rejects changed identity locale and missing package`() {
        val file = tempFile()
        val cache = InstalledAppLabelCache(file)
        val original = identity().copy(labelRes = null)
        cache.putManifestOutcome(original, "de-DE", "Name")
        val reopened = InstalledAppLabelCache(file)
        listOf(original.copy(deviceSerial = "other"), original.copy(androidUserId = 10),
            original.copy(versionCode = 8), original.copy(lastUpdateTime = "later"),
            original.copy(baseApkPath = "/data/app/other/base.apk"), original.copy(labelRes = 1)).forEach {
            assertNull(reopened.manifestOutcome(it, "de-DE"))
        }
        assertNull(reopened.manifestOutcome(original, "en-US"))
        reopened.prune("serial-a", 0, emptySet())
        assertNull(InstalledAppLabelCache(file).manifestOutcome(original, "de-DE"))
    }

    @Test
    fun `failed manifest parse is not persisted across restart`() {
        val file = tempFile()
        val device = FakeDevice()
        repeat(2) {
            val resolver = InstalledAppLabelResolver(InstalledAppLabelCache(file), device,
                manifestParser = { error("parse failure") })
            assertIs<InstalledAppLabelOutcome.Retryable>(resolver.resolve("adb", identity(), "de-DE"))
        }
        assertEquals(2, device.entries.size)
        assertFalse(file.exists())
    }

    @Test
    fun `real resource table with language but null region resolves label`() {
        val table = com.reandroid.arsc.chunk.TableBlock()
        val pkg = table.packageArray.createNext().apply { id = 0x7f; name = "app.pkg" }
        val config = com.reandroid.arsc.value.ResConfig().apply { setLanguage("de") }
        assertNull(config.region)
        val entry = pkg.getOrCreate(config, "string", "app_name")
        entry.setValueAsString("Übersetzer")
        table.refreshFull()
        val result = InstalledAppLabelResolver.parseResource(table.bytes, entry.resourceId, "de-DE")
        assertEquals("app.pkg:string/app_name", result?.resourceName)
        assertEquals("Übersetzer", result?.unambiguousValue)
        assertTrue(result?.overlayLookupSafe == true)
    }

    @Test
    fun `duplicate compiled resource names disable descriptor lookup`() {
        val table = com.reandroid.arsc.chunk.TableBlock()
        val pkg = table.packageArray.createNext().apply { id = 0x7f; name = "app.pkg" }
        val config = com.reandroid.arsc.value.ResConfig()
        val label = pkg.getOrCreate(config, "string", "label").apply { setValueAsString("Correct") }
        val unrelated = pkg.getOrCreate(config, "string", "policy").apply { setValueAsString("Wrong") }
        label.setName("0_resource_name_obfuscated")
        unrelated.setName("0_resource_name_obfuscated")
        table.refreshFull()

        val result = InstalledAppLabelResolver.parseResource(table.bytes, label.resourceId, "de-DE")

        assertEquals("Correct", result?.unambiguousValue)
        assertFalse(result?.overlayLookupSafe ?: true)
    }

    @Test
    fun `ambiguous descriptor bypasses overlay and heals its stale cached label`() {
        val file = tempFile()
        val identity = identity()
        InstalledAppLabelCache(file).apply {
            putDescriptor(identity, "app.pkg:string/0_resource_name_obfuscated")
            putLabel(identity, "de-DE", InstalledAppLabel("Wrong policy text", AppLabelProvenance.ANDROID_RESOURCE))
        }
        assertNull(InstalledAppLabelCache(file).label(identity, "de-DE"))
        val client = object : InstalledAppDeviceClient {
            override fun readApkEntry(
                adbPath: String,
                serial: String,
                baseApkPath: String,
                entryName: String,
            ) = DeviceEntryResult(0, byteArrayOf(), "")
            override fun overlayLookup(
                adbPath: String,
                serial: String,
                userId: Int,
                packageName: String,
                resourceName: String,
            ): DeviceEntryResult = error("Ambiguous descriptor must not reach Android lookup")
        }
        val resolver = InstalledAppLabelResolver(
            InstalledAppLabelCache(file),
            client,
            manifestParser = { InstalledAppLabelResolver.ParsedManifest(null, identity.labelRes) },
            resourceParser = { _, _, _ -> InstalledAppLabelResolver.ParsedResource(
                "app.pkg:string/0_resource_name_obfuscated",
                "Correct",
                overlayLookupSafe = false,
            ) },
        )

        val outcome = assertIs<InstalledAppLabelOutcome.Resolved>(resolver.resolve("adb", identity, "de-DE"))

        assertEquals("Correct", outcome.label.value)
        assertNull(InstalledAppLabelCache(file).descriptor(identity))
        assertEquals("Correct", InstalledAppLabelCache(file).label(identity, "de-DE")?.value)
    }

    @Test
    fun `401 unchanged installs perform zero label transfers on second enrichment`() {
        val device = FakeDevice()
        val resolver = resolver(device)
        val service = DeviceAppDiscoveryService(labelResolver = resolver)
        val apps = (1..401).map {
            app("app.pkg$it", InstalledAppType.USER).let { app ->
                app.copy(installIdentity = app.installIdentity!!.copy(labelRes = null))
            }
        }
        val inventory = DeviceAppDiscoverySnapshot("serial-a", apps, androidUserId = 0, localeTag = "de-DE")
        service.enrichLabels("adb", inventory) {}
        val initialReads = device.entries.size
        assertEquals(802, initialReads)
        resolver.prune("serial-a", 0, apps.mapNotNull { it.installIdentity }.toSet())
        val refreshed = service.enrichLabels("adb", inventory) {}
        assertEquals(AppLabelIndexState.COMPLETE, refreshed.labelIndexState)
        assertEquals(initialReads, device.entries.size)
    }

    @Test
    fun `real manifest and resource parsers safely share ARSCLib decoders across workers`() {
        val label = "Übersetzer 日本語 " + "Name".repeat(100)
        val manifest = manifestBytes(AndroidManifestBlock.empty().apply {
            packageName = "app.pkg"
            setApplicationLabel(label)
        })
        val table = com.reandroid.arsc.chunk.TableBlock()
        val pkg = table.packageArray.createNext().apply { id = 0x7f; name = "app.pkg" }
        val entry = pkg.getOrCreate(com.reandroid.arsc.value.ResConfig(), "string", "app_name")
        entry.setValueAsString(label)
        table.refreshFull()
        val bytes = table.bytes
        val id = entry.resourceId
        val executor = java.util.concurrent.Executors.newFixedThreadPool(4)
        val start = java.util.concurrent.CountDownLatch(1)
        try {
            val tasks = (1..4).map { worker -> executor.submit {
                start.await()
                repeat(200) {
                    if (worker % 2 == 0) assertEquals(label, InstalledAppLabelResolver.parseManifest(manifest).literal)
                    else assertEquals(label, InstalledAppLabelResolver.parseResource(bytes, id, "de-DE")?.unambiguousValue)
                }
            } }
            start.countDown()
            tasks.forEach { it.get(20, java.util.concurrent.TimeUnit.SECONDS) }
        } finally { start.countDown(); executor.shutdownNow() }
    }

    @Test
    fun `refresh inventory without labelRes keeps cache and reuses completed labels`() {
        val device = FakeDevice()
        val cacheFile = tempFile()
        val resolver = InstalledAppLabelResolver(
            cache = InstalledAppLabelCache(cacheFile), device = device,
            manifestParser = { bytes -> when (bytes.single().toInt()) {
                3 -> InstalledAppLabelResolver.ParsedManifest("Literal", null)
                4 -> InstalledAppLabelResolver.ParsedManifest(null, null)
                else -> InstalledAppLabelResolver.ParsedManifest(null, 0x7f010001)
            } },
            resourceParser = { _, _, _ -> InstalledAppLabelResolver.ParsedResource("app.pkg:string/app_name", "Name") },
        )
        val runner = app.morphe.engine.installation.AdbCommandRunner { command, _ ->
            val output = when {
                command.contains("--show-versioncode") -> "package:/data/app/app.pkg/base.apk=app.pkg versionCode:7"
                command.contains("get-current-user") -> "0"
                command.contains("getprop") -> "de-DE"
                command.contains("dumpsys") -> "Package [app.pkg] (123):\n codePath=/data/app/app.pkg\n versionCode=7\n lastUpdateTime=now\n"
                else -> ""
            }
            app.morphe.engine.installation.AdbCommandResult(0, output)
        }
        val service = DeviceAppDiscoveryService(runner, resolver)
        val first = service.discover("adb", "serial-a", emptyList(), emptyList())
        assertNull(first.apps.single().installIdentity!!.labelRes)
        service.enrichLabels("adb", first) {}
        val reads = device.entries.size
        val refreshed = service.discover("adb", "serial-a", emptyList(), emptyList())
        service.enrichLabels("adb", refreshed) {}
        assertEquals(reads, device.entries.size)
        assertEquals(AppLabelIndexState.COMPLETE, refreshed.labelIndexState)
        assertEquals(AppLabelProvenance.CACHE_OF_ANDROID_RESOURCE, refreshed.apps.single().labelProvenance)
        assertEquals("Resolved", InstalledAppLabelCache(cacheFile).label(identity().copy(labelRes = null), "de-DE")?.value)
    }

    @Test
    fun `literal and no-label outcomes are session reused but mutations and locale invalidate`() {
        listOf(InstalledAppLabelResolver.ParsedManifest("Literal", null),
            InstalledAppLabelResolver.ParsedManifest(null, null)).forEach { manifest ->
            val device = FakeDevice()
            val resolver = resolver(device, manifest = manifest)
            val inventory = identity().copy(labelRes = null)
            resolver.resolve("adb", inventory, "de-DE")
            resolver.prune("serial-a", 0, setOf(inventory))
            resolver.resolve("adb", inventory, "de-DE")
            assertEquals(1, device.entries.size)
            resolver.resolve("adb", inventory, "en-US")
            assertEquals(2, device.entries.size)
            resolver.invalidate("serial-a", "app.pkg")
            resolver.resolve("adb", inventory, "de-DE")
            assertEquals(3, device.entries.size)
            resolver.resolve("adb", inventory.copy(lastUpdateTime = "changed"), "de-DE")
            assertEquals(4, device.entries.size)
        }
    }

    @Test
    fun `unknown labelRes is not a wildcard for different installation or ambiguous records`() {
        val cache = InstalledAppLabelCache(tempFile())
        cache.putLabel(identity(), "de-DE", InstalledAppLabel("Name", AppLabelProvenance.ANDROID_RESOURCE))
        val inventory = identity().copy(labelRes = null)
        listOf(inventory.copy(deviceSerial = "other"), inventory.copy(androidUserId = 10),
            inventory.copy(versionCode = 8), inventory.copy(lastUpdateTime = "later"),
            inventory.copy(baseApkPath = "/data/app/changed/base.apk")).forEach {
            assertNull(cache.label(it, "de-DE"))
        }
        cache.putLabel(identity().copy(labelRes = 2), "de-DE", InstalledAppLabel("Other", AppLabelProvenance.ANDROID_RESOURCE))
        assertNull(cache.label(inventory, "de-DE"))
    }

    @Test
    fun `parse failures are retried and never session cached`() {
        val device = FakeDevice()
        val resolver = InstalledAppLabelResolver(InstalledAppLabelCache(tempFile()), device,
            manifestParser = { error("bad manifest") })
        repeat(2) { assertIs<InstalledAppLabelOutcome.Retryable>(resolver.resolve("adb", identity(), "de-DE")) }
        assertEquals(2, device.entries.size)
    }

    @Test
    fun `Pixel system extension APK label reads retain strict path boundaries`() {
        listOf(
            "/system_ext/priv-app/SystemUIGoogle/SystemUIGoogle.apk",
            "/system_ext/app/EmergencyInfoGoogleNoUi/EmergencyInfoGoogleNoUi.apk",
        ).forEach { path ->
            ProcessInstalledAppDeviceClient.requireSafeBaseApkPath(path)
            assertFalse(DeviceAppImportService.isSafePackageApkPath(path))
        }
        listOf(
            "/system_ext/../data/private.apk",
            "/system_ext_fake/app.apk",
            "/system_ext/app.apk;id",
            "/system_ext/$(id).apk",
            "/system_ext/a\n.apk",
            "/system_ext/app/resources.arsc",
            "/data/local/tmp/app.apk",
        ).forEach { path ->
            assertFailsWith<IllegalArgumentException>(path) {
                ProcessInstalledAppDeviceClient.requireSafeBaseApkPath(path)
            }
        }
    }

    @Test
    fun `publication identity rejects cross-device user version path and timestamp results`() {
        val original = identity()
        assertTrue(sameInstalledAppIdentity(original, original))
        assertTrue(sameInstalledAppIdentity(original.copy(labelRes = 1), original.copy(labelRes = null)))
        assertFalse(sameInstalledAppIdentity(original.copy(labelRes = 1), original))
        assertFalse(sameInstalledAppIdentity(original.copy(deviceSerial = "serial-b"), original))
        assertFalse(sameInstalledAppIdentity(original.copy(androidUserId = 10), original))
        assertFalse(sameInstalledAppIdentity(original.copy(versionCode = 8), original))
        assertFalse(sameInstalledAppIdentity(original.copy(baseApkPath = "/data/app/other/base.apk"), original))
        assertFalse(sameInstalledAppIdentity(original.copy(lastUpdateTime = "later"), original))
    }

    @Test
    fun `ARSCLib manifest probe distinguishes literal and numeric labels`() {
        val literal = AndroidManifestBlock.empty().apply {
            packageName = "app.pkg"
            setApplicationLabel("Literal")
        }
        assertEquals(
            InstalledAppLabelResolver.ParsedManifest("Literal", null),
            InstalledAppLabelResolver.parseManifest(manifestBytes(literal)),
        )
        val resource = AndroidManifestBlock.empty().apply {
            packageName = "app.pkg"
            setApplicationLabel(0x7f010001)
        }
        assertEquals(
            0x7f010001,
            InstalledAppLabelResolver.parseManifest(manifestBytes(resource)).labelRes,
        )
    }

    @Test
    fun `cache identity isolates serial user install fields and locale`() {
        val file = tempFile()
        val cache = InstalledAppLabelCache(file)
        val identity = identity()
        cache.putDescriptor(identity, "app.pkg:string/app_name")
        cache.putLabel(identity, "de-DE", InstalledAppLabel("Echter Name", AppLabelProvenance.ANDROID_RESOURCE))

        val reopened = InstalledAppLabelCache(file)
        assertEquals("app.pkg:string/app_name", reopened.descriptor(identity))
        assertEquals("Echter Name", reopened.label(identity, "de-DE")?.value)
        assertNull(reopened.label(identity, "en-US"))
        assertNull(reopened.descriptor(identity.copy(deviceSerial = "other")))
    }

    @Test
    fun `all installation identity changes invalidate cache hits`() {
        val cache = InstalledAppLabelCache(tempFile())
        val original = identity()
        cache.putDescriptor(original, "app.pkg:string/app_name")
        cache.putLabel(original, "de-DE", InstalledAppLabel("Name", AppLabelProvenance.ANDROID_RESOURCE))
        val changed = listOf(
            original.copy(deviceSerial = "serial-b"),
            original.copy(androidUserId = 10),
            original.copy(packageName = "other.pkg"),
            original.copy(versionCode = 8),
            original.copy(baseApkPath = "/data/app/other/base.apk"),
            original.copy(lastUpdateTime = "later"),
            original.copy(labelRes = 0x7f010002),
        )
        changed.forEach {
            assertNull(cache.descriptor(it))
            assertNull(cache.label(it, "de-DE"))
        }
    }

    @Test
    fun `corrupt cache and untrusted values fail safe`() {
        val file = tempFile().apply { writeText("not-json") }
        val cache = InstalledAppLabelCache(file)
        assertNull(cache.label(identity(), "de-DE"))
        cache.putLabel(identity(), "de-DE", InstalledAppLabel("Pkg", AppLabelProvenance.PACKAGE_FALLBACK))
        cache.putLabel(identity(), "de-DE", InstalledAppLabel("Pretty", AppLabelProvenance.INFERRED_PRESENTATION))
        assertNull(cache.label(identity(), "de-DE"))
    }

    @Test
    fun `mutation invalidation and absent-install pruning remove both cache layers`() {
        val cache = InstalledAppLabelCache(tempFile())
        val first = identity()
        val second = identity().copy(packageName = "other.pkg")
        listOf(first, second).forEach {
            cache.putDescriptor(it, "${it.packageName}:string/app_name")
            cache.putLabel(it, "de-DE", InstalledAppLabel("Name", AppLabelProvenance.ANDROID_RESOURCE))
        }
        cache.invalidate(first.deviceSerial, first.packageName)
        assertNull(cache.descriptor(first))
        assertNull(cache.label(first, "de-DE"))
        cache.prune(second.deviceSerial, second.androidUserId, emptySet())
        assertNull(cache.descriptor(second))
        assertNull(cache.label(second, "de-DE"))
    }

    @Test
    fun `literal label resolves without resource transfer`() {
        val device = FakeDevice()
        val resolver = resolver(device, manifest = InstalledAppLabelResolver.ParsedManifest("Literal", null))
        val outcome = assertIs<InstalledAppLabelOutcome.Resolved>(resolver.resolve("adb", identity(), "de-DE"))
        assertEquals("Literal", outcome.label.value)
        assertEquals(AppLabelProvenance.ANDROID_LITERAL, outcome.label.provenance)
        assertEquals(listOf("AndroidManifest.xml"), device.entries)
    }

    @Test
    fun `cold resource path stores descriptor and localized label`() {
        val file = tempFile()
        val device = FakeDevice(overlay = DeviceEntryResult(0, "waipu.tv\n".toByteArray(), ""))
        val resolver = resolver(device, file = file)
        val outcome = assertIs<InstalledAppLabelOutcome.Resolved>(resolver.resolve("adb", identity(), "de-DE"))
        assertEquals("waipu.tv", outcome.label.value)
        assertEquals(AppLabelProvenance.ANDROID_RESOURCE, outcome.label.provenance)
        assertEquals(listOf("AndroidManifest.xml", "resources.arsc"), device.entries)
        val cached = InstalledAppLabelCache(file)
        assertEquals("app.pkg:string/(name removed)", cached.descriptor(identity()))
        assertEquals("waipu.tv", cached.label(identity(), "de-DE")?.value)
    }

    @Test
    fun `descriptor warm path uses overlay without resource transfer`() {
        val file = tempFile()
        InstalledAppLabelCache(file).putDescriptor(identity(), "app.pkg:string/app_name")
        val device = FakeDevice(overlay = DeviceEntryResult(0, "Warm\n".toByteArray(), ""))
        val resolver = resolver(device, file = file)
        val outcome = assertIs<InstalledAppLabelOutcome.Resolved>(resolver.resolve("adb", identity(), "de-DE"))
        assertEquals("Warm", outcome.label.value)
        assertEquals(listOf("AndroidManifest.xml"), device.entries)
    }

    @Test
    fun `localized cache is zero-transfer warm path`() {
        val file = tempFile()
        InstalledAppLabelCache(file).putLabel(
            identity(), "de-DE", InstalledAppLabel("Cached", AppLabelProvenance.ANDROID_RESOURCE),
        )
        val device = FakeDevice()
        val outcome = assertIs<InstalledAppLabelOutcome.Resolved>(resolver(device, file = file).resolve("adb", identity(), "de-DE"))
        assertEquals("Cached", outcome.label.value)
        assertEquals(AppLabelProvenance.CACHE_OF_ANDROID_RESOURCE, outcome.label.provenance)
        assertTrue(device.entries.isEmpty())
    }

    @Test
    fun `overlay failure uses unambiguous ARSC fallback`() {
        val device = FakeDevice(overlay = DeviceEntryResult(1, byteArrayOf(), "unsupported"))
        val outcome = assertIs<InstalledAppLabelOutcome.Resolved>(resolver(device).resolve("adb", identity(), "de-DE"))
        assertEquals("ARSCLib value", outcome.label.value)
    }

    @Test
    fun `transfers and overlay ambiguity remain retryable while no label is terminal`() {
        val transferFailure = FakeDevice(manifest = DeviceEntryResult(1, byteArrayOf(), "offline"))
        assertIs<InstalledAppLabelOutcome.Retryable>(resolver(transferFailure).resolve("adb", identity(), "de-DE"))

        val noLabel = resolver(FakeDevice(), manifest = InstalledAppLabelResolver.ParsedManifest(null, null))
        assertIs<InstalledAppLabelOutcome.Terminal>(noLabel.resolve("adb", identity().copy(labelRes = null), "de-DE"))

        val ambiguous = resolver(
            FakeDevice(overlay = DeviceEntryResult(1, byteArrayOf(), "missing")),
            resourceValue = null,
        )
        assertIs<InstalledAppLabelOutcome.Retryable>(ambiguous.resolve("adb", identity(), "de-DE"))
    }

    @Test
    fun `cancellation propagates and is never negative cached`() {
        val device = object : InstalledAppDeviceClient {
            override fun readApkEntry(adbPath: String, serial: String, baseApkPath: String, entryName: String): DeviceEntryResult =
                throw CancellationException("cancel")
            override fun overlayLookup(adbPath: String, serial: String, userId: Int, packageName: String, resourceName: String) =
                error("not reached")
        }
        assertFailsWith<CancellationException> { resolver(device).resolve("adb", identity(), "de-DE") }
        val parserCancellation = InstalledAppLabelResolver(
            cache = InstalledAppLabelCache(tempFile()),
            device = FakeDevice(),
            manifestParser = { throw CancellationException("parse cancelled") },
            resourceParser = { _, _, _ -> null },
        )
        assertFailsWith<CancellationException> { parserCancellation.resolve("adb", identity(), "de-DE") }
    }

    @Test
    fun `remote resource names are shell quoted as one inert argument`() {
        assertEquals(
            "'com.facebook.katana:string/(name removed)'",
            ProcessInstalledAppDeviceClient.quoteRemoteShellArg("com.facebook.katana:string/(name removed)"),
        )
        assertEquals("'a'\\''b'", ProcessInstalledAppDeviceClient.quoteRemoteShellArg("a'b"))
        assertEquals(
            "'cmd' 'overlay' 'lookup' '--user' '0' 'com.facebook.katana' " +
                "'com.facebook.katana:string/(name removed)'",
            ProcessInstalledAppDeviceClient.buildOverlayLookupCommand(
                0, "com.facebook.katana", "com.facebook.katana:string/(name removed)",
            ),
        )
        assertFailsWith<IllegalArgumentException> {
            ProcessInstalledAppDeviceClient.requireSafeBaseApkPath("/data/app/pkg/base.apk;rm")
        }
    }

    @Test
    fun `index publishes batches and transient work prevents completeness`() {
        val indexDevice = FakeDevice()
        val resolver = resolver(indexDevice)
        val service = DeviceAppDiscoveryService(labelResolver = resolver, labelParallelism = 1)
        val user = app("user.pkg", InstalledAppType.USER)
        val system = app("system.pkg", InstalledAppType.SYSTEM)
        val states = mutableListOf<AppLabelIndexState>()
        val complete = service.enrichLabels(
            "adb",
            DeviceAppDiscoverySnapshot(
                "serial-a", listOf(user, system), androidUserId = 0, localeTag = "de-DE",
            ),
            priorityPackages = { listOf("system.pkg") },
        ) { states += it.labelIndexState }
        assertEquals(AppLabelIndexState.COMPLETE, complete.labelIndexState)
        assertEquals(listOf(AppLabelIndexState.INDEXING, AppLabelIndexState.COMPLETE), states)
        assertTrue(indexDevice.readPaths.first().contains("system.pkg"))

        val retryService = DeviceAppDiscoveryService(
            labelResolver = resolver(FakeDevice(manifest = DeviceEntryResult(1, byteArrayOf(), "offline"))),
        )
        val retry = retryService.enrichLabels(
            "adb", DeviceAppDiscoverySnapshot("serial-a", listOf(user), androidUserId = 0, localeTag = "de-DE"),
        ) { }
        assertEquals(AppLabelIndexState.INCOMPLETE_RETRYABLE, retry.labelIndexState)
        assertEquals(AppLabelResolutionState.PENDING, retry.apps.single().labelResolutionState)
    }

    @Test
    fun `index overlaps exactly two resolutions and publishes only on caller thread`() {
        val entered = java.util.concurrent.CountDownLatch(2)
        val release = java.util.concurrent.CountDownLatch(1)
        val reads = java.util.concurrent.atomic.AtomicInteger()
        val active = java.util.concurrent.atomic.AtomicInteger()
        val peak = java.util.concurrent.atomic.AtomicInteger()
        val client = object : InstalledAppDeviceClient {
            override fun readApkEntry(adbPath: String, serial: String, baseApkPath: String, entryName: String): DeviceEntryResult {
                assertEquals("serial-a", serial)
                reads.incrementAndGet()
                peak.accumulateAndGet(active.incrementAndGet(), ::maxOf)
                try {
                    entered.countDown()
                    assertTrue(release.await(5, java.util.concurrent.TimeUnit.SECONDS))
                    return DeviceEntryResult(0, byteArrayOf(1), "")
                } finally { active.decrementAndGet() }
            }
            override fun overlayLookup(adbPath: String, serial: String, userId: Int, packageName: String, resourceName: String): DeviceEntryResult =
                error("Literal labels need no overlay")
        }
        val service = DeviceAppDiscoveryService(labelResolver = resolver(client,
            manifest = InstalledAppLabelResolver.ParsedManifest("Name", null)))
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
            val result = executor.submit<DeviceAppDiscoverySnapshot> {
                val caller = Thread.currentThread()
                service.enrichLabels("adb", DeviceAppDiscoverySnapshot("serial-a",
                    (1..12).map { app("app.p$it", InstalledAppType.USER) },
                    androidUserId = 0, localeTag = "de-DE")) {
                    assertEquals(caller, Thread.currentThread())
                }
            }
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
            assertEquals(2, reads.get())
            release.countDown()
            val complete = result.get(10, java.util.concurrent.TimeUnit.SECONDS)
            assertEquals(AppLabelIndexState.COMPLETE, complete.labelIndexState)
            assertEquals(12, reads.get())
            assertEquals(2, peak.get())
        } finally { release.countDown(); executor.shutdownNow() }
    }

    @Test
    fun `index cancellation interrupts both workers without publishing completion`() {
        val entered = java.util.concurrent.CountDownLatch(2)
        val stopped = java.util.concurrent.CountDownLatch(2)
        val client = object : InstalledAppDeviceClient {
            override fun readApkEntry(adbPath: String, serial: String, baseApkPath: String, entryName: String): DeviceEntryResult {
                entered.countDown()
                try { java.util.concurrent.CountDownLatch(1).await(); error("unreachable") }
                catch (ex: InterruptedException) { throw CancellationException() }
                finally { stopped.countDown() }
            }
            override fun overlayLookup(adbPath: String, serial: String, userId: Int, packageName: String, resourceName: String): DeviceEntryResult = error("unexpected")
        }
        val states = java.util.Collections.synchronizedList(mutableListOf<AppLabelIndexState>())
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
            val future = executor.submit {
                DeviceAppDiscoveryService(labelResolver = resolver(client)).enrichLabels("adb",
                    DeviceAppDiscoverySnapshot("serial-a", (1..4).map { app("app.p$it", InstalledAppType.USER) },
                        androidUserId = 0, localeTag = "de-DE")) { states += it.labelIndexState }
            }
            assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS))
            future.cancel(true)
            assertTrue(stopped.await(5, java.util.concurrent.TimeUnit.SECONDS))
            assertEquals(listOf(AppLabelIndexState.INDEXING), states.toList())
        } finally { executor.shutdownNow() }
    }

    @Test
    fun `index rejects mismatched device or user before dispatch`() {
        val device = FakeDevice()
        val original = app("app.pkg", InstalledAppType.USER)
        listOf(original.copy(installIdentity = identity().copy(deviceSerial = "other")),
            original.copy(installIdentity = identity().copy(androidUserId = 10))).forEach { candidate ->
            val result = DeviceAppDiscoveryService(labelResolver = resolver(device)).enrichLabels("adb",
                DeviceAppDiscoverySnapshot("serial-a", listOf(candidate), androidUserId = 0, localeTag = "de-DE")) {}
            assertEquals(AppLabelIndexState.INCOMPLETE_RETRYABLE, result.labelIndexState)
        }
        assertTrue(device.entries.isEmpty())
    }

    @Test
    fun `large transfers remain serial while parsing overlaps the next transfer`() {
        val firstTransfer = java.util.concurrent.CountDownLatch(1)
        val releaseTransfer = java.util.concurrent.CountDownLatch(1)
        val secondTransfer = java.util.concurrent.CountDownLatch(1)
        val resources = java.util.concurrent.atomic.AtomicInteger()
        val client = object : InstalledAppDeviceClient {
            override fun readApkEntry(adbPath: String, serial: String, baseApkPath: String, entryName: String): DeviceEntryResult {
                if (entryName != "resources.arsc") return DeviceEntryResult(0, byteArrayOf(), "")
                val order = resources.incrementAndGet()
                if (order == 1) {
                    firstTransfer.countDown()
                    assertTrue(releaseTransfer.await(5, java.util.concurrent.TimeUnit.SECONDS))
                } else secondTransfer.countDown()
                return DeviceEntryResult(0, byteArrayOf(order.toByte()), "")
            }
            override fun overlayLookup(adbPath: String, serial: String, userId: Int, packageName: String, resourceName: String) =
                DeviceEntryResult(0, "Name".toByteArray(), "")
        }
        val resolver = InstalledAppLabelResolver(InstalledAppLabelCache(tempFile()), client,
            manifestParser = { InstalledAppLabelResolver.ParsedManifest(null, 1) },
            resourceParser = { bytes, _, _ ->
                if (bytes[0].toInt() == 1) assertTrue(secondTransfer.await(5, java.util.concurrent.TimeUnit.SECONDS))
                InstalledAppLabelResolver.ParsedResource("app.pkg:string/name", "Name")
            })
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        try {
            val result = executor.submit<DeviceAppDiscoverySnapshot> {
                DeviceAppDiscoveryService(labelResolver = resolver).enrichLabels("adb",
                    DeviceAppDiscoverySnapshot("serial-a", (1..2).map { app("app.p$it", InstalledAppType.USER) },
                        androidUserId = 0, localeTag = "de-DE")) {}
            }
            assertTrue(firstTransfer.await(5, java.util.concurrent.TimeUnit.SECONDS))
            assertFalse(secondTransfer.await(100, java.util.concurrent.TimeUnit.MILLISECONDS))
            releaseTransfer.countDown()
            assertEquals(AppLabelIndexState.COMPLETE, result.get(10, java.util.concurrent.TimeUnit.SECONDS).labelIndexState)
            assertEquals(2, resources.get())
        } finally { releaseTransfer.countDown(); executor.shutdownNow() }
    }

    @Test
    fun `cold manifests use bounded batches and warm index performs no batch reads`() {
        val batches = mutableListOf<List<String>>()
        val client = object : InstalledAppDeviceClient {
            override fun supportsManifestBatch() = true
            override fun readManifestBatch(adbPath: String, serial: String, paths: List<String>): List<DeviceEntryResult> {
                assertEquals("serial-a", serial)
                batches += paths
                return paths.map { DeviceEntryResult(0, it.toByteArray(), "") }
            }
            override fun readApkEntry(adbPath: String, serial: String, baseApkPath: String, entryName: String): DeviceEntryResult = error("Unexpected single read")
            override fun overlayLookup(adbPath: String, serial: String, userId: Int, packageName: String, resourceName: String): DeviceEntryResult = error("Unexpected overlay")
        }
        val resolver = InstalledAppLabelResolver(InstalledAppLabelCache(tempFile()), client,
            manifestParser = { InstalledAppLabelResolver.ParsedManifest(String(it), null) })
        val service = DeviceAppDiscoveryService(labelResolver = resolver)
        val inventory = DeviceAppDiscoverySnapshot("serial-a",
            (1..17).map { app("app.p$it", InstalledAppType.USER) }, androidUserId = 0, localeTag = "de-DE")
        val first = service.enrichLabels("adb", inventory) {}
        assertEquals(AppLabelIndexState.COMPLETE, first.labelIndexState)
        assertEquals(listOf(8, 8, 1), batches.map { it.size })
        first.apps.forEach { assertEquals(it.installIdentity!!.baseApkPath, it.displayName) }
        val warm = service.enrichLabels("adb", inventory) {}
        assertEquals(AppLabelIndexState.COMPLETE, warm.labelIndexState)
        assertEquals(3, batches.size)
    }

    @Test
    fun `unsupported manifest batch capability uses direct worker reads without prefetch attempts`() {
        val batchAttempts = java.util.concurrent.atomic.AtomicInteger()
        val directReads = java.util.concurrent.atomic.AtomicInteger()
        val client = object : InstalledAppDeviceClient {
            override fun readManifestBatch(adbPath: String, serial: String, paths: List<String>): List<DeviceEntryResult>? {
                batchAttempts.incrementAndGet()
                return null
            }
            override fun readApkEntry(
                adbPath: String,
                serial: String,
                baseApkPath: String,
                entryName: String,
            ): DeviceEntryResult {
                directReads.incrementAndGet()
                return DeviceEntryResult(0, baseApkPath.toByteArray(), "")
            }
            override fun overlayLookup(
                adbPath: String,
                serial: String,
                userId: Int,
                packageName: String,
                resourceName: String,
            ): DeviceEntryResult = error("Unexpected overlay")
        }
        val resolver = InstalledAppLabelResolver(
            InstalledAppLabelCache(tempFile()),
            client,
            manifestParser = { InstalledAppLabelResolver.ParsedManifest(String(it), null) },
        )
        val inventory = DeviceAppDiscoverySnapshot(
            "serial-a",
            (1..17).map { app("app.p$it", InstalledAppType.USER) },
            androidUserId = 0,
            localeTag = "de-DE",
        )

        val result = DeviceAppDiscoveryService(labelResolver = resolver).enrichLabels("adb", inventory) {}

        assertEquals(AppLabelIndexState.COMPLETE, result.labelIndexState)
        assertEquals(0, batchAttempts.get())
        assertEquals(17, directReads.get())
    }

    private fun resolver(
        device: InstalledAppDeviceClient,
        file: File = tempFile(),
        manifest: InstalledAppLabelResolver.ParsedManifest = InstalledAppLabelResolver.ParsedManifest(null, 0x7f010001),
        resourceValue: String? = "ARSCLib value",
    ) = InstalledAppLabelResolver(
        cache = InstalledAppLabelCache(file),
        device = device,
        manifestParser = { manifest },
        resourceParser = { _, _, _ ->
            InstalledAppLabelResolver.ParsedResource("app.pkg:string/(name removed)", resourceValue)
        },
    )

    private fun identity() = InstalledAppIdentity(
        "serial-a", 0, "app.pkg", 7, "/data/app/app.pkg/base.apk", "now", 0x7f010001,
    )

    private fun app(packageName: String, type: InstalledAppType) = DiscoveredDeviceApp(
        packageName, packageName, 7, null, DevicePatchability.NO_PATCH_SOURCE,
        emptyList(), emptyList(), DeviceUpdateOwner.NotApplicable,
        DevicePatchSourceAvailability.NONE, type,
        AppLabelProvenance.PACKAGE_FALLBACK, AppLabelResolutionState.PENDING,
        identity().copy(packageName = packageName, baseApkPath = "/data/app/$packageName/base.apk"),
    )

    private fun tempFile(): File = createTempDirectory("morphe-label-test").resolve("labels.json").toFile()

    private fun manifestBytes(manifest: AndroidManifestBlock): ByteArray {
        val file = tempFile()
        manifest.refreshFull()
        manifest.writeBytes(file)
        return file.readBytes()
    }

    private class FakeDevice(
        private val manifest: DeviceEntryResult = DeviceEntryResult(0, byteArrayOf(1), ""),
        private val resources: DeviceEntryResult = DeviceEntryResult(0, byteArrayOf(2), ""),
        private val overlay: DeviceEntryResult = DeviceEntryResult(0, "Resolved\n".toByteArray(), ""),
    ) : InstalledAppDeviceClient {
        val entries = java.util.Collections.synchronizedList(mutableListOf<String>())
        val readPaths = java.util.Collections.synchronizedList(mutableListOf<String>())
        override fun readApkEntry(adbPath: String, serial: String, baseApkPath: String, entryName: String): DeviceEntryResult {
            entries += entryName
            readPaths += baseApkPath
            return if (entryName == "AndroidManifest.xml") manifest else resources
        }
        override fun overlayLookup(adbPath: String, serial: String, userId: Int, packageName: String, resourceName: String) = overlay
    }

}
