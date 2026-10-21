/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine

import app.morphe.engine.util.Logger
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
        if (!isNewer(required, current)) return null
        Logger.info("Bundle ${mpp.name} needs patcher $required, this build ships $current")
        return "This patch bundle needs Morphe patcher $required, but this build ships $current. " +
            "Update Morphe to use it."
    }

    private fun patcherVersionFromManifest(manifest: String): String? =
        manifest.lineSequence()
            .firstOrNull { it.substringBefore(':', "").trim().equals("Patcher-Version", ignoreCase = true) }
            ?.substringAfter(':')?.trim()
            ?.takeUnless { it.isBlank() || it.equals("na", ignoreCase = true) }

    /** Returns true if version [a] is strictly newer than [b] by numeric components. */
    private fun isNewer(a: String, b: String): Boolean {
        val pa = numericParts(a)
        val pb = numericParts(b)
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun numericParts(v: String): List<Int> =
        v.substringBefore('-').split('.').map { it.trim().toIntOrNull() ?: 0 }
}

/** Thrown when a patch bundle requires a newer patcher version than the current build. */
class PatchBundleIncompatibleException(message: String) : Exception(message)
