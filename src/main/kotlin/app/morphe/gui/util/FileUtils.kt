/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.gui.util

import app.morphe.engine.MorpheData
import java.io.File
import java.util.zip.ZipFile

/**
 * Platform-agnostic file utilities.
 * Handles app directories, temp files, and cross-platform path operations.
 *
 * Directory paths delegate to [MorpheData] (the engine-level single source of
 * truth) so the GUI, CLI, and any future surface all agree on where data
 * lives. The previous per-OS app-data folders (`%APPDATA%/morphe-gui`,
 * `~/Library/Application Support/morphe-gui`, `~/.config/morphe-gui`) are
 * superseded by `MorpheData.root` — see `unified-data-location-plan.md`.
 */
object FileUtils {

    /**
     * All modern Android architectures. Obsolete architectures such as Mips are not included.
     */
    val ANDROID_ARCHITECTURES = setOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")

    private val EXTENSION_APK_BUNDLES = setOf("apkm", "xapk", "apks")
    private val EXTENSION_APK_ANY = EXTENSION_APK_BUNDLES + "apk"

    /** Returns the unified Morphe data root. Was: per-OS app-data folder. */
    fun getAppDataDir(): File = MorpheData.root

    /** Returns the patches cache directory. */
    fun getPatchesDir(): File = MorpheData.patchesDir

    /** Returns the logs directory. */
    fun getLogsDir(): File = MorpheData.logsDir

    /** Returns the GUI config file path. */
    fun getConfigFile(): File = MorpheData.configFile

    /** Returns the patcher-scratch directory shared with the CLI. */
    fun getTempDir(): File = MorpheData.tmpDir

    /**
     * Create a unique temp directory for a patching session. Session-scoped
     * timestamp keeps concurrent CLI/GUI patches from stepping on each other
     * (see Phase 6 of the unified-data-location plan).
     */
    fun createPatchingTempDir(): File {
        val timestamp = System.currentTimeMillis()
        return File(getTempDir(), "patching-$timestamp").also { it.mkdirs() }
    }

    /**
     * Clean up a temp directory.
     */
    fun cleanupTempDir(dir: File): Boolean {
        return try {
            if (dir.exists() && dir.startsWith(getTempDir())) {
                dir.deleteRecursively()
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Clean up all temp directories (call on app exit).
     */
    fun cleanupAllTempDirs(): Boolean {
        return try {
            getTempDir().deleteRecursively()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get the size of all temp directories.
     */
    fun getTempDirSize(): Long {
        return try {
            getTempDir().walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Check if there are any temp files to clean.
     */
    fun hasTempFiles(): Boolean {
        return try {
            val tempDir = getTempDir()
            tempDir.exists() && (tempDir.listFiles()?.isNotEmpty() == true)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Build a path using the system file separator.
     */
    fun buildPath(vararg parts: String): String {
        return parts.joinToString(File.separator)
    }

    /**
     * Get file extension.
     */
    fun getExtension(file: File): String {
        return file.extension.lowercase()
    }

    /**
     * Check if file is an APK or split APK bundle (APKM, XAPK, APKS).
     */
    fun isApkFile(file: File): Boolean {
        val ext = getExtension(file)
        return file.isFile && ext in EXTENSION_APK_ANY
    }

    /**
     * Check if file is a split APK bundle (.apkm, .xapk, or .apks).
     */
    fun isBundleFormat(file: File): Boolean {
        return file.extension.lowercase() in EXTENSION_APK_BUNDLES
    }

    /**
     * Extract base.apk from a split APK bundle (.apkm, .xapk, or .apks) to a temp directory.
     * For XAPK files, the base APK may not be named "base.apk" — falls back to the
     * first non-split .apk entry or the largest by compressed size.
     * Returns the extracted base.apk file, or null if extraction fails.
     * Caller is responsible for cleaning up the returned temp file.
     */
    fun extractBaseApkFromBundle(bundleFile: File): File? {
        return try {
            ZipFile(bundleFile).use { zip ->
                val allEntries = zip.entries().asSequence().toList()

                // Try "base.apk" first (APKM format)
                var baseEntry = zip.getEntry("base.apk")

                // For XAPK: find the base APK among all .apk entries.
                // Splits are named like "config.arm64_v8a.apk", "split_config.en.apk", etc.
                // The base APK is typically the package name (e.g., "com.google.android.youtube.apk").
                if (baseEntry == null) {
                    val apkEntries = allEntries
                        .filter { !it.isDirectory && it.name.endsWith(".apk", ignoreCase = true) }

                    val splitPatterns = listOf("split_config", "config.", "split_")
                    baseEntry = apkEntries
                        .firstOrNull { entry ->
                            val name = entry.name.substringAfterLast('/').lowercase()
                            splitPatterns.none { name.startsWith(it) }
                        }
                        // Final fallback: largest .apk by compressed size
                        ?: apkEntries.maxByOrNull { it.compressedSize }
                }

                if (baseEntry == null) return null

                val tempFile = File(getTempDir(), "base-${System.currentTimeMillis()}.apk")
                zip.getInputStream(baseEntry).use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                tempFile
            }
        } catch (e: Exception) {
            null
        }
    }

    @Deprecated("Use extractBaseApkFromBundle instead", ReplaceWith("extractBaseApkFromBundle(apkmFile)"))
    fun extractBaseApkFromApkm(apkmFile: File): File? = extractBaseApkFromBundle(apkmFile)

    /**
     * Extract supported CPU architectures from native libraries in an APK or bundle.
     * Scans for lib/<arch>/ directories, and for bundles also detects arch from split APK names.
     */
    fun extractArchitectures(file: File): List<String> {
        return try {
            ZipFile(file).use { zip ->
                val archDirs = mutableSetOf<String>()

                // Scan for lib/<arch>/ entries
                zip.entries().asSequence()
                    .map { it.name }
                    .filter { it.startsWith("lib/") }
                    .mapNotNull { path ->
                        val parts = path.split("/")
                        if (parts.size >= 2) parts[1] else null
                    }
                    .forEach { archDirs.add(it) }

                // For bundles: detect arch from split APK names (e.g. split_config.arm64_v8a.apk)
                if (archDirs.isEmpty()) {
                    zip.entries().asSequence()
                        .map { it.name }
                        .filter { it.endsWith(".apk") }
                        .forEach { name ->
                            val normalized = name.replace("_", "-")
                            ANDROID_ARCHITECTURES.filter { arch -> normalized.contains(arch) }
                                .forEach { archDirs.add(it) }
                        }
                }

                archDirs.toList().ifEmpty { listOf("universal") }
            }
        } catch (e: Exception) {
            Logger.warn("Could not extract architectures: ${e.message}")
            emptyList()
        }
    }
}
