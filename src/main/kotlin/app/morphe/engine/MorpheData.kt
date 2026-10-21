/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine

import app.morphe.engine.util.Logger
import java.io.File

/** Resolves storage paths for runtime data, prioritizing JAR-adjacent storage with home directory fallback. */
object MorpheData {
    private val resolution: Resolution by lazy { resolveRoot() }

    /** Root data directory. */
    val root: File get() = resolution.root

    /** Containing directory of the JAR for resolving relative paths, or null in fallback mode. */
    val bundleRoot: File? get() = resolution.bundleRoot

    private data class Resolution(val root: File, val bundleRoot: File?)

    /** Directory for cached patch bundles. */
    val patchesDir: File by lazy { File(root, "patches").also { it.mkdirs() } }

    /** Directory for log files. */
    val logsDir: File by lazy { File(root, "logs").also { it.mkdirs() } }

    /** Directory for custom app icons. */
    val iconsDir: File by lazy { File(root, "icons").also { it.mkdirs() } }

    /** Directory for temporary patcher scratch files. */
    val tmpDir: File by lazy { File(root, "tmp").also { it.mkdirs() } }

    /** Configuration file path. */
    val configFile: File get() = File(root, "config.json")

    /** Default signing keystore file. */
    val defaultKeystoreFile: File get() = File(root, "morphe.keystore")

    /** Target file for converted imported keystores. */
    val importedKeystoreFile: File get() = File(root, "imported.keystore")

    // Reason primary JAR-adjacent storage could not be used.
    private enum class FallbackReason(val message: String) {
        NO_JAR_LOCATION("Could not determine JAR location (running from IDE / classpath?)"),
        NOT_A_JAR("Running source is not a JAR (likely IDE / `./gradlew run`)"),
        NOT_WRITABLE("JAR directory is not writable"),
        EXCEPTION("Exception while resolving JAR location"),
    }

    private fun resolveRoot(): Resolution {
        // Allow explicit override via MORPHE_DATA_DIR.
        envOverrideRoot()?.let { override ->
            Logger.info("Morphe data root: ${override.absolutePath} (MORPHE_DATA_DIR override)")
            override.mkdirs()
            return Resolution(root = override, bundleRoot = null)
        }
        val (jarAdjacent, fallbackReason) = tryJarAdjacent()
        if (jarAdjacent != null) {
            Logger.info("Morphe data root: ${jarAdjacent.absolutePath} (JAR-adjacent)")
            jarAdjacent.mkdirs()
            return Resolution(root = jarAdjacent, bundleRoot = jarAdjacent.parentFile)
        }
        val fallback = userHomeFallback()
        Logger.warn(
            "Morphe data root falling back to ${fallback.absolutePath}, " +
                "primary (JAR-adjacent) unavailable: ${fallbackReason?.message ?: "unknown"}"
        )
        fallback.mkdirs()
        return Resolution(root = fallback, bundleRoot = null)
    }

    /** Attempts to resolve the data root adjacent to the executing JAR. */
    private fun tryJarAdjacent(): Pair<File?, FallbackReason?> {
        val location = try {
            MorpheData::class.java.protectionDomain.codeSource?.location
                ?: return null to FallbackReason.NO_JAR_LOCATION
        } catch (_: Exception) {
            return null to FallbackReason.EXCEPTION
        }

        val jarFile = try {
            // Resolve symlinks to place data next to the real JAR.
            File(location.toURI()).canonicalFile
        } catch (_: Exception) {
            return null to FallbackReason.EXCEPTION
        }

        // Fall back when running unpackaged from a classes directory.
        if (jarFile.isDirectory || !jarFile.name.endsWith(".jar")) {
            return null to FallbackReason.NOT_A_JAR
        }

        val candidate = File(jarFile.parentFile, "morphe-data")
        if (!isWritable(candidate)) {
            return null to FallbackReason.NOT_WRITABLE
        }
        return candidate to null
    }

    /** Resolves data directory from MORPHE_DATA_DIR if set and writable. */
    private fun envOverrideRoot(): File? {
        val raw = System.getenv("MORPHE_DATA_DIR")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val dir = File(raw)
        val usable = runCatching { dir.mkdirs(); dir.isDirectory && dir.canWrite() }.getOrDefault(false)
        if (!usable) {
            Logger.warn("MORPHE_DATA_DIR is set to '$raw' but it isn't a writable directory, ignoring.")
            return null
        }
        return dir
    }

    private fun userHomeFallback(): File {
        val userHome = System.getProperty("user.home")
        val legacy = File(userHome, "morphe")
        // Prefer existing legacy directory if present.
        if (legacy.isDirectory) return legacy
        return xdgDataRoot() ?: legacy
    }

    /** Returns `$XDG_DATA_HOME/morphe` on Linux if set. */
    private fun xdgDataRoot(): File? {
        val os = System.getProperty("os.name")?.lowercase().orEmpty()
        val isLinux = "linux" in os || "nix" in os || "nux" in os
        if (!isLinux) return null
        val base = System.getenv("XDG_DATA_HOME")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return File(base, "morphe")
    }

    private fun isWritable(dir: File): Boolean {
        if (dir.exists()) return dir.canWrite()
        val parent = dir.parentFile ?: return false
        return parent.canWrite()
    }
}
