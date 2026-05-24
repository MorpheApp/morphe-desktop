/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.gui.data.repository

import app.morphe.engine.patches.PatchProvider
import app.morphe.engine.patches.RemotePatchSourceFactory
import app.morphe.gui.data.model.PatchSource
import app.morphe.gui.data.model.PatchSourceType
import app.morphe.gui.util.Logger
import io.ktor.client.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Which top-level UI mode is currently visible. Used by [PatchSourceManager]
 * to gate per-VM patch loading so only the visible mode's VM does the work.
 */
enum class ActiveMode { QUICK, EXPERT }

/**
 * Manages PatchRepository instances for different patch sources.
 * Creates and caches a PatchRepository per GitHub-based source.
 * Emits [sourceVersion] whenever the active source changes so the UI can react.
 */
class PatchSourceManager(
    private val httpClient: HttpClient,
    private val configRepository: ConfigRepository
) {
    private val repositories = mutableMapOf<String, PatchRepository>()

    // Cached active state for synchronous access
    private var cachedActiveRepo: PatchRepository? = null
    private var cachedActiveSource: PatchSource? = null

    // Snapshot of currently-enabled sources for sync access. Updated on initialize()
    // and whenever setSourceEnabled / addSource / removeSource fires.
    private var cachedEnabledSources: List<PatchSource> = emptyList()

    // Incremented on every source switch / enable change so Compose can key on it
    private val _sourceVersion = MutableStateFlow(0)
    val sourceVersion: StateFlow<Int> = _sourceVersion.asStateFlow()

    // Observable list of enabled sources for UI
    private val _enabledSources = MutableStateFlow<List<PatchSource>>(emptyList())
    val enabledSources: StateFlow<List<PatchSource>> = _enabledSources.asStateFlow()

    // Observable list of ALL sources (enabled + disabled) — drives the
    // SourceManagementSheet which needs to render every source with a toggle.
    private val _allSources = MutableStateFlow<List<PatchSource>>(emptyList())
    val allSources: StateFlow<List<PatchSource>> = _allSources.asStateFlow()

    /**
     * Which mode's ViewModel is currently driving the UI. Used by both
     * [HomeViewModel] (EXPERT) and [QuickPatchViewModel] (QUICK) to skip
     * patch-loading when they're not visible — both VMs can be alive
     * simultaneously (QuickVM is `remember`-scoped to App.kt; HomeVM is
     * created by Voyager when the Navigator branch composes), and without
     * this gate they'd race to download the same sources twice on every
     * cache clear / source toggle.
     */
    private val _activeMode = MutableStateFlow(ActiveMode.QUICK)
    val activeMode: StateFlow<ActiveMode> = _activeMode.asStateFlow()

    fun setActiveMode(mode: ActiveMode) {
        if (_activeMode.value != mode) {
            Logger.info("PatchSourceManager: active mode → $mode")
            _activeMode.value = mode
        }
    }

    /**
     * Load the active source from config and cache its PatchRepository.
     * Call once at app startup (from a LaunchedEffect).
     */
    suspend fun initialize() {
        val source = configRepository.getActivePatchSource()
        cachedActiveSource = source
        cachedActiveRepo = getRepositoryForSource(source)
        refreshEnabledSources()
        Logger.info("PatchSourceManager initialized with source '${source.name}' (type=${source.type})")
        Logger.info("Enabled sources: ${cachedEnabledSources.joinToString { it.name }}")
    }

    /**
     * Switch the active source, persist it, and signal the UI.
     */
    suspend fun switchSource(id: String) {
        configRepository.setActivePatchSource(id)
        val source = configRepository.getActivePatchSource()
        cachedActiveSource = source
        cachedActiveRepo = getRepositoryForSource(source)
        _sourceVersion.value++
        Logger.info("Switched active patch source to '${source.name}' (type=${source.type})")
    }

    /**
     * Whether the current active source is a local .mpp file.
     */
    fun isLocalSource(): Boolean {
        return cachedActiveSource?.type == PatchSourceType.LOCAL
    }

    /**
     * Get the local .mpp file path if the active source is LOCAL, null otherwise.
     */
    fun getLocalFilePath(): String? {
        val source = cachedActiveSource ?: return null
        return if (source.type == PatchSourceType.LOCAL) source.filePath else null
    }

    /**
     * Get the display name of the active source.
     */
    fun getActiveSourceName(): String {
        return cachedActiveSource?.name ?: "Morphe Patches"
    }

    /**
     * Whether the active source is the built-in Morphe default.
     */
    fun isDefaultSource(): Boolean {
        return cachedActiveSource?.type == PatchSourceType.DEFAULT
    }

    /**
     * Get the cached active PatchRepository synchronously.
     * Returns null for LOCAL sources (no GitHub API needed).
     * Falls back to default repo if not yet initialized and source is not LOCAL.
     */
    fun getActiveRepositorySync(): PatchRepository {
        return cachedActiveRepo ?: defaultMorpheRepository().also {
            if (!isLocalSource()) cachedActiveRepo = it
        }
    }

    /**
     * Build the fallback PatchRepository pointed at the built-in Morphe
     * repo (`MorpheApp/morphe-patches` on GitHub). Used when the active
     * source isn't yet known.
     */
    private fun defaultMorpheRepository(): PatchRepository {
        val remote = RemotePatchSourceFactory.build(
            PatchProvider.GITHUB,
            "MorpheApp/morphe-patches",
            httpClient,
        )
        return PatchRepository(remote)
    }

    /**
     * Get the PatchRepository for the currently active source (suspend version).
     * For LOCAL sources, returns null (caller should use the file path directly).
     */
    suspend fun getActiveRepository(): PatchRepository? {
        val source = configRepository.getActivePatchSource()
        return getRepositoryForSource(source)
    }

    /**
     * Get the PatchRepository for a specific source.
     * Returns null for LOCAL sources (no remote API needed).
     */
    fun getRepositoryForSource(source: PatchSource): PatchRepository? {
        if (source.type == PatchSourceType.LOCAL) return null

        return repositories.getOrPut(source.id) {
            val repoPath = extractRepoPath(source)
            // Map the GUI's persisted source type to the engine's provider
            // enum. DEFAULT inherits GitHub (Morphe Patches lives there).
            val provider = when (source.type) {
                PatchSourceType.GITLAB -> PatchProvider.GITLAB
                else -> PatchProvider.GITHUB
            }
            Logger.info("Creating PatchRepository for source '${source.name}' (repo=$repoPath, provider=$provider)")
            val remote = RemotePatchSourceFactory.build(provider, repoPath, httpClient)
            PatchRepository(remote)
        }
    }

    /**
     * Get the active patch source config.
     */
    suspend fun getActiveSource(): PatchSource {
        return configRepository.getActivePatchSource()
    }

    /**
     * Extract "owner/repo" from a PatchSource's URL. Works for both GitHub
     * and GitLab hosts. Falls back to the built-in default repo when no URL
     * is configured (e.g. for the DEFAULT source on first launch).
     */
    private fun extractRepoPath(source: PatchSource): String {
        val url = source.url ?: return "MorpheApp/morphe-patches"
        return url
            .removePrefix("https://github.com/")
            .removePrefix("http://github.com/")
            .removePrefix("https://gitlab.com/")
            .removePrefix("http://gitlab.com/")
            .removeSuffix("/")
            .removeSuffix(".git")
    }

    /**
     * Clear all cached repository instances (e.g. after source list changes).
     */
    fun clearAll() {
        repositories.clear()
    }

    /**
     * Notify that cached patch files were deleted (e.g. via "Clear Cache" in settings).
     * Clears cached repo state and bumps [sourceVersion] so ViewModels reload.
     */
    fun notifyCacheCleared() {
        cachedActiveRepo?.clearCache()
        _sourceVersion.value++
    }

    // ── Multi-source API ──────────────────────────────────────────────────────

    /**
     * Snapshot of currently-enabled sources, in config order. Synchronous.
     */
    fun getEnabledSourcesSync(): List<PatchSource> = cachedEnabledSources

    /**
     * Pair each enabled source with its [PatchRepository]. The repo is null for LOCAL
     * sources — callers should use [PatchSource.filePath] directly in that case.
     */
    fun getEnabledRepositories(): List<Pair<PatchSource, PatchRepository?>> =
        cachedEnabledSources.map { it to getRepositoryForSource(it) }

    /**
     * Toggle enablement of a source. Persists, refreshes the cached snapshot, and
     * bumps [sourceVersion] so consumers reload. Default-source safety net is
     * applied at the [ConfigRepository] layer.
     */
    suspend fun setSourceEnabled(id: String, enabled: Boolean) {
        configRepository.setPatchSourceEnabled(id, enabled)
        refreshEnabledSources()
        _sourceVersion.value++
        Logger.info("Source '$id' enabled=$enabled. Enabled now: ${cachedEnabledSources.joinToString { it.name }}")
    }

    /**
     * Add a new source. Persists and refreshes the cached snapshot.
     */
    suspend fun addSource(source: PatchSource) {
        configRepository.addPatchSource(source)
        refreshEnabledSources()
        _sourceVersion.value++
    }

    /**
     * Remove a source by id. Refuses non-deletable (default) sources. Drops the
     * cached repo for that id so a re-add doesn't reuse stale state.
     */
    suspend fun removeSource(id: String) {
        configRepository.removePatchSource(id)
        repositories.remove(id)
        refreshEnabledSources()
        _sourceVersion.value++
    }

    /**
     * Update an existing source (e.g. rename). Refuses non-deletable sources.
     */
    suspend fun updateSource(updated: PatchSource) {
        configRepository.updatePatchSource(updated)
        // Drop the cached repo so the new url/name is picked up on next access.
        repositories.remove(updated.id)
        refreshEnabledSources()
        _sourceVersion.value++
    }

    private suspend fun refreshEnabledSources() {
        val all = configRepository.loadConfig().patchSource
        val enabled = all.filter { it.enabled }
        cachedEnabledSources = enabled
        _enabledSources.value = enabled
        _allSources.value = all
    }
}
