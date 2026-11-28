/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.data.repository

import app.morphe.engine.SelfUpdater.AppInstallation
import app.morphe.engine.SelfUpdater.StagedUpdate
import app.morphe.engine.UpdateInfo
import app.morphe.engine.config.EngineConfigRepository
import io.ktor.client.HttpClient
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.io.TempDir

class AppUpdateManagerTest {

    private fun sampleInfo() = UpdateInfo(
        currentVersion = "2.4.0",
        latestVersion = "2.5.0",
        crossesDevToStable = false,
        downloadLink = "https://github.com/MorpheApp/morphe-desktop/releases/tag/v2.5.0",
    )

    private fun awaitState(
        manager: AppUpdateManager,
        predicate: (AppUpdateState) -> Boolean,
    ): AppUpdateState {
        val deadline = System.currentTimeMillis() + 5_000L
        while (System.currentTimeMillis() < deadline) {
            val current = manager.state.value
            if (predicate(current)) return current
            Thread.sleep(15L)
        }
        return manager.state.value
    }

    private fun writableInstall(jarFile: File) = AppInstallation.WritableJar(
        jarFile = jarFile,
        isSymlink = false,
        signerFingerprints = setOf("test-signer"),
    )

    @Test
    fun `update pauses in ReadyToRestart while patching and applies when triggered`(@TempDir tempDir: Path) {
        val oldJar = Files.createFile(tempDir.resolve("morphe-desktop-2.4.0-all.jar")).toFile()
        val stagedFile = Files.createFile(tempDir.resolve("morphe-desktop-2.5.0-all.jar")).toFile()
        val destJar = File(tempDir.toFile(), "morphe-desktop-2.5.0-all.jar")
        var applyCalled = 0
        var exitCalled = 0

        val manager = AppUpdateManager(
            httpClient = HttpClient(),
            engineConfigRepository = EngineConfigRepository(tempDir.resolve("config.json").toFile()),
            installation = writableInstall(oldJar),
            downloadRunner = { _, onProgress ->
                onProgress(500L, 1_000L)
                stagedFile
            },
            verifier = { file, info, install ->
                StagedUpdate(file, install.jarFile, destJar, "abc123", info.latestVersion)
            },
            applier = { _, _, _ ->
                applyCalled++
            },
            mainDispatcher = Dispatchers.Unconfined,
            exitFallback = {},
        )

        manager.startUpdate(sampleInfo(), isPatchingProvider = { true }, onExitApplication = { exitCalled++ })
        val ready = assertIs<AppUpdateState.ReadyToRestart>(
            awaitState(manager) { it is AppUpdateState.ReadyToRestart || it is AppUpdateState.Failed }
        )
        assertEquals("2.5.0", ready.info.latestVersion)
        assertEquals(0, applyCalled)

        manager.applyStagedUpdate { exitCalled++ }
        awaitState(manager) { exitCalled == 1 }
        assertEquals(1, applyCalled)
        assertEquals(1, exitCalled)
    }

    @Test
    fun `cancelling download cleans staged file and resets to Idle`(@TempDir tempDir: Path) {
        val oldJar = Files.createFile(tempDir.resolve("morphe-desktop-2.4.0-all.jar")).toFile()
        val stagedFile = Files.createFile(tempDir.resolve("staged.jar")).toFile()
        val gate = CompletableDeferred<Unit>()

        val manager = AppUpdateManager(
            httpClient = HttpClient(),
            engineConfigRepository = EngineConfigRepository(tempDir.resolve("config.json").toFile()),
            installation = writableInstall(oldJar),
            downloadRunner = { _, onProgress ->
                onProgress(100L, 1_000L)
                gate.await()
                stagedFile
            },
            mainDispatcher = Dispatchers.Unconfined,
            exitFallback = {},
        )

        manager.startUpdate(sampleInfo(), isPatchingProvider = { false }, onExitApplication = {})
        awaitState(manager) { it is AppUpdateState.Downloading }

        manager.cancelDownload()
        val finalState = awaitState(manager) { it is AppUpdateState.Idle }
        assertIs<AppUpdateState.Idle>(finalState)
    }

    @Test
    fun `download failure enters Failed with null staged and apply failure preserves staged for retry`(@TempDir tempDir: Path) {
        val oldJar = Files.createFile(tempDir.resolve("morphe-desktop-2.4.0-all.jar")).toFile()
        val stagedFile = Files.createFile(tempDir.resolve("staged.jar")).toFile()
        val destJar = File(tempDir.toFile(), "morphe-desktop-2.5.0-all.jar")

        // Download failure
        val failDownloadManager = AppUpdateManager(
            httpClient = HttpClient(),
            engineConfigRepository = EngineConfigRepository(tempDir.resolve("config1.json").toFile()),
            installation = writableInstall(oldJar),
            downloadRunner = { _, _ -> throw IOException("Network unreachable") },
            mainDispatcher = Dispatchers.Unconfined,
            exitFallback = {},
        )
        failDownloadManager.startUpdate(sampleInfo(), isPatchingProvider = { false }, onExitApplication = {})
        val downloadFailed = assertIs<AppUpdateState.Failed>(
            awaitState(failDownloadManager) { it is AppUpdateState.Failed }
        )
        assertNull(downloadFailed.staged)
        assertTrue(downloadFailed.message.contains("Network unreachable"))

        failDownloadManager.clearError()
        assertIs<AppUpdateState.Idle>(failDownloadManager.state.value)

        // Apply failure retains staged update so retry does not re-download
        var downloadCalls = 0
        var shouldFailApply = true
        var exitCalled = 0
        val failApplyManager = AppUpdateManager(
            httpClient = HttpClient(),
            engineConfigRepository = EngineConfigRepository(tempDir.resolve("config2.json").toFile()),
            installation = writableInstall(oldJar),
            downloadRunner = { _, _ ->
                downloadCalls++
                stagedFile
            },
            verifier = { file, info, install ->
                StagedUpdate(file, install.jarFile, destJar, "abc123", info.latestVersion)
            },
            applier = { _, _, _ ->
                if (shouldFailApply) throw IOException("Spawn failed")
            },
            mainDispatcher = Dispatchers.Unconfined,
            exitFallback = {},
        )

        failApplyManager.startUpdate(sampleInfo(), isPatchingProvider = { false }, onExitApplication = { exitCalled++ })
        val applyFailed = assertIs<AppUpdateState.Failed>(
            awaitState(failApplyManager) { it is AppUpdateState.Failed }
        )
        assertNotNull(applyFailed.staged)
        assertEquals(1, downloadCalls)
        assertEquals(0, exitCalled)

        shouldFailApply = false
        failApplyManager.applyStagedUpdate { exitCalled++ }
        awaitState(failApplyManager) { exitCalled == 1 }
        assertEquals(1, downloadCalls)
        assertEquals(1, exitCalled)
    }
}
