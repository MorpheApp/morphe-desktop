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
     * Returns null for filenames that don't follow this convention.
     */
    fun extractApkVersionFromFilename(fileName: String): String? = try {
        val afterPackage = fileName.substringAfter("_")
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
     * Resolve the human-friendly app label from an APK file using apk-parser.
     * Returns null when parsing fails (corrupt APK) or when the manifest has
     * no label. Callers should fall back to filename in that case.
     *
     * This is the same path the GUI uses to populate `apkInfo.displayName`
     * — running it here lets the CLI produce the same friendly folder names
     * without each caller having to roll its own.
     */
    fun resolveAppDisplayName(apkFile: File): String? = try {
        net.dongliu.apk.parser.ApkFile(apkFile).use { apk ->
            apk.apkMeta?.label?.takeIf { it.isNotBlank() }
        }
    } catch (e: Exception) {
        null
    }

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
