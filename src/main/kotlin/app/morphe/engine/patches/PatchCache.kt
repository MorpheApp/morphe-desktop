/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.patches

import app.morphe.engine.MorpheData
import app.morphe.engine.model.Release
import app.morphe.engine.model.ReleaseAsset
import java.io.File

/**
 * Shared on-disk cache layout for downloaded `.mpp` patch files.
 *
 * Both the GUI ([app.morphe.gui.data.repository.PatchRepository]) and the CLI
 * ([app.morphe.cli.command.PatchFileResolver]) resolve their cache paths through here,
 * so a patch file downloaded by one side is reused by the other instead of each keeping its own copy in a different place.
 *
 * Layout: `<MorpheData.patchesDir>/<owner>-<repo>/<tag>__<asset>.mpp`
 */
object PatchCache {

    /** Per-source cache directory, e.g. `morphe-data/patches/MorpheApp-morphe-patches/`. */
    fun sourceDir(repoPath: String): File =
        File(MorpheData.patchesDir, repoPath.replace("/", "-")).also { it.mkdirs() }

    /**
     * Per-release cache filename, prefixed with the release tag.
     *
     * Many sources name their `.mpp` asset the same string across versions
     * (e.g. `morphe-patches.mpp`); prefixing with the tag keeps versions from
     * overwriting each other in the cache.
     */
    fun cachedFileName(release: Release, asset: ReleaseAsset): String =
        "${release.tagName}__${asset.name}"
}
