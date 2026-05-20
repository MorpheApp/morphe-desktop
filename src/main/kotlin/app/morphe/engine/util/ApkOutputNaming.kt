/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.engine.util

import java.io.File

/**
 * Shared filename helpers + output-path computation for patched APKs. Used by
 * both the GUI ([app.morphe.gui.ui.screens.patches.PatchSelectionViewModel])
 * and the CLI ([app.morphe.cli.command.PatchCommand]) so identical inputs
 * produce identical output paths — no surprises when users switch between
 * surfaces.
 *
 * Lives in `engine.util` because output naming is a pure data transformation
 * with no UI or CLI dependencies, and consolidating it in the engine moves
 * one more thing toward the long-term "engine is the heart" architecture.
 */
object ApkOutputNaming {

    private val patchesVersionRegex = Regex("""(\d+\.\d+\.\d+(?:-dev\.\d+)?)""")

    /**
     * Extract APK version from an APKMirror-style filename:
     * `<package>_<version>-<build>.apk` → returns `<version>`.
     * Also handles the same convention with .apkm/.xapk/.apks extensions —
     * `<package>_<version>.apkm` → returns `<version>`. Returns null for
     * filenames that don't follow this convention.
     */
    fun extractApkVersionFromFilename(fileName: String): String? = try {
        // Strip the bundle extension first so it doesn't leak into the version.
        // File.nameWithoutExtension handles single extensions cleanly; we list
        // the .apk-family ones explicitly because filenames like
        // `soundcloud_2026.04.27.apkm` have multiple "extensions" in a row
        // (the version dots look like extensions to nameWithoutExtension).
        val withoutExt = fileName
            .removeSuffix(".apk")
            .removeSuffix(".apkm")
            .removeSuffix(".xapk")
            .removeSuffix(".apks")
        val afterPackage = withoutExt.substringAfter("_")
        afterPackage.substringBefore("-").takeIf { it.isNotEmpty() }
    } catch (e: Exception) {
        null
    }

    /**
     * Extract patches version from a .mpp filename like
     * `morphe-patches-1.13.0.mpp` or `morphe-patches-1.13.0-dev.5.mpp`.
     * Returns the bare version string (`1.13.0` / `1.13.0-dev.5`) or null
     * when no version-shaped token is present.
     */
    fun extractPatchesVersion(patchesFileName: String): String? =
        patchesVersionRegex.find(patchesFileName)?.groupValues?.get(1)

    /**
     * Resolve the human-friendly app label from an APK file via ARSCLib
     * (engine [ApkManifestReader]). Returns null when:
     *  - the manifest can't be read at all (corrupt APK)
     *  - the manifest has no label
     *  - the label is stored as a resource reference (`@string/app_name`)
     *    instead of a literal string — common for big apps. Callers should
     *    fall back to a supported-apps lookup or filename in that case.
     */
    fun resolveAppDisplayName(apkFile: File): String? =
        ApkManifestReader.read(apkFile)?.applicationLabel?.takeIf { it.isNotBlank() }

    /**
     * Compute the unified output APK path. Layout:
     * `<base>/<appName>/<appName>-Morphe-{apkVer}-patches-{patchesVer}.apk`
     *
     * - Per-app subfolder prevents collisions when patching different APK
     *   versions of the same package
     * - Both versions encoded in the filename so the output is self-describing
     * - `patchesFile` is optional; if null, no `-patches-{ver}` suffix is added
     *
     * @param inputApk       the APK being patched. Its parent directory is the
     *                       default base unless [baseOutputDir] is provided.
     * @param patchesFile    primary `.mpp` file. Used only for the suffix —
     *                       in multi-source mode pass any one of the bundles.
     * @param baseOutputDir  override for the base directory (e.g. the GUI's
     *                       configured default output directory). Defaults to
     *                       `inputApk.parentFile`.
     * @param appDisplayName Pre-resolved app label (e.g. "Youtube"). If null,
     *                       falls back to the input APK's filename without
     *                       extension. GUI callers pass the value from their
     *                       apkInfo; the CLI can call [resolveAppDisplayName]
     *                       to populate this.
     */
    fun outputApkPath(
        inputApk: File,
        patchesFile: File? = null,
        baseOutputDir: File? = null,
        appDisplayName: String? = null,
    ): File {
        val appFolderName = (appDisplayName ?: inputApk.nameWithoutExtension)
            .replace(" ", "-")
        val base = baseOutputDir
            ?: inputApk.absoluteFile.parentFile
            ?: File("").absoluteFile
        val outputDir = File(base, appFolderName).also { it.mkdirs() }
        val version = extractApkVersionFromFilename(inputApk.name) ?: "patched"
        val patchesVersion = patchesFile?.name?.let { extractPatchesVersion(it) }
        val patchesSuffix = if (patchesVersion != null) "-patches-$patchesVersion" else ""
        return File(outputDir, "${appFolderName}-Morphe-${version}${patchesSuffix}.apk")
    }
}
