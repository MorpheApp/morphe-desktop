/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.data.model

import app.morphe.gui.ui.theme.ThemePreference
import kotlinx.serialization.Serializable

/** Application configuration stored in config.json. */

val DEFAULT_PATCH_SOURCE = PatchSource(
    id = "morphe-default",
    name = "Morphe Patches",
    type = PatchSourceType.DEFAULT,
    url = "https://github.com/MorpheApp/morphe-patches",
    deletable = false
)

/** Release tracking mode for a patch source. */
@Serializable
enum class FollowMode { FOLLOW_STABLE, FOLLOW_DEV, PINNED }

/** Version tracking preference and optional pinned release tag for a patch source. */
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
    /** Legacy single-source version pin kept for config migration. */
    val lastPatchesVersion: String? = null,
    /** Legacy per-source version pin map kept for config migration. */
    val lastPatchesVersionBySource: Map<String, String> = emptyMap(),
    /** Release tracking preference per patch source ID. */
    val sourceVersionPrefs: Map<String, SourceVersionPref> = emptyMap(),
    val sourceChannelFlagsSeeded: Boolean = false,
    val cardFills: Map<String, MorpheFill> = emptyMap(),
    val globalCardFill: MorpheFill? = null,
    val useSharpCorners: Boolean = false,
    val groupPatchesByCategory: Boolean = true,
    val homeAppSortMode: String = "RECOMMENDED",
    val preferredPatchChannel: String = "STABLE",
    val useSimplifiedMode: Boolean = true,
    val patchSource: List<PatchSource> = listOf(DEFAULT_PATCH_SOURCE),
    val activePatchSourceId: String = "morphe-default",
    // Persisted expand/collapse state for settings sections.
    val collapsibleSectionStates: Map<String, Boolean> = emptyMap(),
    // Latest version for which update notification was dismissed.
    val dismissedUpdateVersion: String? = null,
    val updateChannelPreference: String? = null,
    // True if the update channel was explicitly chosen in settings.
    val userDidChooseUpdateChannel: Boolean = false,
    // Dismissal flag for multi-source introduction banner.
    val multiSourceHintDismissed: Boolean = false,
    // Last viewed apps filter tab.
    val homeAppListFilter: String = "ALL",
) {

    fun getUpdateChannelPreference(): UpdateChannelPreference? {
        val raw = updateChannelPreference ?: return null
        return try {
            UpdateChannelPreference.valueOf(raw)
        } catch (_: Exception) {
            null
        }
    }
    fun getThemePreference(): ThemePreference {
        if (themePreference == "PURE_BLACK") return ThemePreference.AMOLED
        return try {
            ThemePreference.valueOf(themePreference)
        } catch (_: Exception) {
            ThemePreference.SYSTEM
        }
    }
}

@Serializable
data class PatchSource (
    val id: String,
    val name: String,
    val type: PatchSourceType,
    val url: String? = null,
    val filePath: String? = null,
    val deletable: Boolean = true,
    val enabled: Boolean = true,
    val usePreRelease: Boolean = false,
    val useExperimentalVersions: Boolean = false,
)

@Serializable
enum class PatchSourceType{
    DEFAULT, GITHUB, GITLAB, LOCAL
}

/** Release channel preference for update notifications. */
enum class UpdateChannelPreference {
    STABLE,
    DEV,
    /** Disable update checking. */
    OFF,
}
