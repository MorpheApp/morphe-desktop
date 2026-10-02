/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.apk

import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import java.io.ByteArrayInputStream
import java.io.File
import java.util.logging.Logger
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

object AndroidArchitectures {
    const val UNIVERSAL = "universal"
    const val ARM64_V8A = "arm64-v8a"
    const val ARMEABI_V7A = "armeabi-v7a"
    const val X86_64 = "x86_64"
    const val X86 = "x86"

    val ALL = setOf(ARM64_V8A, ARMEABI_V7A, X86, X86_64)
}

/**
 * Unified archive inspection engine for standalone APKs and split APK bundles
 * (.apkm, .xapk, .apks).
 *
 * All operations execute in-memory with zero temporary disk writes.
 */
object ApkInspector {
    private val logger = Logger.getLogger(ApkInspector::class.java.name)
    private val splitPatterns = listOf("split_config", "config.", "split_")

    /**
     * Inspect an APK or bundle archive and extract its manifest attributes and CPU architectures.
     * Returns null if the file does not exist, is not an archive, or has no valid Android manifest.
     */
    fun inspect(file: File): ApkInspectionResult? {
        if (!file.exists() || !file.isFile) return null

        return try {
            if (BundleFormats.isBundle(file)) {
                inspectBundle(file)
            } else {
                inspectApk(file)
            }
        } catch (e: Exception) {
            logger.warning("Failed to inspect ${file.name}: ${e.message ?: e::class.simpleName}")
            null
        }
    }

    /**
     * Extract CPU architectures from an APK or bundle.
     */
    fun extractArchitectures(file: File): Set<String> {
        return inspect(file)?.architectures ?: emptySet()
    }

    /**
     * Optional utility to extract base.apk from a split bundle to a target file.
     * Inspection itself does NOT use this method and operates purely in-memory.
     */
    fun extractBaseApk(bundleFile: File, destinationFile: File): File? {
        return try {
            ZipFile(bundleFile).use { zip ->
                val baseEntry = findBaseApkEntry(zip) ?: return null
                destinationFile.parentFile?.mkdirs()
                zip.getInputStream(baseEntry).use { input ->
                    destinationFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                destinationFile
            }
        } catch (e: Exception) {
            logger.warning("Failed to extract base APK from ${bundleFile.name}: ${e.message}")
            null
        }
    }

    private fun inspectApk(file: File): ApkInspectionResult? {
        ZipFile(file).use { zip ->
            val manifestEntry = zip.getEntry("AndroidManifest.xml") ?: run {
                logger.warning("No AndroidManifest.xml found in APK ${file.name}")
                return null
            }

            val block = zip.getInputStream(manifestEntry).use { input ->
                AndroidManifestBlock.load(input)
            }

            val packageName = block.packageName ?: run {
                logger.warning("No package name in manifest of ${file.name}")
                return null
            }

            val archDirs = mutableSetOf<String>()
            zip.entries().asSequence()
                .map { it.name }
                .filter { it.startsWith("lib/") }
                .forEach { path ->
                    val parts = path.split("/")
                    if (parts.size >= 2 && parts[1].isNotBlank()) {
                        archDirs.add(parts[1])
                    }
                }

            val architectures = if (archDirs.isEmpty()) {
                setOf(AndroidArchitectures.UNIVERSAL)
            } else {
                archDirs
            }

            return ApkInspectionResult(
                file = file,
                packageName = packageName,
                versionName = block.versionName,
                versionCode = block.versionCode,
                minSdkVersion = block.minSdkVersion,
                applicationLabel = block.applicationLabelString,
                architectures = architectures,
                isBundle = false,
            )
        }
    }

    private fun inspectBundle(file: File): ApkInspectionResult? {
        ZipFile(file).use { zip ->
            val baseEntry = findBaseApkEntry(zip) ?: run {
                logger.warning("Could not find base APK inside bundle ${file.name}")
                return null
            }

            var manifestBlock: AndroidManifestBlock? = null
            val baseEmbeddedArchs = mutableSetOf<String>()

            // Stream base.apk directly in memory without writing to disk
            zip.getInputStream(baseEntry).use { baseIn ->
                ZipInputStream(baseIn).use { innerZip ->
                    var innerEntry = innerZip.nextEntry
                    while (innerEntry != null) {
                        if (innerEntry.name == "AndroidManifest.xml") {
                            val manifestBytes = innerZip.readBytes()
                            manifestBlock = AndroidManifestBlock.load(ByteArrayInputStream(manifestBytes))
                        } else if (innerEntry.name.startsWith("lib/")) {
                            val parts = innerEntry.name.split("/")
                            if (parts.size >= 2 && parts[1].isNotBlank()) {
                                baseEmbeddedArchs.add(parts[1])
                            }
                        }
                        innerZip.closeEntry()
                        innerEntry = innerZip.nextEntry
                    }
                }
            }

            val block = manifestBlock ?: run {
                logger.warning("No AndroidManifest.xml found inside base APK of bundle ${file.name}")
                return null
            }

            val packageName = block.packageName ?: run {
                logger.warning("No package name in manifest of bundle ${file.name}")
                return null
            }

            val architectures = mutableSetOf<String>()
            architectures.addAll(baseEmbeddedArchs)

            // Detect architectures from split package names in bundle (e.g. split_config.arm64_v8a.apk)
            zip.entries().asSequence()
                .map { it.name }
                .filter { it.endsWith(".apk", ignoreCase = true) }
                .forEach { name ->
                    val normalized = name.replace("_", "-")
                    AndroidArchitectures.ALL.filter { arch -> normalized.contains(arch) }
                        .forEach { architectures.add(it) }
                }

            val finalArchitectures = if (architectures.isEmpty()) {
                setOf(AndroidArchitectures.UNIVERSAL)
            } else {
                architectures
            }

            return ApkInspectionResult(
                file = file,
                packageName = packageName,
                versionName = block.versionName,
                versionCode = block.versionCode,
                minSdkVersion = block.minSdkVersion,
                applicationLabel = block.applicationLabelString,
                architectures = finalArchitectures,
                isBundle = true,
            )
        }
    }

    private fun findBaseApkEntry(zip: ZipFile): ZipEntry? {
        // 1. APKM / standard bundle convention
        zip.getEntry("base.apk")?.let { return it }

        // 2. APKS (bundletool / SAI) convention
        zip.getEntry("splits/base-master.apk")?.let { return it }
        zip.getEntry("splits/base.apk")?.let { return it }

        // 3. XAPK / custom bundle: look for non-split .apk entry
        val allApks = zip.entries().asSequence()
            .filter { !it.isDirectory && it.name.endsWith(".apk", ignoreCase = true) }
            .toList()

        val nonSplit = allApks.firstOrNull { entry ->
            val simpleName = entry.name.substringAfterLast('/').lowercase()
            splitPatterns.none { simpleName.startsWith(it) }
        }

        // 4. Fallback: largest APK by compressed size
        return nonSplit ?: allApks.maxByOrNull { it.compressedSize }
    }
}
