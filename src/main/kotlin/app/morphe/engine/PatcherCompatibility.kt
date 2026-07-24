/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine

import java.io.File
import java.util.Properties
import java.util.zip.ZipInputStream
import java.util.logging.Logger

/**
 * Detects when a patch bundle (.mpp) was built against a newer morphe-patcher than this
 * build ships. Such a bundle fails to load with a `java.lang.Error` (NoSuchMethodError,
 * NoClassDefFoundError, LinkageError) because it references patcher APIs that do not exist
 * here, which otherwise surfaces as a cryptic failure. This turns it into a clear "update
 * Morphe" message, mirroring how morphe-manager reads the bundle's `Patcher-Version`
 * manifest attribute and compares it to the patcher it ships.
 *
 * Deliberately lenient: any doubt (missing attribute, unparseable version, unreadable file)
 * returns null so a valid bundle is never wrongly rejected.
 */
object PatcherCompatibility {

    private val logger = Logger.getLogger(PatcherCompatibility::class.java.name)

    /** The morphe-patcher version this build ships, from the generated version.properties. */
    val currentPatcherVersion: String? by lazy {
        runCatching {
            PatcherCompatibility::class.java
                .getResourceAsStream("/app/morphe/cli/version.properties")
                ?.use { Properties().apply { load(it) }.getProperty("patcherVersion") }
                ?.trim()
                ?.takeUnless { it.isEmpty() || it.startsWith("\${") }
        }.getOrNull()
    }

    /**
     * The morphe-patcher version [mpp] requires, from its `Patcher-Version` manifest
     * attribute. Null when the bundle predates that attribute or can't be read.
     */
    fun requiredPatcherVersion(mpp: File): String? = runCatching {
        mpp.inputStream().use { fis ->
            ZipInputStream(fis).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "META-INF/MANIFEST.MF") {
                        return@runCatching manifestAttr(zip.bufferedReader().readText(), "Patcher-Version")
                    }
                    entry = zip.nextEntry
                }
                null
            }
        }
    }.getOrNull()

    /**
     * A user-facing explanation when [mpp] needs a newer patcher than this build ships, or
     * null when compatible / unknown (never blocks on doubt).
     */
    fun incompatibilityMessage(mpp: File): String? {
        val required = requiredPatcherVersion(mpp) ?: return null
        val current = currentPatcherVersion ?: return null
        if (!isNewer(required, current)) return null
        logger.info("Bundle ${mpp.name} needs patcher $required, this build ships $current")
        return "This patch bundle needs Morphe patcher $required, but this build ships $current. " +
            "Update Morphe to use it."
    }

    private fun manifestAttr(manifest: String, key: String): String? =
        manifest.lineSequence()
            .firstOrNull { it.substringBefore(':', "").trim().equals(key, ignoreCase = true) }
            ?.substringAfter(':')?.trim()
            ?.takeUnless { it.isBlank() || it.equals("na", ignoreCase = true) }

    /** True when version [a] is newer than [b]. Compares numeric components, ignores pre-release. */
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

/**
 * A patch bundle that could not be loaded because it needs a newer patcher than this build
 * ships. Its [message] is already user-facing (from [PatcherCompatibility.incompatibilityMessage]).
 */
class PatchBundleIncompatibleException(message: String) : Exception(message)
