/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.engine.util

import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import java.io.File
import java.util.logging.Logger
import java.util.zip.ZipFile

/**
 * Read structural metadata from an APK's `AndroidManifest.xml` using ARSCLib.
 *
 * This is the **only** manifest reader we use across the project. It replaces
 * `net.dongliu:apk-parser`, which is unmaintained and crashes on split APKs
 * whose base.apk references resources living in other splits (SoundCloud,
 * Spotify, every large modular Play Store app). ARSCLib is the same library
 * morphe-patcher uses internally — so anything the patcher can read, we can
 * read.
 *
 * Only **direct string attributes** are exposed (packageName, versionName,
 * minSdkVersion). The application label is included only when it's a literal
 * string in the manifest — when it's a `@string/app_name` resource reference,
 * resolving it would require the full resource table (and the split tables
 * for split APKs), which we deliberately don't do. Callers that need a
 * friendly display name should look it up against their supported-apps list
 * by packageName.
 *
 * Caller is responsible for extracting `base.apk` from bundle formats
 * (.apkm/.xapk/.apks) before passing to [read].
 */
object ApkManifestReader {
    private val logger = Logger.getLogger(ApkManifestReader::class.java.name)

    /**
     * Read the manifest of an APK file. Returns null on failure (corrupt,
     * not an APK, missing AndroidManifest.xml).
     */
    fun read(apkFile: File): ApkManifest? {
        return try {
            ZipFile(apkFile).use { zip ->
                val entry = zip.getEntry("AndroidManifest.xml") ?: run {
                    logger.warning("No AndroidManifest.xml in ${apkFile.name}")
                    return null
                }
                val block = zip.getInputStream(entry).use { input ->
                    AndroidManifestBlock.load(input)
                }
                val packageName = block.packageName ?: run {
                    logger.warning("Manifest has no package name in ${apkFile.name}")
                    return null
                }
                ApkManifest(
                    packageName = packageName,
                    versionName = block.versionName,
                    versionCode = block.versionCode,
                    minSdkVersion = block.minSdkVersion,
                    applicationLabel = block.applicationLabelString,
                )
            }
        } catch (e: Exception) {
            logger.warning("Failed to read manifest from ${apkFile.name}: ${e.message}")
            null
        }
    }
}

/**
 * Direct attributes from `AndroidManifest.xml`. None of these require resource
 * resolution — they're plain strings/integers stored inline in the manifest.
 *
 * @property packageName        always present (manifest is rejected without it)
 * @property versionName        may be null for APKs that omit it (rare)
 * @property versionCode        may be null for APKs that omit it (rare)
 * @property minSdkVersion      from `<uses-sdk android:minSdkVersion>`
 * @property applicationLabel   the app's display name, only when stored as a
 *                              literal string. Null when stored as a resource
 *                              reference (`@string/app_name`) — callers should
 *                              fall back to a supported-apps lookup by package.
 */
data class ApkManifest(
    val packageName: String,
    val versionName: String?,
    val versionCode: Int?,
    val minSdkVersion: Int?,
    val applicationLabel: String?,
)
