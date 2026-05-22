/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.gui.data.model

import app.morphe.engine.PatchEngine.Config.Companion.DEFAULT_KEYSTORE_ALIAS
import app.morphe.engine.PatchEngine.Config.Companion.DEFAULT_KEYSTORE_PASSWORD
import app.morphe.engine.util.PortablePaths
import app.morphe.gui.ui.theme.ThemePreference
import app.morphe.gui.util.FileUtils.ANDROID_ARCHITECTURES
import kotlinx.serialization.Serializable
import java.io.File

/**
 * Application configuration stored in config.json
 */

val DEFAULT_PATCH_SOURCE = PatchSource(
    id = "morphe-default",
    name = "Morphe Patches",
    type = PatchSourceType.DEFAULT,
    url = "https://github.com/MorpheApp/morphe-patches",
    deletable = false
)

@Serializable
data class AppConfig(
    val themePreference: String = ThemePreference.SYSTEM.name,
    val lastCliVersion: String? = null,
    /**
     * LEGACY single-source version pin. Kept only for one-version migration into
     * [lastPatchesVersionBySource] — read it on first load if the map is empty,
     * then phase out. Do not read this directly anywhere new — go through
     * [ConfigRepository.getLastPatchesVersionsBySource].
     */
    val lastPatchesVersion: String? = null,
    /**
     * Per-source version pin: sourceId → release tag. Absence of a key means
     * "no pin — use that source's latest stable". Replaces the legacy single
     * [lastPatchesVersion] which silently contaminated other sources whose tag
     * names happened to overlap.
     */
    val lastPatchesVersionBySource: Map<String, String> = emptyMap(),
    val preferredPatchChannel: String = PatchChannel.STABLE.name,
    val defaultOutputDirectory: String? = null,
    val autoCleanupTempFiles: Boolean = true,  // Default ON
    val useSimplifiedMode: Boolean = true, // Default to Quick/Simplified mode
    val patchSource: List<PatchSource> = listOf(DEFAULT_PATCH_SOURCE),
    val activePatchSourceId: String = "morphe-default",
    val keystorePath: String? = null,
    val keystorePassword: String? = null,
    val keystoreAlias: String = DEFAULT_KEYSTORE_ALIAS,
    val keystoreEntryPassword: String = DEFAULT_KEYSTORE_PASSWORD,
    // User's global keep-list for strip libs. Defaults to all common modern arches
    // (equivalent to no stripping). Stripping is only applied when the APK contains
    // an arch NOT in this set. See PatchSelectionViewModel.computeStripLibsStatus.
    val keepArchitectures: Set<String> = ANDROID_ARCHITECTURES,
    // Persisted expand/collapse state for each section in the Settings dialog.
    // Keyed by section title (e.g. "STRIP LIBS"). Missing key = section starts collapsed.
    val collapsibleSectionStates: Map<String, Boolean> = emptyMap(),
    // Latest CLI version the user dismissed the update banner for. The banner stays
    // hidden while the available update equals this; reappears when a newer version drops.
    val dismissedUpdateVersion: String? = null,
    // Which release channel the user wants update checks to follow. Null = not yet set;
    // resolved at first read to STABLE/DEV based on the running build's version (so an
    // existing dev user upgrading isn't silently flipped to stable).
    val updateChannelPreference: String? = null,
    // Whether the user explicitly picked the update channel via Settings. When false,
    // the channel is re-derived from the running build's version on each read so a
    // user who swaps from a stable build to a dev build sees the right default.
    // Once they pick one in Settings, this flips to true and we respect their choice.
    val userDidChooseUpdateChannel: Boolean = false,
    // One-shot dismissal flag for the "multiple sources are now active" hint shown
    // after upgrading to multi-source builds. Flips to true once the user dismisses
    // the banner, never resets.
    val multiSourceHintDismissed: Boolean = false,
    // Whether Morphe should auto-start the ADB daemon at GUI launch to monitor
    // connected devices. Default OFF — many users never push patched APKs to a
    // device, so spawning a long-lived adb server unprompted is unwanted noise.
    // When ON, DeviceMonitor polls devices; if Morphe was the one that started
    // the daemon, it's killed on toggle-OFF and on window close.
    val autoStartAdb: Boolean = false,
) {

    fun getUpdateChannelPreference(): UpdateChannelPreference? {
        val raw = updateChannelPreference ?: return null
        return try {
            UpdateChannelPreference.valueOf(raw)
        } catch (e: Exception) {
            null
        }
    }
    fun getThemePreference(): ThemePreference {
        return try {
            ThemePreference.valueOf(themePreference)
        } catch (e: Exception) {
            ThemePreference.SYSTEM
        }
    }

    fun getPatchChannel(): PatchChannel {
        return try {
            PatchChannel.valueOf(preferredPatchChannel)
        } catch (e: Exception) {
            PatchChannel.STABLE
        }
    }

    /**
     * Resolved live [File] for [defaultOutputDirectory]. Goes through
     * [PortablePaths.resolve] so a stored relative value is anchored to the
     * bundle, not the JVM's CWD. Use this instead of `File(...)` at call sites.
     */
    fun resolvedDefaultOutputDirectory(): File? =
        defaultOutputDirectory?.let(PortablePaths::resolve)

    /**
     * Resolved live [File] for [keystorePath]. See [resolvedDefaultOutputDirectory].
     */
    fun resolvedKeystorePath(): File? =
        keystorePath?.let(PortablePaths::resolve)
}

@Serializable
data class PatchSource (
    val id: String,
    val name: String,
    val type: PatchSourceType,
    // For DEFAULT (morphe), GITHUB and GITLAB sources: the canonical
    // "https://{host}/{owner}/{repo}" URL.
    val url: String? = null,
    val filePath: String? = null, // For local files
    val deletable: Boolean = true,
    // Multi-source enablement. Default true so old configs migrate to "all enabled"
    // on first load (per user choice — see project memory).
    val enabled: Boolean = true,
)

@Serializable
enum class PatchSourceType{
    DEFAULT, GITHUB, GITLAB, LOCAL
}

enum class PatchChannel {
    STABLE,
    DEV
}

/**
 * Tracks which CLI release channel the user wants update notifications for.
 * No `AUTO` value — the smart default is computed once at first launch based
 * on the running build's version, then persisted as a concrete choice.
 */
enum class UpdateChannelPreference {
    /** Probe the `main` branch — only stable releases trigger the banner. */
    STABLE,
    /** Probe the `dev` branch — both newer dev and newer stable releases trigger the banner. */
    DEV,
    /** No update check, no banner. Re-enable from Settings. */
    OFF,
}
