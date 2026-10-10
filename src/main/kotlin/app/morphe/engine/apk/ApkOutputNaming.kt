/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.apk

import app.morphe.engine.patches.PatchBundleLoader
import java.io.File

/**
 * Shared filename helpers + output-path computation for patched APKs. Used by
 * both the GUI and the CLI so identical inputs produce identical output paths.
 */
object ApkOutputNaming {

    private val patchesVersionRegex = Regex("""(\d+\.\d+\.\d+(?:-dev\.\d+)?)""")

    /**
     * Extract APK version from an APKMirror-style filename:
     * `<package>_<version>-<build>.apk` → returns `<version>`.
     * Also handles the same convention with .apkm/.xapk/.apks.
     * Returns null for filenames that don't follow this convention.
     */
    fun extractApkVersionFromFilename(fileName: String): String? = try {
        val withoutExt = stripKnownExtension(fileName)
        val afterPackage = withoutExt.substringAfter("_")
        afterPackage.substringBefore("-").takeIf { it.isNotEmpty() }
    } catch (e: Exception) {
        null
    }

    /**
     * Best-effort package + version extraction from APKMirror-style filenames:
     *   `com.google.android.youtube_19.20.30-12345.apk` → `("com.google.android.youtube", "19.20.30")`
     *
     * Returns `(null, null)` when the filename doesn't match a package_version pattern.
     * Scans for semver / date patterns as fallback when not in standard APKMirror format.
     */
    fun extractPackageAndVersionFromFilename(fileName: String): Pair<String?, String?> = try {
        val withoutExt = stripKnownExtension(fileName)
        val splitOnUnderscore = withoutExt.split('_', limit = 2)

        val packageCandidate = splitOnUnderscore.getOrNull(0)
        val afterUnderscore = splitOnUnderscore.getOrNull(1)

        val looksLikePackage = packageCandidate != null &&
            packageCandidate.contains('.') &&
            packageCandidate.split('.').all { segment ->
                segment.isNotEmpty() && segment.all { c -> c.isLowerCase() || c.isDigit() || c == '_' }
            }

        val packageName = if (looksLikePackage) packageCandidate else null

        val versionAfterUnderscore = afterUnderscore?.substringBefore('-')?.takeIf { it.isNotBlank() }
        val version = versionAfterUnderscore
            ?: Regex("""\d+\.\d+\.\d+(?:-dev\.\d+)?""").find(withoutExt)?.value
            ?: Regex("""\d+\.\d+(?:\.\d+)?""").find(withoutExt)?.value

        packageName to version
    } catch (e: Exception) {
        null to null
    }

    private fun stripKnownExtension(fileName: String): String {
        for (ext in BundleFormats.SUPPORTED_EXTENSIONS) {
            if (fileName.endsWith(".$ext", ignoreCase = true)) {
                return fileName.dropLast(ext.length + 1)
            }
        }
        return fileName.substringBeforeLast('.')
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
     * Resolve the human-friendly app label from an APK or bundle archive via ARSCLib.
     * Returns null when:
     *  - the manifest can't be read at all (corrupt archive)
     *  - the manifest has no label
     *  - the label is stored as a resource reference (`@string/app_name`)
     *    instead of a literal string. Callers should fall back to
     *    a supported-apps lookup or filename in that case.
     */
    fun resolveAppDisplayName(apkFile: File): String? =
        ApkInspector.inspect(apkFile)?.applicationLabel?.takeIf { it.isNotBlank() }

    /**
     * The app's versionName from its manifest or null when it can't be read or 
     * is blank. Supports both standalone APKs and split APK bundles.
     */
    fun resolveAppVersion(apkFile: File): String? =
        ApkInspector.inspect(apkFile)?.versionName?.takeIf { it.isNotBlank() }

    /**
     * Compute the unified output APK path. Layout:
     * `<base>/<appName>/<appName>-{apkVer}-patches-{patchesVer}.apk`
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
        appVersion: String? = null,
    ): File {
        val appFolderName = (appDisplayName ?: inputApk.nameWithoutExtension)
            .replace(" ", "-")
        val base = baseOutputDir
            ?: inputApk.absoluteFile.parentFile
            ?: File("").absoluteFile
        val outputDir = File(base, appFolderName).also { it.mkdirs() }
        // App version, most reliable first: a version the caller already resolved, then the
        // APK manifest's versionName, then the input filename (APKMirror convention), then a
        // constant. Reading it from the manifest is what keeps the output unique by app
        // version even when the input file was renamed (e.g. base.apk) — the filename-only
        // path fell back to "patched" and collided across versions.
        val version = sanitizeForFilename(
            appVersion?.takeIf { it.isUsableVersion() }
                ?: resolveAppVersion(inputApk)
                ?: extractApkVersionFromFilename(inputApk.name)
                ?: "patched"
        )
        val patchesVersion = patchesFile?.let { PatchBundleLoader.extractVersion(it) }
            ?: patchesFile?.name?.let { extractPatchesVersion(it) }
        val patchesSuffix = if (patchesVersion != null) "-patches-$patchesVersion" else ""
        return File(outputDir, "${appFolderName}-${version}${patchesSuffix}.apk")
    }

    private fun String.isUsableVersion(): Boolean =
        isNotBlank() && !equals("unknown", ignoreCase = true)

    /** Keep a versionName filename-safe: some apps put spaces or symbols in versionName. */
    private fun sanitizeForFilename(v: String): String =
        v.trim().replace(Regex("""[^A-Za-z0-9.\-_]"""), "-").ifBlank { "patched" }
}
