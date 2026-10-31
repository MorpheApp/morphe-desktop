/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine

import app.morphe.engine.util.Logger
import app.morphe.engine.util.isNewerVersion
import java.io.File
import java.util.zip.ZipInputStream

/** Checks patch bundle compatibility against the bundled morphe-patcher version. */
object PatcherCompatibility {

    /** Version of morphe-patcher on the classpath. */
    val currentPatcherVersion: String? get() = MorpheComponents.patcherVersion

    /** Reads the required patcher version from [mpp]'s manifest, or null if missing. */
    fun requiredPatcherVersion(mpp: File): String? = runCatching {
        mpp.inputStream().use { fis ->
            ZipInputStream(fis).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "META-INF/MANIFEST.MF") {
                        return@runCatching patcherVersionFromManifest(zip.bufferedReader().readText())
                    }
                    entry = zip.nextEntry
                }
                null
            }
        }
    }.getOrNull()

    /** Returns an explanation if [mpp] requires a newer patcher than the active build, or null if compatible. */
    fun incompatibilityMessage(mpp: File): String? {
        val required = requiredPatcherVersion(mpp) ?: return null
        val current = currentPatcherVersion ?: return null
        if (!isNewerVersion(required, current)) return null
        Logger.info("Bundle ${mpp.name} needs patcher $required, this build ships $current")
        return "This patch bundle needs Morphe patcher $required, but this build ships $current. " +
            "Update Morphe to use it."
    }

    private fun patcherVersionFromManifest(manifest: String): String? =
        manifest.lineSequence()
            .firstOrNull { it.substringBefore(':', "").trim().equals("Patcher-Version", ignoreCase = true) }
            ?.substringAfter(':')?.trim()
            ?.takeUnless { it.isBlank() || it.equals("na", ignoreCase = true) }
}

/** Thrown when a patch bundle requires a newer patcher version than the current build. */
class PatchBundleIncompatibleException(
    message: String,
    val requiredPatcherVersion: String? = null,
    val currentPatcherVersion: String? = null,
    val appVersion: String? = null,
    cause: Throwable? = null,
) : Exception(message, cause)
