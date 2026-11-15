/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import app.morphe.engine.MorpheData
import app.morphe.engine.apk.ApkInspector
import app.morphe.engine.apk.BundleFormats
import app.morphe.engine.util.AdbManager
import app.morphe.engine.util.Logger
import app.morphe.gui.LocalShowAppIcons
import java.io.File
import java.security.MessageDigest
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

/** Source for extracting an app icon. */
sealed interface AppIconSource {
    /** Local APK or bundle file source. */
    data class Apk(val file: File) : AppIconSource

    /** Package installed on a connected device. */
    data class Device(val deviceId: String, val packageName: String) : AppIconSource
}

/** Extracts launcher icons from APKs and caches them on disk. */
object AppIconLoader {
    private const val ICON_SIZE_PX = 192

    private val adbManager = AdbManager()

    /** Icons already shown this session, so rows scrolled back into view don't flash the app initial. */
    private val memoryCache = ConcurrentHashMap<AppIconSource, ImageBitmap>()

    /** Last icon loaded per package, so its card keeps it after the APK is removed or the device disconnects. */
    private val savedIcons = ConcurrentHashMap<String, ImageBitmap>()

    /** Package name to the cache file of its last loaded icon, kept on disk so [savedIcons] survives restarts. */
    private val packageIndex: Properties by lazy {
        Properties().also { index ->
            try {
                if (packageIndexFile.isFile) packageIndexFile.inputStream().use { index.load(it) }
            } catch (_: Exception) {
                // Corrupt index; it's rebuilt as icons load
            }
        }
    }
    private val packageIndexFile: File get() = File(MorpheData.appIconsDir, "packages.properties")

    /** Cache keys whose extraction failed this session, so slow extractions aren't retried on every recomposition. */
    private val failedKeys = ConcurrentHashMap.newKeySet<String>()

    /** Extraction loads whole resource tables into memory, so only one runs at a time. */
    private val extractLock = Mutex()

    /** Returns an icon already in memory for any of [sources] or for [packageName], without loading anything. */
    fun cachedIcon(sources: List<AppIconSource>, packageName: String? = null): ImageBitmap? =
        sources.firstNotNullOfOrNull { memoryCache[it] } ?: packageName?.let { savedIcons[it] }

    /** Clears the in-memory icon cache. Call after the cache folder has been emptied. */
    fun clearMemoryCache() {
        memoryCache.clear()
        savedIcons.clear()
        packageIndex.clear()
    }

    /** Returns the icon last loaded for [packageName], or null if there is none. */
    suspend fun savedIcon(packageName: String): ImageBitmap? = withContext(Dispatchers.IO) {
        savedIcons[packageName]?.let { return@withContext it }
        val cacheFileName = packageIndex.getProperty(packageName) ?: return@withContext null
        readCacheFile(File(MorpheData.appIconsDir, cacheFileName))?.also { savedIcons[packageName] = it }
    }

