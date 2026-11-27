/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine

import app.morphe.engine.network.HttpService
import app.morphe.engine.util.FileChecksum
import app.morphe.engine.util.Logger
import app.morphe.engine.util.normalizeVersion
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import java.io.File
import java.io.IOException
import java.net.URL
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.CodeSigner
import java.security.MessageDigest
import java.time.Instant
import java.util.Locale
import java.util.Properties
import java.util.jar.JarFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.system.exitProcess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object SelfUpdater {
    internal const val LAUNCHER_PID_ENV = "MORPHE_LAUNCHER_PID"
    private const val REPO_OWNER = "MorpheApp"
    private const val REPO_NAME = "morphe-desktop"
    private const val LAUNCHER_CLASS_ENTRY = "app/morphe/MorpheLauncherKt.class"
    private const val VERSION_PROPERTIES_ENTRY = "app/morphe/cli/version.properties"
    private const val WAIT_PID_TIMEOUT_MS = 15_000L
    private const val WAIT_PID_POLL_MS = 100L
    private const val MOVE_RETRY_ATTEMPTS = 40
    private const val MOVE_RETRY_DELAY_MS = 250L
    private const val CLASS_FILE_MAGIC = 0xCAFEBABE.toInt()

    private val VERSIONED_JAR_REGEX = Regex("""^morphe-desktop-.+-all\.jar$""", RegexOption.IGNORE_CASE)
    private val applyJson = Json { ignoreUnknownKeys = true }

    /** Outer launcher JVM PID (`MORPHE_LAUNCHER_PID`), waited on before replacing the JAR. */
    @Volatile
    var launcherParentPid: Long? = null

    @Serializable
    internal data class ApplyPlan(
        val source: String,
        val oldJar: String,
        val destJar: String,
        val sha256: String,
        val waitPids: List<Long> = emptyList(),
        val relaunchGui: Boolean = false,
    )

    @JvmStatic
    fun main(args: Array<String>) {
        val payload = System.`in`.bufferedReader(Charsets.UTF_8).use { it.readText() }
        exitProcess(runApplyUpdateFromJson(payload))
    }

    sealed interface AppInstallation {
        data class WritableJar(
            val jarFile: File,
            val isSymlink: Boolean,
            val signerFingerprints: Set<String>,
        ) : AppInstallation
        data class UnsignedJar(val jarFile: File) : AppInstallation
        data class ManagedByHomebrew(val jarFile: File) : AppInstallation
        data class ReadOnlyJar(val jarFile: File) : AppInstallation
        data object NotAJar : AppInstallation
    }

    data class StagedUpdate(
        val stagedJar: File,
        val oldJar: File,
        val destinationJar: File,
        val sha256: String,
        val version: String,
    )

    class IncompatibleJavaVersionException(
        val requiredJavaVersion: Int,
        val currentJavaVersion: Int,
        val targetVersion: String,
    ) : IOException(
        "Update v$targetVersion requires Java $requiredJavaVersion or newer (currently running Java $currentJavaVersion)"
    )

    @Serializable
    private data class GitHubReleaseDetail(
        val assets: List<GitHubReleaseAssetDetail> = emptyList(),
    )

    @Serializable
    private data class GitHubReleaseAssetDetail(
        val url: String = "",
        val name: String = "",
        @SerialName("browser_download_url")
        val browserDownloadUrl: String = "",
    )

    fun detectInstallation(
        codeSourceLocation: URL? = MorpheData::class.java.protectionDomain.codeSource?.location,
        isDirWritable: (File) -> Boolean = ::isDirectoryWritable,
        runningSignerFingerprints: () -> Set<String> = ::currentJarSignerFingerprints,
    ): AppInstallation {
        val location = codeSourceLocation ?: return AppInstallation.NotAJar
        val rawFile = runCatching { File(location.toURI()).absoluteFile }.getOrNull() ?: return AppInstallation.NotAJar
        val isSymlink = runCatching { Files.isSymbolicLink(rawFile.toPath()) }.getOrDefault(false)
        val canonicalFile = runCatching { rawFile.canonicalFile }.getOrDefault(rawFile)

        if (canonicalFile.isDirectory || !canonicalFile.name.endsWith(".jar", ignoreCase = true)) {
            return AppInstallation.NotAJar
        }

        if (isHomebrewPath(canonicalFile.path)) {
            return AppInstallation.ManagedByHomebrew(canonicalFile)
        }

        val signers = runningSignerFingerprints()
        if (signers.isEmpty()) {
            return AppInstallation.UnsignedJar(canonicalFile)
        }

        val parent = canonicalFile.parentFile
        if (parent == null || !isDirWritable(parent)) {
            return AppInstallation.ReadOnlyJar(canonicalFile)
        }

        return AppInstallation.WritableJar(
            jarFile = canonicalFile,
            isSymlink = isSymlink,
            signerFingerprints = signers,
        )
    }

    private fun currentJarSignerFingerprints(): Set<String> =
        signerFingerprintsOf(SelfUpdater::class.java.protectionDomain.codeSource?.codeSigners)

    private fun signerFingerprintsOf(codeSigners: Array<CodeSigner>?): Set<String> {
        if (codeSigners.isNullOrEmpty()) return emptySet()
        return codeSigners.mapNotNull { signer ->
            val cert = signer.signerCertPath.certificates.firstOrNull() ?: return@mapNotNull null
            sha256Hex(cert.encoded)
        }.toSet()
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    internal fun isHomebrewPath(path: String): Boolean {
        val normalized = path.replace('\\', '/')
        return normalized.contains("/opt/homebrew/") ||
            normalized.contains("/usr/local/Cellar/") ||
            normalized.contains("/home/linuxbrew/.linuxbrew/") ||
            normalized.contains("/Cellar/morphe/")
    }

    private fun isDirectoryWritable(dir: File): Boolean =
        dir.isDirectory && runCatching { Files.isWritable(dir.toPath()) && dir.canWrite() }.getOrDefault(false)

    /** Renames `morphe-desktop-*-all.jar` files to [newVersion]; overwrites symlinks and custom filenames in place. */
    fun resolveDestinationJar(currentJar: File, newVersion: String, isSymlink: Boolean): File {
        val clean = newVersion.normalizeVersion()
        return if (!isSymlink && VERSIONED_JAR_REGEX.matches(currentJar.name)) {
            File(currentJar.parentFile, "morphe-desktop-$clean-all.jar")
        } else {
            currentJar
        }
    }

    /** Downloads [info] into [updatesDir], retrying via the GitHub Releases API if the direct download fails. */
    suspend fun downloadToStaging(
        info: UpdateInfo,
        httpService: HttpService,
        gitHubPat: String? = null,
        updatesDir: File = MorpheData.updatesDir,
        onProgress: ((bytesRead: Long, contentLength: Long?) -> Unit)? = null,
    ): File = withContext(Dispatchers.IO) {
        updatesDir.mkdirs()
        val stagedFile = File(updatesDir, info.assetName)
        try {
            httpService.downloadToFile(
                url = info.assetDownloadUrl,
                saveLocation = stagedFile,
                onProgress = onProgress,
            )
            stagedFile
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            val apiAssetUrl = resolveAssetApiUrl(info.latestVersion, info.assetName, httpService, gitHubPat)
                ?: throw e
            Logger.info("Retrying desktop update download via GitHub API: $apiAssetUrl")
            httpService.downloadToFile(
                url = apiAssetUrl,
                saveLocation = stagedFile,
                onProgress = onProgress,
            ) {
                header(HttpHeaders.Accept, ContentType.Application.OctetStream.toString())
                gitHubPat?.takeIf { it.isNotBlank() }?.let {
                    header(HttpHeaders.Authorization, "Bearer $it")
                }
            }
            stagedFile
        }
    }

    private suspend fun resolveAssetApiUrl(
        version: String,
        preferredName: String,
        httpService: HttpService,
        gitHubPat: String?,
    ): String? {
        val clean = version.normalizeVersion()
        val releaseUrl = "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/tags/v$clean"
        return try {
            val release: GitHubReleaseDetail = httpService.request(releaseUrl) {
                header(HttpHeaders.Accept, "application/vnd.github+json")
                header("X-GitHub-Api-Version", "2022-11-28")
                gitHubPat?.takeIf { it.isNotBlank() }?.let {
                    header(HttpHeaders.Authorization, "Bearer $it")
                }
            }
            val jarAssets = release.assets.filter { it.name.endsWith(".jar", ignoreCase = true) }
            val picked = jarAssets.firstOrNull { it.name.equals(preferredName, ignoreCase = true) }
                ?: jarAssets.firstOrNull { it.name.endsWith("-all.jar", ignoreCase = true) }
                ?: jarAssets.firstOrNull()
            picked?.url?.takeIf { it.isNotBlank() }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Validates a downloaded JAR's cryptographic signature against [expectedSignerFingerprints],
     * bytecode compatibility, and bundled version before it can replace the running executable.
     * Deletes [file] if any check fails.
     */
    fun verifyUpdateJar(
        file: File,
        expectedVersion: String,
        expectedSignerFingerprints: Set<String>,
        currentJavaFeature: Int = Runtime.version().feature(),
    ) {
        try {
            if (expectedSignerFingerprints.isEmpty()) {
                throw SecurityException("Running JAR has no trusted signer certificates")
            }
            if (!file.exists() || file.length() < 4L) {
                throw IOException("Downloaded update file is missing or empty")
            }

            val header = ByteArray(4)
            file.inputStream().use { input ->
                if (input.read(header) != 4 ||
                    header[0] != 0x50.toByte() ||
                    header[1] != 0x4B.toByte() ||
                    header[2] != 0x03.toByte() ||
                    header[3] != 0x04.toByte()
                ) {
                    throw IOException("Downloaded update is not a valid JAR archive")
                }
            }

            JarFile(file, true).use { jar ->
                verifyJarSignatures(jar, expectedSignerFingerprints)

                val launcherEntry = jar.getJarEntry(LAUNCHER_CLASS_ENTRY)
                    ?: throw IOException("Downloaded JAR is missing $LAUNCHER_CLASS_ENTRY")

                val classHeader = jar.getInputStream(launcherEntry).use { it.readNBytes(8) }
                if (classHeader.size < 8) {
                    throw IOException("Corrupted class entry in downloaded JAR")
                }
                val magic = ((classHeader[0].toInt() and 0xFF) shl 24) or
                    ((classHeader[1].toInt() and 0xFF) shl 16) or
                    ((classHeader[2].toInt() and 0xFF) shl 8) or
                    (classHeader[3].toInt() and 0xFF)
                if (magic != CLASS_FILE_MAGIC) {
                    throw IOException("Invalid class header in downloaded JAR")
                }
                val majorVersion = ((classHeader[6].toInt() and 0xFF) shl 8) or
                    (classHeader[7].toInt() and 0xFF)
                val requiredJava = majorVersion - 44
                if (requiredJava > currentJavaFeature) {
                    throw IncompatibleJavaVersionException(
                        requiredJavaVersion = requiredJava,
                        currentJavaVersion = currentJavaFeature,
                        targetVersion = expectedVersion.normalizeVersion(),
                    )
                }

                val versionEntry = jar.getJarEntry(VERSION_PROPERTIES_ENTRY)
                    ?: throw IOException("Downloaded JAR is missing $VERSION_PROPERTIES_ENTRY")
                val bundledVersion = jar.getInputStream(versionEntry).use { stream ->
                    Properties().apply { load(stream) }.getProperty("version")?.normalizeVersion()
                }
                val expectedClean = expectedVersion.normalizeVersion()
                if (bundledVersion.isNullOrBlank() || bundledVersion != expectedClean) {
                    throw IOException(
                        "Downloaded JAR version mismatch: expected $expectedClean, found ${bundledVersion ?: "none"}"
                    )
                }
            }
        } catch (t: Throwable) {
            runCatching { if (file.exists()) file.delete() }
            throw t
        }
    }

    private fun verifyJarSignatures(jar: JarFile, expectedSignerFingerprints: Set<String>) {
        if (jar.manifest == null) {
            throw SecurityException("Downloaded JAR is not signed")
        }

        val buffer = ByteArray(8192)
        var verifiedEntries = 0
        val entries = jar.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (entry.isDirectory) continue

            // Streaming to EOF triggers JarVerifier digest checks and populates entry.codeSigners.
            jar.getInputStream(entry).use { input ->
                while (input.read(buffer) != -1) {
                    // Drain entry bytes.
                }
            }

            if (!isSignatureMetadataEntry(entry.name)) {
                val entrySigners = signerFingerprintsOf(entry.codeSigners)
                if (entrySigners.isEmpty()) {
                    throw SecurityException("Unsigned entry in downloaded JAR: ${entry.name}")
                }
                if (entrySigners != expectedSignerFingerprints) {
                    throw SecurityException("Downloaded JAR signature does not match the running application")
                }
                verifiedEntries++
            }
        }

        if (verifiedEntries == 0) {
            throw SecurityException("Downloaded JAR contains no signed entries")
        }
    }

    private fun isSignatureMetadataEntry(name: String): Boolean {
        val upper = name.uppercase(Locale.ROOT)
        if (upper == "META-INF/MANIFEST.MF") return true
        if (!upper.startsWith("META-INF/")) return false
        val subPath = upper.removePrefix("META-INF/")
        if ('/' in subPath) return false
        return subPath.endsWith(".SF") ||
            subPath.endsWith(".RSA") ||
            subPath.endsWith(".DSA") ||
            subPath.endsWith(".EC") ||
            subPath.startsWith("SIG-")
    }

    fun stageVerifiedUpdate(
        stagedFile: File,
        info: UpdateInfo,
        installation: AppInstallation.WritableJar,
        currentJavaFeature: Int = Runtime.version().feature(),
    ): StagedUpdate {
        verifyUpdateJar(stagedFile, info.latestVersion, installation.signerFingerprints, currentJavaFeature)
        val sha256 = FileChecksum.sha256(stagedFile)
        val destJar = resolveDestinationJar(installation.jarFile, info.latestVersion, installation.isSymlink)
        return StagedUpdate(
            stagedJar = stagedFile,
            oldJar = installation.jarFile,
            destinationJar = destJar,
            sha256 = sha256,
            version = info.latestVersion.normalizeVersion(),
        )
    }

    suspend fun downloadAndVerify(
        info: UpdateInfo,
        installation: AppInstallation.WritableJar,
        httpService: HttpService,
        gitHubPat: String? = null,
        updatesDir: File = MorpheData.updatesDir,
        onProgress: ((bytesRead: Long, contentLength: Long?) -> Unit)? = null,
    ): StagedUpdate {
        val stagedFile = downloadToStaging(info, httpService, gitHubPat, updatesDir, onProgress)
        return withContext(Dispatchers.IO) {
            stageVerifiedUpdate(stagedFile, info, installation)
        }
    }

    /**
     * Verifies [stagedJar] against [expectedSha256], moves it into [destJar], and removes [oldJar]
     * if renamed. Retries file moves/deletes briefly in case Windows still holds a handle on the JAR.
     */
    fun replaceJarAtomically(
        stagedJar: File,
        oldJar: File,
        destJar: File,
        expectedSha256: String,
    ) {
        val stagedPath = stagedJar.toPath()
        val oldParent = oldJar.parentFile?.canonicalFile
            ?: throw IOException("Invalid current JAR parent directory")
        val destParent = destJar.parentFile?.canonicalFile
            ?: throw IOException("Invalid destination JAR parent directory")

        require(destJar.name.endsWith(".jar", ignoreCase = true)) {
            "Destination must be a .jar file: ${destJar.name}"
        }
        require(destParent == oldParent) {
            "Destination directory mismatch: $destParent != $oldParent"
        }
        require(!Files.isSymbolicLink(stagedPath)) {
            "Refusing to read staged JAR from a symlink: $stagedJar"
        }
        require(!Files.isSymbolicLink(destJar.toPath())) {
            "Refusing to overwrite symlink destination: $destJar"
        }
        if (!stagedJar.exists()) {
            throw IOException("Staged update JAR not found: ${stagedJar.absolutePath}")
        }
        val stagedActualHash = FileChecksum.sha256(stagedJar)
        if (!stagedActualHash.equals(expectedSha256, ignoreCase = true)) {
            throw IOException("Staged JAR SHA-256 mismatch (expected $expectedSha256, got $stagedActualHash)")
        }

        val tmpPath = Files.createTempFile(destParent.toPath(), ".morphe-update-", ".tmp")
        try {
            Files.copy(stagedPath, tmpPath, StandardCopyOption.REPLACE_EXISTING)
            if (oldJar.canExecute()) {
                runCatching { tmpPath.toFile().setExecutable(true, false) }
            }

            // Re-check hash after copying into the target directory (mitigates TOCTOU).
            val tmpActualHash = FileChecksum.sha256(tmpPath.toFile())
            if (!tmpActualHash.equals(expectedSha256, ignoreCase = true)) {
                throw IOException("Temp update JAR SHA-256 mismatch after copy")
            }

            moveWithRetries(tmpPath, destJar.toPath())
        } catch (t: Throwable) {
            runCatching { Files.deleteIfExists(tmpPath) }
            throw t
        }

        val sameFile = runCatching { oldJar.canonicalFile == destJar.canonicalFile }.getOrDefault(false)
        if (!sameFile && oldJar.exists()) {
            deleteWithRetries(oldJar.toPath())
        }
        runCatching { Files.deleteIfExists(stagedPath) }
    }

    private fun moveWithRetries(source: Path, target: Path) {
        var lastError: Throwable? = null
        for (attempt in 1..MOVE_RETRY_ATTEMPTS) {
            lastError = try {
                try {
                    Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
                }
                return
            } catch (e: FileSystemException) {
                e
            }
            if (attempt < MOVE_RETRY_ATTEMPTS) {
                Thread.sleep(MOVE_RETRY_DELAY_MS)
            }
        }
        throw IOException("Could not replace ${target.fileName} after retries", lastError)
    }

    private fun deleteWithRetries(path: Path) {
        for (attempt in 1..10) {
            try {
                if (Files.deleteIfExists(path) || !Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                    return
                }
            } catch (_: IOException) {
            }
            if (attempt < 10) Thread.sleep(150L)
        }
        Logger.warn("Could not delete previous JAR: $path")
    }

    /**
     * Launches `app.morphe.engine.SelfUpdater` directly from the verified downloaded JAR and sends
     * the [ApplyPlan] JSON over `stdin` so the old process(es) can exit and release file locks
     * without passing CLI arguments or creating any helper JAR copy.
     */
    fun spawnApplyProcess(
        staged: StagedUpdate,
        waitPids: List<Long>,
        relaunchGui: Boolean,
    ) {
        val stagedPath = staged.stagedJar.toPath()
        require(!Files.isSymbolicLink(stagedPath) && staged.stagedJar.isFile) {
            "Refusing to run apply process from missing or symlinked JAR: ${staged.stagedJar}"
        }

        val javaBin = resolveJavaBinary(preferWindowless = relaunchGui || isWindows())
        val cmd = buildList {
            add(javaBin)
            if (isMac()) {
                add("-Dapple.awt.UIElement=true")
            }
            add("-cp")
            add(staged.stagedJar.absolutePath)
            add("app.morphe.engine.SelfUpdater")
        }

        val plan = ApplyPlan(
            source = staged.stagedJar.absolutePath,
            oldJar = staged.oldJar.absolutePath,
            destJar = staged.destinationJar.absolutePath,
            sha256 = staged.sha256,
            waitPids = waitPids.distinct(),
            relaunchGui = relaunchGui,
        )
        val payload = applyJson.encodeToString(ApplyPlan.serializer(), plan)

        val logFile = File(MorpheData.logsDir.also { it.mkdirs() }, "updater.log")
        val process = ProcessBuilder(cmd)
            .directory(staged.stagedJar.parentFile)
            .redirectInput(ProcessBuilder.Redirect.PIPE)
            .redirectErrorStream(true)
            .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))
            .start()
        process.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(payload)
        }
    }

    internal fun runApplyUpdateFromJson(payload: String): Int {
        val plan = runCatching { applyJson.decodeFromString<ApplyPlan>(payload) }.getOrNull()
        if (plan == null ||
            plan.source.isBlank() ||
            plan.oldJar.isBlank() ||
            plan.destJar.isBlank() ||
            plan.sha256.isBlank()
        ) {
            System.err.println("[${Instant.now()}] Invalid self-update plan received on stdin")
            return 1
        }

        val sourceFile = File(plan.source)
        val oldJarFile = File(plan.oldJar)
        val destJarFile = File(plan.destJar)

        waitForProcesses(plan.waitPids)

        return try {
            replaceJarAtomically(
                stagedJar = sourceFile,
                oldJar = oldJarFile,
                destJar = destJarFile,
                expectedSha256 = plan.sha256,
            )
            println("[${Instant.now()}] Updated JAR successfully: ${destJarFile.absolutePath}")
            if (plan.relaunchGui) {
                launchJarProcess(destJarFile)
            }
            0
        } catch (t: Throwable) {
            System.err.println("[${Instant.now()}] Failed to apply update: ${t.message}")
            t.printStackTrace(System.err)
            if (plan.relaunchGui && oldJarFile.exists()) {
                runCatching { launchJarProcess(oldJarFile) }
            }
            1
        }
    }

    private fun waitForProcesses(pids: List<Long>) {
        if (pids.isEmpty()) return
        val deadline = System.currentTimeMillis() + WAIT_PID_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val anyAlive = pids.any { pid ->
                ProcessHandle.of(pid).orElse(null)?.isAlive == true
            }
            if (!anyAlive) return
            Thread.sleep(WAIT_PID_POLL_MS)
        }
    }

    private fun launchJarProcess(jarFile: File) {
        val javaBin = resolveJavaBinary(preferWindowless = true)
        val cmd = listOf(javaBin, "-jar", jarFile.absolutePath)
        ProcessBuilder(cmd)
            .directory(jarFile.parentFile)
            .apply { environment().remove(LAUNCHER_PID_ENV) }
            .redirectInput(ProcessBuilder.Redirect.PIPE)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
            .also { process ->
                runCatching { process.outputStream.close() }
            }
    }

    /** Cleans leftover staged files in [updatesDir] without following symlinks and removes it if empty. */
    fun cleanupUpdatesDir(updatesDir: File = MorpheData.updatesDir, retryLocked: Boolean = false) {
        val rootPath = updatesDir.toPath()
        if (!Files.exists(rootPath, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(rootPath)) return

        val entries = runCatching { Files.list(rootPath).use { it.toList() } }.getOrNull() ?: return
        for (entry in entries) {
            if (Files.isSymbolicLink(entry) || Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) continue
            val name = entry.fileName.toString()
            if (name.endsWith(".jar", ignoreCase = true) ||
                name.endsWith(".part", ignoreCase = true) ||
                name.endsWith(".tmp", ignoreCase = true)
            ) {
                if (retryLocked) {
                    deleteWithRetries(entry)
                } else {
                    runCatching { Files.deleteIfExists(entry) }
                }
            }
        }

        val isEmpty = runCatching { Files.list(rootPath).use { !it.findFirst().isPresent } }.getOrDefault(false)
        if (isEmpty) {
            runCatching { Files.deleteIfExists(rootPath) }
        }
    }

    /** Cleans [updatesDir] asynchronously on startup so retrying locked staged JARs never delays launch. */
    fun cleanupUpdatesDirAsync(updatesDir: File = MorpheData.updatesDir) {
        Thread({
            runCatching { cleanupUpdatesDir(updatesDir, retryLocked = true) }
        }, "MorpheUpdateCleanup").apply {
            isDaemon = true
            start()
        }
    }

    internal fun resolveJavaBinary(preferWindowless: Boolean): String {
        val javaHome = System.getProperty("java.home")
        val binDir = File(javaHome, "bin")
        if (isWindows()) {
            if (preferWindowless) {
                val javaw = File(binDir, "javaw.exe")
                if (javaw.isFile) return javaw.absolutePath
            }
            val javaExe = File(binDir, "java.exe")
            if (javaExe.isFile) return javaExe.absolutePath
        }
        val unixJava = File(binDir, "java")
        return if (unixJava.isFile) unixJava.absolutePath else "java"
    }

    private fun isWindows(): Boolean =
        System.getProperty("os.name").orEmpty().lowercase().contains("windows")

    private fun isMac(): Boolean =
        System.getProperty("os.name").orEmpty().lowercase().contains("mac")
}
