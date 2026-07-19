/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.update

import app.morphe.engine.ReleaseChannel
import app.morphe.engine.model.Release
import app.morphe.engine.model.ReleaseAsset

/**
 * A fully-resolved self-update candidate: the running build's version, the
 * [Release] it would update to, the specific [asset] (the shadow JAR) to
 * download, and which [channel] the check was made against.
 *
 * This is the richer counterpart to [app.morphe.engine.UpdateInfo] — that
 * type only carries enough to render the lightweight banner (which links out
 * to the releases page); this type carries everything [UpdateDownloadManager],
 * [UpdateVerifier], and the GUI's update dialog need to actually fetch and
 * install the update in-app.
 */
data class UpdateReleaseInfo(
    val currentVersion: String,
    val release: Release,
    val asset: ReleaseAsset,
    val channel: ReleaseChannel,
) {
    val latestVersion: String get() = release.getVersion()
}