    /** Loads the app icon from the first source in [sources] that has one, remembering it for [packageName] if given. */
    suspend fun load(sources: List<AppIconSource>, packageName: String? = null): ImageBitmap? = withContext(Dispatchers.IO) {
        for (source in sources) {
            val cacheFile = try {
                when (source) {
                    is AppIconSource.Apk -> cacheApkIcon(source.file)
                    is AppIconSource.Device -> cacheDeviceIcon(source.deviceId, source.packageName)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.debug("App icon extraction failed for $source: ${e.message ?: e::class.simpleName}")
                null
            }
            val icon = cacheFile?.let { readCacheFile(it) } ?: continue
            memoryCache[source] = icon
            if (packageName != null) rememberForPackage(packageName, cacheFile, icon)
            return@withContext icon
        }
        null
    }

    /** Makes sure the icon of the APK or bundle [file] is cached on disk, and returns its cache file. */
    private suspend fun cacheApkIcon(file: File): File? {
        if (!file.isFile) return null
        // A replaced or updated file changes size or modification time, which gives it a new cache key.
        val cacheKey = "${file.canonicalPath}|${file.length()}|${file.lastModified()}"
        return cacheIcon(cacheKey) {
            if (BundleFormats.isBundle(file)) extractFromBundle(file) else extractFromApks(listOf(file))
        }
    }

    /** Makes sure the icon of [packageName] installed on [deviceId] is cached on disk, and returns its cache file. */
    private suspend fun cacheDeviceIcon(deviceId: String, packageName: String): File? {
        val apkPaths = adbManager.getApkPaths(deviceId, packageName)
        if (apkPaths.isEmpty()) return null
        val cacheKey = "$packageName|${apkPaths.first()}"
        return cacheIcon(cacheKey) { extractFromDevice(deviceId, apkPaths) }
    }

    /** Returns the cache file for [cacheKey], running [extract] to create it if it doesn't exist yet. */
    private suspend fun cacheIcon(cacheKey: String, extract: suspend () -> Image?): File? {
        val cacheFile = File(MorpheData.appIconsDir, "${sha256(cacheKey)}.png")
        if (cacheFile.isFile) return cacheFile
        if (cacheKey in failedKeys) return null

        return extractLock.withLock {
            // Another row may have extracted the same icon while we waited.
            if (cacheFile.isFile) return@withLock cacheFile

            val png = extract()?.encodeToData(EncodedImageFormat.PNG)
            if (png == null) {
                failedKeys += cacheKey
                return@withLock null
            }
            cacheFile.writeBytes(png.bytes)
            cacheFile
        }
    }

    /** Records [cacheFile] as the icon of [packageName], so its card can still show it once the source is gone. */
    private fun rememberForPackage(packageName: String, cacheFile: File, icon: ImageBitmap) {
        savedIcons[packageName] = icon
        synchronized(packageIndex) {
            if (packageIndex.getProperty(packageName) == cacheFile.name) return
            packageIndex.setProperty(packageName, cacheFile.name)
            try {
                packageIndexFile.outputStream().use { packageIndex.store(it, null) }
            } catch (_: Exception) {
                // Best-effort disk write
            }
        }
    }

    /** Extracts the icon from local APKs, reading each file from the first APK that has it. */
    private suspend fun extractFromApks(apks: List<File>): Image? = withContext(Dispatchers.IO) {
        val zips = apks.map { ZipFile(it) }
        try {
            AppIconExtractor.extract(ICON_SIZE_PX) { path ->
                zips.firstNotNullOfOrNull { zip ->
                    zip.getEntry(path)?.let { entry -> zip.getInputStream(entry).use { it.readBytes() } }
                }
            }
        } finally {
            zips.forEach { it.close() }
        }
    }

    /** Extracts the icon from a bundle by first copying its base and density APKs to a temp folder. */
    private suspend fun extractFromBundle(bundle: File): Image? = withContext(Dispatchers.IO) {
        val tempDir = newTempDir()
        try {
            val apks = mutableListOf<File>()
            ZipFile(bundle).use { zip ->
                val baseApk = ApkInspector.findBaseApkEntry(zip) ?: return@withContext null
                val densityApks = zip.entries().toList().filter { it != baseApk && isDensitySplit(it.name) }
                for (entry in listOf(baseApk) + densityApks) {
                    val apk = File(tempDir, "${apks.size}.apk")
                    zip.getInputStream(entry).use { input -> apk.outputStream().use { input.copyTo(it) } }
                    apks += apk
                }
            }
            extractFromApks(apks)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    /** Extracts the icon from APKs installed on [deviceId]. */
    private suspend fun extractFromDevice(deviceId: String, apkPaths: List<String>): Image? {
        val baseApk = apkPaths.first()
        val iconApks = listOf(baseApk) + apkPaths.drop(1).filter { isDensitySplit(it) }

        // Android 9+ can stream single files out of an APK, which is much faster than pulling it.
        if (adbManager.readApkEntry(deviceId, baseApk, "AndroidManifest.xml") != null) {
            return AppIconExtractor.extract(ICON_SIZE_PX) { path ->
                iconApks.firstNotNullOfOrNull { apk -> adbManager.readApkEntry(deviceId, apk, path) }
            }
        }

        Logger.info("Device $deviceId can't stream APK files, pulling APKs for app icon")
        val tempDir = newTempDir()
        try {
            val localApks = mutableListOf<File>()
            for (remoteApk in iconApks) {
                val localApk = File(tempDir, "${localApks.size}.apk")
                if (!adbManager.pullFile(deviceId, remoteApk, localApk)) return null
                localApks += localApk
            }
            return extractFromApks(localApks)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    /** Checks whether an APK path is a screen density split. */
    private fun isDensitySplit(path: String): Boolean = path.substringAfterLast('/').contains("dpi")

    private fun newTempDir(): File = File(MorpheData.tmpDir, "app-icon-${System.nanoTime()}").also { it.mkdirs() }

    private fun readCacheFile(file: File): ImageBitmap? {
        if (!file.isFile) return null
        return try {
            Image.makeFromEncoded(file.readBytes()).toComposeImageBitmap()
        } catch (_: Exception) {
            file.delete() // corrupt cache entry
            null
        }
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

/** State holder for an app icon bitmap and its loading state. */
@Immutable
data class AppIconState(val bitmap: ImageBitmap? = null, val isLoading: Boolean = false)

/**
 * Asynchronously loads and remembers an app icon from [sources]. With [packageName], the icon is
 * also remembered for that app and shown when none of the sources are available.
 */
@Composable
fun rememberAppIcon(sources: List<AppIconSource>, packageName: String? = null): AppIconState {
    val enabled = LocalShowAppIcons.current.value
    val initial = AppIconState(bitmap = if (enabled) AppIconLoader.cachedIcon(sources, packageName) else null)
    val state by produceState(initial, enabled, sources, packageName) {
        if (!enabled) {
            value = AppIconState()
            return@produceState
        }
        // Show the saved icon straight away, then refresh it from the sources.
        val saved = packageName?.let { AppIconLoader.savedIcon(it) }
        if (value.bitmap == null) value = AppIconState(bitmap = saved)

        // Cache hits finish in milliseconds; only show the spinner for real extractions.
        val showSpinner = launch {
            delay(300.milliseconds)
            value = value.copy(isLoading = true)
        }
        value = AppIconState(bitmap = AppIconLoader.load(sources, packageName) ?: saved)
        showSpinner.cancel()
    }
    return state
}
