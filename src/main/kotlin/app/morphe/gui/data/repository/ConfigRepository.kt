/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.gui.data.repository

import app.morphe.engine.util.PortablePaths
import app.morphe.gui.data.model.AppConfig
import app.morphe.gui.data.model.DEFAULT_PATCH_SOURCE
import app.morphe.gui.data.model.PatchChannel
import app.morphe.gui.data.model.PatchSource
import app.morphe.gui.data.model.UpdateChannelPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import app.morphe.gui.ui.theme.ThemePreference
import app.morphe.gui.util.FileUtils
import app.morphe.gui.util.Logger

/**
 * Repository for managing app configuration (config.json)
 */
class ConfigRepository {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private var cachedConfig: AppConfig? = null

    /**
     * Load config from file, or return default if not exists.
     */
    suspend fun loadConfig(): AppConfig = withContext(Dispatchers.IO) {
        cachedConfig?.let { return@withContext it }

        // One-time migration from the legacy per-OS app-data path to the
        // unified morphe-data location. Runs once and is a no-op thereafter.
        ConfigMigration.runIfNeeded()

        val configFile = FileUtils.getConfigFile()

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
            val configFile = FileUtils.getConfigFile()
            val content = json.encodeToString(AppConfig.serializer(), config)
            configFile.writeText(content)
            cachedConfig = config
            Logger.info("Config saved to ${configFile.absolutePath}")
        } catch (e: Exception) {
            Logger.error("Failed to save config", e)
        }
    }

    /**
     * Update theme preference.
     */
    suspend fun setThemePreference(theme: ThemePreference) {
        val current = loadConfig()
        saveConfig(current.copy(themePreference = theme.name))
    }

    /**
     * Update patch channel preference.
     */
    suspend fun setPatchChannel(channel: PatchChannel) {
        val current = loadConfig()
        saveConfig(current.copy(preferredPatchChannel = channel.name))
    }

    /**
     * Update last used CLI version.
     */
    suspend fun setLastCliVersion(version: String) {
        val current = loadConfig()
        saveConfig(current.copy(lastCliVersion = version))
    }

    /**
     * LEGACY — kept so single-source callers don't break during the multi-source
     * transition. New code should use [setLastPatchesVersionForSource].
     */
    @Deprecated("Use setLastPatchesVersionForSource", ReplaceWith("setLastPatchesVersionForSource(sourceId, version)"))
    suspend fun setLastPatchesVersion(version: String) {
        val current = loadConfig()
        saveConfig(current.copy(lastPatchesVersion = version))
    }

    /**
     * Pin a specific release tag for [sourceId]. Used by PatchesScreen when the
     * user picks a version. Per-source = no cross-contamination across sources
     * with overlapping tag names.
     */
    suspend fun setLastPatchesVersionForSource(sourceId: String, version: String) {
        val current = loadConfig()
        val updated = current.lastPatchesVersionBySource + (sourceId to version)
        saveConfig(current.copy(lastPatchesVersionBySource = updated))
    }

    /**
     * Returns the per-source version pin map, with one-time migration from the
     * legacy [AppConfig.lastPatchesVersion] field: if the map is empty and the
     * legacy field is set, it's mapped to the default source.
     */
    suspend fun getLastPatchesVersionsBySource(): Map<String, String> {
        val current = loadConfig()
        if (current.lastPatchesVersionBySource.isNotEmpty()) {
            return current.lastPatchesVersionBySource
        }
        val legacy = current.lastPatchesVersion ?: return emptyMap()
        // Migrate: write the legacy pin onto the default source, return the new map.
        val migrated = mapOf(DEFAULT_PATCH_SOURCE.id to legacy)
        saveConfig(current.copy(lastPatchesVersionBySource = migrated))
        return migrated
    }

    /**
     * Mark the given CLI version as dismissed for the update banner. Pass null to
     * clear (so the banner reappears for whatever the next-available version is).
     */
    suspend fun setDismissedUpdateVersion(version: String?) {
        val current = loadConfig()
        saveConfig(current.copy(dismissedUpdateVersion = version))
    }

    /**
     * Persist the user's chosen update channel. Marks the choice as explicit
     * so subsequent reads respect it even if the running build's channel
     * differs. Also clears any prior [AppConfig.dismissedUpdateVersion] —
     * that dismissal referred to a specific version on the previous channel.
     */
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

    /**
     * Resolve the update-channel preference. When the user has explicitly
     * picked one (via Settings), respect that. Otherwise re-derive from the
     * running build's version on every call so swapping between a stable and
     * dev build flips the default automatically.
     */
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

    /**
     * Update default output directory.
     */
    suspend fun setDefaultOutputDirectory(path: String?) {
        val current = loadConfig()
        saveConfig(current.copy(defaultOutputDirectory = path?.let(PortablePaths::storableForm)))
    }

    /**
     * Update auto-cleanup temp files setting.
     */
    suspend fun setAutoCleanupTempFiles(enabled: Boolean) {
        val current = loadConfig()
        saveConfig(current.copy(autoCleanupTempFiles = enabled))
    }

    /**
     * Update simplified mode setting.
     */
    suspend fun setUseSimplifiedMode(enabled: Boolean) {
        val current = loadConfig()
        saveConfig(current.copy(useSimplifiedMode = enabled))
    }

    /**
     * Update the user's global keep-architectures list for strip libs.
     */
    suspend fun setKeepArchitectures(keep: Set<String>) {
        val current = loadConfig()
        saveConfig(current.copy(keepArchitectures = keep))
    }

    /**
     * Persist the expand/collapse state of a Settings dialog section so it restores
     * on next open instead of resetting to collapsed.
     */
    suspend fun setCollapsibleSectionExpanded(id: String, expanded: Boolean) {
        val current = loadConfig()
        val updated = current.collapsibleSectionStates + (id to expanded)
        saveConfig(current.copy(collapsibleSectionStates = updated))
    }

    /**
     * Update keystore path only (used for auto-remember on first creation).
     */
    suspend fun setKeystorePath(path: String?) {
        val current = loadConfig()
        saveConfig(current.copy(keystorePath = path?.let(PortablePaths::storableForm)))
    }

    /**
     * Update all keystore details at once.
     */
    suspend fun setKeystoreDetails(
        path: String?,
        password: String?,
        alias: String,
        entryPassword: String
    ) {
        val current = loadConfig()
        saveConfig(current.copy(
            keystorePath = path?.let(PortablePaths::storableForm),
            keystorePassword = password,
            keystoreAlias = alias,
            keystoreEntryPassword = entryPassword
        ))
    }

    /**
     * Get the currently active patch source.
     */
    suspend fun getActivePatchSource(): PatchSource {
        val config = loadConfig()
        return config.patchSource.find { it.id == config.activePatchSourceId }
            ?: DEFAULT_PATCH_SOURCE
    }

    /**
     * Set the active patch source by ID.
     */
    suspend fun setActivePatchSource(id: String) {
        val current = loadConfig()
        if (current.patchSource.any { it.id == id }) {
            saveConfig(current.copy(activePatchSourceId = id))
        }
    }

    /**
     * Add a new patch source.
     */
    suspend fun addPatchSource(source: PatchSource) {
        val current = loadConfig()
        val updated = current.copy(patchSource = current.patchSource + source)
        saveConfig(updated)
    }

    /**
     * Update an existing patch source. Cannot update non-deletable sources.
     */
    suspend fun updatePatchSource(updated: PatchSource) {
        val current = loadConfig()
        val existing = current.patchSource.find { it.id == updated.id }
        if (existing == null || !existing.deletable) return

        val updatedSources = current.patchSource.map { if (it.id == updated.id) updated else it }
        saveConfig(current.copy(patchSource = updatedSources))
    }

    /**
     * Update whether Morphe auto-starts the ADB daemon at GUI launch.
     */
    suspend fun setAutoStartAdb(enabled: Boolean) {
        val current = loadConfig()
        saveConfig(current.copy(autoStartAdb = enabled))
    }

    /**
     * Mark the multi-source upgrade hint as dismissed. One-shot — never resets.
     */
    suspend fun setMultiSourceHintDismissed() {
        val current = loadConfig()
        if (current.multiSourceHintDismissed) return
        saveConfig(current.copy(multiSourceHintDismissed = true))
    }

    /**
     * Toggle enablement of a patch source. Safety net: if disabling would leave zero
     * enabled sources, the default source is force-enabled (mirrors morphe-manager
     * SourceManagementSheet.kt:142-149 LaunchedEffect).
     */
    suspend fun setPatchSourceEnabled(id: String, enabled: Boolean) {
        val current = loadConfig()
        val updatedSources = current.patchSource.map {
            if (it.id == id) it.copy(enabled = enabled) else it
        }
        val anyEnabled = updatedSources.any { it.enabled }
        val finalSources = if (!anyEnabled) {
            // Safety net: force-enable the default
            updatedSources.map {
                if (it.id == DEFAULT_PATCH_SOURCE.id) it.copy(enabled = true) else it
            }
        } else {
            updatedSources
        }
        saveConfig(current.copy(patchSource = finalSources))
    }

    /**
     * Get the list of currently enabled patch sources (in config order).
     */
    suspend fun getEnabledPatchSources(): List<PatchSource> {
        return loadConfig().patchSource.filter { it.enabled }
    }

    /**
     * Remove a patch source by ID. Cannot remove non-deletable sources.
     */
    suspend fun removePatchSource(id: String) {
        val current = loadConfig()
        val source = current.patchSource.find { it.id == id }
        if (source == null || !source.deletable) return

        val updatedSources = current.patchSource.filter { it.id != id }
        // If we removed the active source, fall back to default
        val newActiveId = if (current.activePatchSourceId == id) {
            DEFAULT_PATCH_SOURCE.id
        } else {
            current.activePatchSourceId
        }
        saveConfig(current.copy(patchSource = updatedSources, activePatchSourceId = newActiveId))
    }

    /**
     * Clear cached config (for testing).
     */
    fun clearCache() {
        cachedConfig = null
    }
}
