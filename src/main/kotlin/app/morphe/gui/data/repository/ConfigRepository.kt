/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.data.repository

import app.morphe.engine.MorpheData
import app.morphe.engine.config.mergeAndSaveJsonObject
import app.morphe.engine.util.Logger
import app.morphe.engine.util.isDevTag
import app.morphe.gui.data.model.AppConfig
import app.morphe.gui.data.model.DEFAULT_PATCH_SOURCE
import app.morphe.gui.data.model.FollowMode
import app.morphe.gui.data.model.SourceVersionPref
import app.morphe.gui.data.model.PatchSource
import app.morphe.gui.data.model.UpdateChannelPreference
import app.morphe.gui.data.model.MorpheFill
import app.morphe.gui.ui.theme.ThemePreference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/**
 * Repository for managing app configuration (config.json)
 */
open class ConfigRepository {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "#kind"
    }

    private var cachedConfig: AppConfig? = null

    /**
     * Load config from file, or return default if not exists.
     */
    open suspend fun loadConfig(): AppConfig = withContext(Dispatchers.IO) {
        cachedConfig?.let { return@withContext it }

        // One-time migration from the legacy per-OS app-data path to the
        // unified morphe-data location. Runs once and is a no-op thereafter.
        ConfigMigration.runIfNeeded()

        val configFile = MorpheData.configFile

        try {
            if (configFile.exists()) {
                val content = configFile.readText()
                val config = json.decodeFromString<AppConfig>(content)
                cachedConfig = config
                Logger.info("Config loaded from ${configFile.absolutePath}")
                config
            } else {
                Logger.info("No config file found, using defaults")
                val default = AppConfig()
                saveConfig(default)
                default
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.error("Failed to load config, using defaults", e)
            AppConfig()
        }
    }

    /**
     * Save config to file.
     */
    suspend fun saveConfig(config: AppConfig) = withContext(Dispatchers.IO) {
        try {
            val configFile = MorpheData.configFile
            val guiObj = json.encodeToJsonElement(AppConfig.serializer(), config).jsonObject
            mergeAndSaveJsonObject(configFile, json, guiObj)
            cachedConfig = config
            Logger.info("Config saved to ${configFile.absolutePath}")
        } catch (e: Exception) {
            Logger.error("Failed to save config", e)
        }
    }

    suspend fun setLanguage(language: String) {
        val current = loadConfig()
        saveConfig(current.copy(language = language))
    }

    suspend fun setThemePreference(theme: ThemePreference) {
        val current = loadConfig()
        saveConfig(current.copy(themePreference = theme.name))
    }

    suspend fun setUseSharpCorners(enabled: Boolean) {
        val current = loadConfig()
        saveConfig(current.copy(useSharpCorners = enabled))
    }

    suspend fun setHomeAppSortMode(mode: String) {
        val current = loadConfig()
        saveConfig(current.copy(homeAppSortMode = mode))
    }

    suspend fun setGlobalCardFill(fill: MorpheFill?) {
        val current = loadConfig()
        saveConfig(current.copy(globalCardFill = fill))
    }

    suspend fun clearCardFills() {
        val current = loadConfig()
        saveConfig(current.copy(cardFills = emptyMap()))
    }

    suspend fun setCardFill(packageName: String, fill: MorpheFill?) {
        val current = loadConfig()
        val updated = current.cardFills.toMutableMap().apply {
            if (fill == null) remove(packageName) else put(packageName, fill)
        }
        saveConfig(current.copy(cardFills = updated))
    }

    suspend fun setBackgroundType(type: String) {
        val current = loadConfig()
        saveConfig(current.copy(backgroundType = type))
    }

    suspend fun setEnableParallax(enabled: Boolean) {
        val current = loadConfig()
        saveConfig(current.copy(enableParallax = enabled))
    }
    
    suspend fun setCustomAccentColorArgb(color: Int?) {
        val current = loadConfig()
        saveConfig(current.copy(customAccentColorArgb = color))
    }

    suspend fun setGroupPatchesByCategory(enabled: Boolean) {
        val current = loadConfig()
        saveConfig(current.copy(groupPatchesByCategory = enabled))
    }

    /** Sets the version tracking preference for a patch source. */
    suspend fun setSourceVersionPref(sourceId: String, pref: SourceVersionPref) {
        val current = loadConfig()
        saveConfig(current.copy(sourceVersionPrefs = current.sourceVersionPrefs + (sourceId to pref)))
    }

    /** Returns per-source version preferences, migrating legacy fields on first read. */
    suspend fun getSourceVersionPrefs(): Map<String, SourceVersionPref> {
        val current = loadConfig()
        if (current.sourceVersionPrefs.isNotEmpty()) return current.sourceVersionPrefs

        val legacyTags: Map<String, String> = when {
            current.lastPatchesVersionBySource.isNotEmpty() -> current.lastPatchesVersionBySource
            current.lastPatchesVersion != null -> mapOf(DEFAULT_PATCH_SOURCE.id to current.lastPatchesVersion)
            else -> emptyMap()
        }
        if (legacyTags.isEmpty()) return emptyMap()

        val migrated = legacyTags.mapValues { (_, tag) ->
            val mode = if (tag.isDevTag()) FollowMode.FOLLOW_DEV else FollowMode.FOLLOW_STABLE
            SourceVersionPref(mode = mode)
        }
        saveConfig(current.copy(sourceVersionPrefs = migrated))
        return migrated
    }

    suspend fun migrateSourceChannelFlags() {
        val prefs = getSourceVersionPrefs()
        val config = loadConfig()
        if (config.sourceChannelFlagsSeeded) return

        val seeded = config.patchSource.map { source ->
            val wantsDev = prefs[source.id].followsPreRelease()
            if (source.usePreRelease == wantsDev) source else source.copy(usePreRelease = wantsDev)
        }
        saveConfig(config.copy(patchSource = seeded, sourceChannelFlagsSeeded = true))
        Logger.info("Seeded source pre-release flags: ${seeded.filter { it.usePreRelease }.map { it.name }}")
    }

    /** Records the update version dismissed by the user, or null to clear. */
    suspend fun setDismissedUpdateVersion(version: String?) {
        val current = loadConfig()
        saveConfig(current.copy(dismissedUpdateVersion = version))
    }

    /** Persists explicit update channel preference and clears prior dismissal. */
    suspend fun setUpdateChannelPreference(pref: UpdateChannelPreference) {
        val current = loadConfig()
        saveConfig(
            current.copy(
                updateChannelPreference = pref.name,
                userDidChooseUpdateChannel = true,
                dismissedUpdateVersion = null,
            )
        )
    }

    /** Returns active update channel preference, deriving from current build if unset. */
    suspend fun getOrInitUpdateChannelPreference(currentVersion: String): UpdateChannelPreference {
        val current = loadConfig()
        val saved = current.getUpdateChannelPreference()
        if (current.userDidChooseUpdateChannel && saved != null) {
            return saved
        }
        val derived = if (currentVersion.contains("dev")) {
            UpdateChannelPreference.DEV
        } else {
            UpdateChannelPreference.STABLE
        }
        if (saved != derived) {
            saveConfig(current.copy(updateChannelPreference = derived.name))
        }
        return derived
    }

    suspend fun setUseSimplifiedMode(enabled: Boolean) {
        val current = loadConfig()
        saveConfig(current.copy(useSimplifiedMode = enabled))
    }

    /** Persists the expanded state of a settings section. */
    suspend fun setCollapsibleSectionExpanded(id: String, expanded: Boolean) {
        val current = loadConfig()
        val updated = current.collapsibleSectionStates + (id to expanded)
        saveConfig(current.copy(collapsibleSectionStates = updated))
    }

    /** Returns the currently active patch source. */
    suspend fun getActivePatchSource(): PatchSource {
        val config = loadConfig()
        return config.patchSource.find { it.id == config.activePatchSourceId }
            ?: DEFAULT_PATCH_SOURCE
    }

    /** Sets the active patch source by ID. */
    suspend fun setActivePatchSource(id: String) {
        val current = loadConfig()
        if (current.patchSource.any { it.id == id }) {
            saveConfig(current.copy(activePatchSourceId = id))
        }
    }

    /** Adds a new patch source to the configuration. */
    suspend fun addPatchSource(source: PatchSource) {
        val current = loadConfig()
        val updated = current.copy(patchSource = current.patchSource + source)
        saveConfig(updated)
    }

    /** Updates an existing patch source. */
    suspend fun updatePatchSource(updated: PatchSource) {
        val current = loadConfig()
        val existing = current.patchSource.find { it.id == updated.id }
        if (existing == null) return

        val newSource = if (!existing.deletable) {
            existing.copy(
                usePreRelease = updated.usePreRelease,
                useExperimentalVersions = updated.useExperimentalVersions
            )
        } else {
            updated
        }

        val updatedSources = current.patchSource.map { if (it.id == updated.id) newSource else it }
        saveConfig(current.copy(patchSource = updatedSources))
    }

    /** Persists reordered patch sources if [orderedIds] matches current sources. */
    suspend fun reorderPatchSources(orderedIds: List<String>) {
        val current = loadConfig()
        val byId = current.patchSource.associateBy { it.id }
        if (orderedIds.size != current.patchSource.size || orderedIds.toSet() != byId.keys) return
        val reordered = orderedIds.map { byId.getValue(it) }
        saveConfig(current.copy(patchSource = reordered))
    }

    /** Marks multi-source introduction banner as dismissed. */
    suspend fun setMultiSourceHintDismissed() {
        val current = loadConfig()
        if (current.multiSourceHintDismissed) return
        saveConfig(current.copy(multiSourceHintDismissed = true))
    }

    /** Persists selected home apps filter tab. */
    suspend fun setHomeAppListFilter(value: String) {
        val current = loadConfig()
        if (current.homeAppListFilter == value) return
        saveConfig(current.copy(homeAppListFilter = value))
    }

    /** Toggles patch source enablement, ensuring at least one source remains active. */
    suspend fun setPatchSourceEnabled(id: String, enabled: Boolean) {
        val current = loadConfig()
        val updatedSources = current.patchSource.map {
            if (it.id == id) it.copy(enabled = enabled) else it
        }
        val anyEnabled = updatedSources.any { it.enabled }
        val finalSources = if (!anyEnabled) {
            updatedSources.map {
                if (it.id == DEFAULT_PATCH_SOURCE.id) it.copy(enabled = true) else it
            }
        } else {
            updatedSources
        }
        saveConfig(current.copy(patchSource = finalSources))
    }

    /** Removes a patch source by ID if deletable. */
    suspend fun removePatchSource(id: String) {
        val current = loadConfig()
        val source = current.patchSource.find { it.id == id }
        if (source == null || !source.deletable) return

        val updatedSources = current.patchSource.filter { it.id != id }
        val newActiveId = if (current.activePatchSourceId == id) {
            DEFAULT_PATCH_SOURCE.id
        } else {
            current.activePatchSourceId
        }
        saveConfig(current.copy(patchSource = updatedSources, activePatchSourceId = newActiveId))
    }
}

internal fun SourceVersionPref?.followsPreRelease(): Boolean = when (this?.mode) {
    FollowMode.FOLLOW_DEV -> true
    FollowMode.PINNED -> pinnedTag.isDevTag()
    else -> false
}
