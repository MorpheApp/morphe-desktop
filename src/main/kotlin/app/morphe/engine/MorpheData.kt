/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.engine

import java.io.File
import java.util.logging.Logger

/**
 * Single source of truth for where Morphe stores its runtime data on disk.
 *
 * **Primary location**: a `morphe-data/` folder created **next to the running
 * JAR**. Survives upgrades (multiple JAR versions side-by-side share one
 * folder), trivially findable for users (just `cd` to where the JAR lives),
 * portable across drives/USB sticks.
 *
 * **Fallback location**: `~/morphe/`. Used when the primary path is
 * unreachable — most commonly when running from an IDE (`./gradlew run`)
 * where the "JAR location" resolves to a `build/classes/` directory that
 * would get wiped by `./gradlew clean`. Also covers the (rare) case of a
 * read-only JAR install location.
 *
 * Layout once populated:
 * ```
 * morphe-data/
 *   patches/{owner}-{repo}/v1.5.0__patches.mpp   # downloaded .mpp files
 *   logs/                                        # app logs
 *   config.json                                  # GUI preferences + sources
 *   tmp/patching-{timestamp}/                    # per-session patcher scratch
 *   morphe.keystore                              # shared default signing key
 * ```
 *
 * A single shared keystore is intentional: Android refuses updates whose
 * signatures don't match the installed app, so per-app or per-output-APK
 * keystores would break "re-patch and reinstall over the old version." A
 * user who wants their own signing identity can point at a custom keystore
 * in Settings, which overrides this default.
 *
 * All paths are computed lazily so the JVM is fully bootstrapped (classloader,
 * security manager, etc.) before we probe for the JAR location. The
 * resolution runs **at most once per JVM** — the lazy property caches.
 */
object MorpheData {
    private val logger = Logger.getLogger(MorpheData::class.java.name)

    private val resolution: Resolution by lazy { resolveRoot() }

    /** Root: JAR-adjacent `morphe-data/`, with fallback to `~/morphe/`. */
    val root: File get() = resolution.root

    /**
     * Anchor for portable relative paths in `config.json`. This is the
     * **JAR's containing directory** — i.e. `root.parentFile` in the happy
     * (JAR-adjacent) case. Null in fallback / IDE mode because there's no
     * portable bundle then.
     *
     * Paths the user picks (output directory, keystore) that live under this
     * anchor are stored in config as anchor-relative, so the whole bundle
     * (JAR + `morphe-data/` + sibling folders) survives being moved.
     */
    val bundleRoot: File? get() = resolution.bundleRoot

    private data class Resolution(val root: File, val bundleRoot: File?)

    /** Downloaded `.mpp` patch files, organized by source. */
    val patchesDir: File by lazy { File(root, "patches").also { it.mkdirs() } }

    /** App logs. */
    val logsDir: File by lazy { File(root, "logs").also { it.mkdirs() } }

    /** Patcher scratch space. Each patching session gets its own subfolder
     *  here (see Phase 6 of the unified-data-location plan). */
    val tmpDir: File by lazy { File(root, "tmp").also { it.mkdirs() } }

    /** GUI's persisted preferences (theme, enabled sources, etc.). */
    val configFile: File get() = File(root, "config.json")

    /**
     * Default shared keystore. The patcher library creates it on first sign
     * if missing; subsequent signs reuse the same identity so patched apps
     * can be updated on-device without reinstalling.
     */
    val defaultKeystoreFile: File get() = File(root, "morphe.keystore")

    /**
     * Reason the primary (JAR-adjacent) location was rejected. Drives the
     * fallback log message so a user reporting "where's my cache?" can
     * tell from logs alone which branch ran.
     */
    private enum class FallbackReason(val message: String) {
        NO_JAR_LOCATION("Could not determine JAR location (running from IDE / classpath?)"),
        NOT_A_JAR("Running source is not a JAR (likely IDE / `./gradlew run`)"),
        NOT_WRITABLE("JAR directory is not writable"),
        EXCEPTION("Exception while resolving JAR location"),
    }

    private fun resolveRoot(): Resolution {
        val (jarAdjacent, fallbackReason) = tryJarAdjacent()
        if (jarAdjacent != null) {
            logger.info("Morphe data root: ${jarAdjacent.absolutePath} (JAR-adjacent)")
            jarAdjacent.mkdirs()
            return Resolution(root = jarAdjacent, bundleRoot = jarAdjacent.parentFile)
        }
        val fallback = userHomeFallback()
        // WARNING level — users debugging "I can't find my patches" or "config
        // didn't persist" need to see this to know we fell back and why.
        logger.warning(
            "Morphe data root falling back to ${fallback.absolutePath} — " +
                "primary (JAR-adjacent) unavailable: ${fallbackReason?.message ?: "unknown"}"
        )
        fallback.mkdirs()
        // No portable bundle concept in fallback mode — paths stay absolute.
        return Resolution(root = fallback, bundleRoot = null)
    }

    /**
     * Returns (path, null) on success, (null, reason) on fallback.
     * The reason gets surfaced in logs so users can tell WHY we fell back.
     */
    private fun tryJarAdjacent(): Pair<File?, FallbackReason?> {
        val location = try {
            MorpheData::class.java.protectionDomain.codeSource?.location
                ?: return null to FallbackReason.NO_JAR_LOCATION
        } catch (e: Exception) {
            return null to FallbackReason.EXCEPTION
        }

        val jarFile = try {
            // canonicalFile resolves symlinks — Homebrew/asdf-style installs
            // often symlink the JAR; we want the cache next to the real file,
            // not next to the symlink.
            File(location.toURI()).canonicalFile
        } catch (e: Exception) {
            return null to FallbackReason.EXCEPTION
        }

        // When running from IDE (`./gradlew run`), location is a classes
        // directory, not a JAR. Detect and fall back so we don't pollute
        // build outputs that `./gradlew clean` wipes.
        if (jarFile.isDirectory || !jarFile.name.endsWith(".jar")) {
            return null to FallbackReason.NOT_A_JAR
        }

        val candidate = File(jarFile.parentFile, "morphe-data")
        if (!isWritable(candidate)) {
            return null to FallbackReason.NOT_WRITABLE
        }
        return candidate to null
    }

    private fun userHomeFallback(): File {
        val userHome = System.getProperty("user.home")
        return File(userHome, "morphe")
    }

    private fun isWritable(dir: File): Boolean {
        if (dir.exists()) return dir.canWrite()
        // Probe parent — if we can create the dir, we can write to it.
        val parent = dir.parentFile ?: return false
        return parent.canWrite()
    }
}
