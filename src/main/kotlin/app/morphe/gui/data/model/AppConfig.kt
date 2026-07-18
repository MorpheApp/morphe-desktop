/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
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

/**
 * How a patch source decides which release to load.
 *
 * - [FOLLOW_STABLE]: ride the newest **stable** (non-pre-release) — auto-updates
 *   as new stables ship. The default for an untouched source.
 * - [FOLLOW_DEV]: ride the newest release **overall**, pre-releases included
 *   ("bleeding edge"). When a dev is newest you get the dev; when a stable is the
 *   newest thing out, you get that stable — without losing the dev track.
 * - [PINNED]: stay frozen on one exact tag (chosen deliberately), ignoring newer
 *   releases. The version lives in [SourceVersionPref.pinnedTag].
 */
@Serializable
enum class FollowMode { FOLLOW_STABLE, FOLLOW_DEV, PINNED }

/**
 * A source's version preference: which release-tracking [mode], plus the exact
 * tag when [mode] is [FollowMode.PINNED] (null otherwise).
 */
@Serializable
data class SourceVersionPref(
    val mode: FollowMode,
    val pinnedTag: String? = null,
)

@Serializable
data class AppConfig(
    val themePreference: String = ThemePreference.SYSTEM.name,
    val lastCliVersion: String? = null,
    /**
     * LEGACY single-source version pin. Kept only so it can be migrated (via
     * [lastPatchesVersionBySource]) into [sourceVersionPrefs]. Do not read directly
     * anywhere new — go through [ConfigRepository.getSourceVersionPrefs].
     */
    val lastPatchesVersion: String? = null,
    /**
     * LEGACY per-source version pin: sourceId → release tag. Superseded by
     * [sourceVersionPrefs]; kept only so existing configs can migrate (every old
     * tag becomes a follow-track based on whether it was a dev tag). Do not read
     * directly — go through [ConfigRepository.getSourceVersionPrefs].
     */
    val lastPatchesVersionBySource: Map<String, String> = emptyMap(),
    /**
     * Per-source version preference: sourceId → [SourceVersionPref].
     *
     * Absence of a key = follow the source's latest stable (the default for a
     * brand-new, untouched source). Otherwise the stored [SourceVersionPref]
     * decides whether the source rides the latest stable, the latest overall
     * (dev/bleeding-edge), or stays frozen on a specific tag. See
     * [ConfigRepository.getSourceVersionPrefs] / [setSourceVersionPref].
     */
    val sourceVersionPrefs: Map<String, SourceVersionPref> = emptyMap(),
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
    // Patch-developer options. Enables a suite of developers options that patch developers
    // can use.
    val developerOptions: Boolean = false,
    // Last folder browsed when picking a local .mpp, so the file picker reopens there
    // instead of a system default. Set after a successful pick.
    val lastLocalPatchDir: String? = null,
    // Which home apps tab the user last viewed ("ALL" or "YOURS"), restored on
    // next launch. Stored as a string so this data layer stays free of UI enums.
    val homeAppListFilter: String = "ALL",
    // After an ADB install, automatically route the patched app's web links to it
    // ("open with"). Default OFF — it changes how the device opens links, so it's
    // opt-in. See AppLinkCommands / AdbManager.setLinkHandling.
    val autoRouteLinksAfterInstall: Boolean = false,
    // When auto-routing links, also stop the stock app from opening those links
    // (only applies when a rename patch was used and stock is installed). Default
    // OFF — it reaches into a stock app's behavior.
    val disableStockLinksAfterInstall: Boolean = false,
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
