/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** How a run keeps the APK it patched in the [OriginalApkStore]. */
enum class SaveOriginalApk {
    COPY,
    MOVE,
}

/**
 * Stores one original APK/bundle per patched app (`<package>-<version>.<ext>`) so it can still be
 * repatched after the source file is removed.
 */
class OriginalApkStore(
    private val dir: File = MorpheData.originalApksDir,
    private val patchedApps: PatchedAppStore = PatchedAppStore.shared,
) {
    /** Whether [file] is one of the kept originals. */
    fun contains(file: File): Boolean =
        file.absoluteFile.parentFile?.canonicalFile == dir.canonicalFile

    /**
     * Copies [apk] into the store for [packageName] and [version], returning the stored file or null
     * if [apk] is already in the store. Replaces any previous entry for that package only after the
     * copy succeeds.
     */
    fun save(apk: File, packageName: String, version: String): File? {
        if (contains(apk)) return null
        dir.mkdirs()
        // UNSAFE_PACKAGE_CHARS strips '-', so this prefix matches a single app.
        val prefix = "${packageName.replace(UNSAFE_PACKAGE_CHARS, "_")}-"
        val safeVersion = version.ifBlank { "unknown" }.replace(UNSAFE_VERSION_CHARS, "_")
        val target = File(dir, "$prefix$safeVersion.${apk.extension.lowercase().ifEmpty { "apk" }}")

        // Written under another name first, so an interrupted copy is never taken for a kept original
        val staging = File(dir, target.name + STAGING_SUFFIX)
        try {
            apk.copyTo(staging, overwrite = true)
            Files.move(staging.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (e: Exception) {
            staging.delete()
            throw e
        }

        // Also drops what an interrupted earlier save of the app left behind
        dir.listFiles { file -> file.name.startsWith(prefix) && file != target }?.forEach { it.delete() }
        return target
    }

    /**
     * Output base directory for a stored [inputApk], reusing the previous run's output folder
     * (or `user.home`) instead of writing into this store. Returns null if [inputApk] isn't stored.
     */
    suspend fun outputBaseFor(inputApk: File): File? {
        if (!contains(inputApk)) return null
        val kept = inputApk.canonicalFile
        // Patched APKs are saved as <base>/<app name>/<file>
        return patchedApps.getAll()
            .firstOrNull { File(it.inputApkPath).canonicalFile == kept }
            ?.let { File(it.outputApkPath).parentFile?.parentFile }
            ?: File(System.getProperty("user.home"))
    }

    /** Deletes [file] if it is a kept original, leaving any file outside the store alone. */
    fun delete(file: File): Boolean = contains(file) && file.delete()

    companion object {
        private const val STAGING_SUFFIX = ".part"
        private val UNSAFE_PACKAGE_CHARS = Regex("[^A-Za-z0-9._]")
        private val UNSAFE_VERSION_CHARS = Regex("[^A-Za-z0-9._-]")

        /** Shared instance using the default storage location. */
        val shared: OriginalApkStore by lazy { OriginalApkStore() }
    }
}
