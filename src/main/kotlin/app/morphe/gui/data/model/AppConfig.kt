/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.data.model

import app.morphe.gui.ui.theme.ThemePreference
import kotlinx.serialization.Serializable

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
 * - [PINNED]: stay frozen on one exact tag (chosen deliberately), ignoring newer
 *   releases. The version lives in [SourceVersionPref.pinnedTag].
 * - [FOLLOW_STABLE] / [FOLLOW_DEV]: not pinned, so the channel comes from
 *   [PatchSource.usePreRelease] instead. These two are still written, and they seed
 *   that flag once (see [ConfigRepository.migrateSourceChannelFlags]), but they no
 *   longer decide which release resolves. Read the flag, not the mode.
 *
 * A source that is not pinned resolves through [app.morphe.gui.util.newerRelease],
 * which is why following dev never strands anyone on a stale pre-release.
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
    val language: String = "system",
    val themePreference: String = ThemePreference.SYSTEM.name,
    val backgroundType: String = "CIRCLES",
    val enableParallax: Boolean = true,
    val customAccentColorArgb: Int? = null,
    val lastCliVersion: String? = null,
    /**
     * LEGACY single-source version pin. Kept only so it can be migrated (via
     * [lastPatchesVersionBySource]) into [sourceVersionPrefs]. Do not read directly
     * anywhere new. Go through [ConfigRepository.getSourceVersionPrefs].
     */
    val lastPatchesVersion: String? = null,
    /**
     * LEGACY per-source version pin: sourceId → release tag. Superseded by
     * [sourceVersionPrefs]. Kept only so existing configs can migrate (every old
     * tag becomes a follow-track based on whether it was a dev tag). Do not read
     * directly. Go through [ConfigRepository.getSourceVersionPrefs].
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
    val sourceChannelFlagsSeeded: Boolean = false,
    val cardFills: Map<String, MorpheFill> = emptyMap(),
    val globalCardFill: MorpheFill? = null,
    val useSharpCorners: Boolean = false,
    val groupPatchesByCategory: Boolean = true,
    val homeAppSortMode: String = "RECOMMENDED",
    val preferredPatchChannel: String = PatchChannel.STABLE.name,
    val useSimplifiedMode: Boolean = true, // Default to Quick/Simplified mode
    val patchSource: List<PatchSource> = listOf(DEFAULT_PATCH_SOURCE),
    val activePatchSourceId: String = "morphe-default",
    // Persisted expand/collapse state for each section in the Settings dialog.
    // Keyed by section title (e.g. "STRIP LIBS"). Missing key = section starts collapsed.
    val collapsibleSectionStates: Map<String, Boolean> = emptyMap(),
    // Latest CLI version the user dismissed the update banner for. The banner stays
    // hidden while the available update equals this. Reappears when a newer version drops.
    val dismissedUpdateVersion: String? = null,
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
    // Which home apps tab the user last viewed ("ALL" or "YOURS"), restored on
    // next launch. Stored as a string so this data layer stays free of UI enums.
    val homeAppListFilter: String = "ALL",
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
        if (themePreference == "PURE_BLACK") return ThemePreference.AMOLED
        return try {
            ThemePreference.valueOf(themePreference)
        } catch (e: Exception) {
            ThemePreference.SYSTEM
        }
    }
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
    // on first load (per user choice, see project memory).
    val enabled: Boolean = true,
    val usePreRelease: Boolean = false,
    val useExperimentalVersions: Boolean = false,
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
 * No `AUTO` value. The smart default is computed once at first launch based
 * on the running build's version, then persisted as a concrete choice.
 */
enum class UpdateChannelPreference {
    STABLE,
    DEV,
    /** No update check, no banner. Re-enable from Settings. */
    OFF,
}
