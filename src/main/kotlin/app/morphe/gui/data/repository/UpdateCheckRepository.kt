/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.data.repository

import app.morphe.engine.AppUpdateCheck
import app.morphe.engine.ReleaseChannel
import app.morphe.engine.UpdateChecker
import app.morphe.engine.UpdateInfo
import app.morphe.engine.util.Logger
import app.morphe.gui.data.model.UpdateChannelPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Singleton wrapper around [UpdateChecker] so the network probe runs at most
 * once per (app session, channel) combination. Both home view models and the
 * About dialog share the cached result. Settings should call [clearCache] after
 * the user changes the update channel preference so the next read re-probes.
 */
class UpdateCheckRepository(
    private val configRepository: ConfigRepository,
) {

    private val mutex = Mutex()
    private var cachedCheck: AppUpdateCheck? = null

    /**
     * Checks for the latest update per the user's channel preference. Returns
     * null when the channel is OFF; otherwise returns [AppUpdateCheck.Available],
     * [AppUpdateCheck.UpToDate], or [AppUpdateCheck.Failed]. Successful checks
     * are cached until [clearCache].
     */
    suspend fun checkUpdate(): AppUpdateCheck? = withContext(Dispatchers.IO) {
        mutex.withLock {
            cachedCheck?.let { if (it is AppUpdateCheck.UpToDate || it is AppUpdateCheck.Available) return@withContext it }

            // Resolve channel preference, smart-defaulting to match the running
            // build the very first time so dev users aren't silently flipped
            // to stable on upgrade.
            val currentVersion = UpdateChecker.currentVersion()
            val pref = if (currentVersion != null) {
                configRepository.getOrInitUpdateChannelPreference(currentVersion)
            } else {
                UpdateChannelPreference.STABLE
            }

            if (pref == UpdateChannelPreference.OFF) {
                return@withContext null
            }

            val channel = when (pref) {
                UpdateChannelPreference.STABLE -> ReleaseChannel.STABLE
                UpdateChannelPreference.DEV -> ReleaseChannel.DEV
                UpdateChannelPreference.OFF -> error("unreachable, short-circuited above")
            }

            val check = try {
                UpdateChecker.checkStatus(channel)
            } catch (e: Exception) {
                Logger.error("UpdateCheck: threw exception", e)
                AppUpdateCheck.Failed
            }
            if (check != AppUpdateCheck.Failed) {
                cachedCheck = check
            }
            check
        }
    }

    /**
     * Fetch the latest update info per the user's channel preference. Returns
     * null when there's no update, the channel is OFF, or the network probe
     * failed. Cached after the first successful call until [clearCache].
     */
    suspend fun getUpdateInfo(): UpdateInfo? = (checkUpdate() as? AppUpdateCheck.Available)?.info

    /**
     * Invalidate the cached result so the next [checkUpdate] or [getUpdateInfo] re-probes.
     * Called when the user changes the update channel preference in Settings.
     */
    suspend fun clearCache() {
        mutex.withLock {
            cachedCheck = null
        }
    }
}
