/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.data.repository

import app.morphe.engine.ReleaseChannel
import app.morphe.engine.UpdateChecker
import app.morphe.engine.UpdateInfo
import app.morphe.gui.data.model.UpdateChannelPreference
import app.morphe.gui.util.Logger as MorpheLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.logging.Logger

/**
 * Singleton wrapper around [UpdateChecker] so the network probe runs at most
 * once per (app session, channel) combination. Both home view models share the
 * cached result. Settings should call [clearCache] after the user changes the
 * update channel preference so the next read re-probes.
 */
class UpdateCheckRepository(
    private val configRepository: ConfigRepository,
) {

    private val mutex = Mutex()
    private var cached: Result<UpdateInfo?>? = null
    private val logger = Logger.getLogger(UpdateCheckRepository::class.java.name)

    /**
     * Fetch the latest update info per the user's channel preference. Returns
     * null when there's no update, the channel is OFF, or the network probe
     * failed. Cached after the first successful call until [clearCache].
     */
    suspend fun getUpdateInfo(): UpdateInfo? = withContext(Dispatchers.IO) {
        mutex.withLock {
            cached?.let { return@withContext it.getOrNull() }

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
                cached = Result.success(null)
                return@withContext null
            }

            val channel = when (pref) {
                UpdateChannelPreference.STABLE -> ReleaseChannel.STABLE
                UpdateChannelPreference.DEV -> ReleaseChannel.DEV
                UpdateChannelPreference.OFF -> error("unreachable — short-circuited above")
            }

            val info = try {
                UpdateChecker.checkInfo(logger, channel)
            } catch (e: Exception) {
                MorpheLogger.error("UpdateCheck: threw exception", e)
                null
            }
            cached = Result.success(info)
            info
        }
    }

    /**
     * Invalidate the cached result so the next [getUpdateInfo] re-probes.
     * Called when the user changes the update channel preference in Settings.
     */
    suspend fun clearCache() {
        mutex.withLock {
            cached = null
        }
    }
}
