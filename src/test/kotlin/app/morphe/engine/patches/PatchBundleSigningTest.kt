/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.patches

import app.morphe.engine.model.Release
import app.morphe.engine.model.ReleaseAsset
import app.morphe.patcher.patch.PatchBundleSignature
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.*
import kotlinx.coroutines.runBlocking

class PatchBundleSigningTest {
    private val repoPath = "test-owner/test-signing-repo"
    private val tempDir: File = Files.createTempDirectory("signing-test").toFile()

    @BeforeTest
    fun setUp() {
        PatchCache.sourceDir(repoPath).deleteRecursively()
    }

    @AfterTest
    fun tearDown() {
        PatchCache.sourceDir(repoPath).deleteRecursively()
        tempDir.deleteRecursively()
    }

    private fun createBundle(name: String, content: Int, privateKey: String? = null): File {
        val file = File(tempDir, "$name.mpp")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("classes.dex"))
            zip.write(ByteArray(512) { content.toByte() })
            zip.closeEntry()
        }
        if (privateKey != null) {
            PatchBundleSignature.sign(file, privateKey)
        }
        return file
    }

    private class MockRemoteSource(
        override val repoPath: String,
        val files: MutableMap<String, File> = mutableMapOf()
    ) : RemotePatchSource {
        override val provider: PatchProvider = PatchProvider.GITHUB
        override suspend fun listReleases(): Result<List<Release>> = Result.success(emptyList())
        override suspend fun fetchLatestFromManifest(prerelease: Boolean): Result<Release> =
            Result.failure(UnsupportedOperationException())

        override suspend fun downloadAsset(
            asset: ReleaseAsset,
            targetFile: File,
            onProgress: ((bytesRead: Long, contentLength: Long?) -> Unit)?
        ): Result<File> {
            val source = files[asset.name] ?: return Result.failure(Exception("File not found: ${asset.name}"))
            targetFile.parentFile?.mkdirs()
            source.copyTo(targetFile, overwrite = true)
            return Result.success(targetFile)
        }
    }

    private fun releaseWithAsset(tagName: String, assetName: String, file: File): Pair<Release, MockRemoteSource> {
        val asset = ReleaseAsset(
            name = assetName,
            downloadUrl = "https://example.com/$assetName",
            size = file.length()
        )
        val release = Release(
            tagName = tagName,
            name = tagName,
            assets = listOf(asset),
            publishedAt = "2026-10-26T10:00:00Z"
        )
        val remote = MockRemoteSource(repoPath, mutableMapOf(assetName to file))
        return release to remote
    }

    @Test
    fun `unsigned bundles are accepted while no key is trusted`() = runBlocking {
        val bundle = createBundle("b1", 1)
        val (release, remote) = releaseWithAsset("v1.0.0", "patches.mpp", bundle)
        val repo = PatchRepository(remote)

        val res = repo.downloadPatches(release)
        assertTrue(res.isSuccess)
        assertNull(repo.signingKey)
    }

    @Test
    fun `first signed bundle pins its signing key`() = runBlocking {
        val bundle = createBundle("b1", 1, KEY)
        val (release, remote) = releaseWithAsset("v1.0.0", "patches.mpp", bundle)
        val repo = PatchRepository(remote)

        val res = repo.downloadPatches(release)
        assertTrue(res.isSuccess)
        assertEquals(PUBLIC_KEY, repo.signingKey)
        assertEquals("5647 5aa7 5463 474c", repo.signingKeyFingerprint)
    }

    @Test
    fun `bundle with different key is refused with PatchBundleSignerChangedException`() = runBlocking {
        val bundle1 = createBundle("b1", 1, KEY)
        val (release1, remote1) = releaseWithAsset("v1.0.0", "patches-1.mpp", bundle1)
        val repo = PatchRepository(remote1)

        val res1 = repo.downloadPatches(release1)
        assertTrue(res1.isSuccess)

        val bundle2 = createBundle("b2", 2, OTHER_KEY)
        val (release2, remote2) = releaseWithAsset("v2.0.0", "patches-2.mpp", bundle2)
        val repo2 = PatchRepository(remote2)

        val res2 = repo2.downloadPatches(release2)
        assertTrue(res2.isFailure)
        val ex = assertIs<PatchBundleSignerChangedException>(res2.exceptionOrNull())
        assertEquals(OTHER_PUBLIC_KEY, ex.signer)
        assertEquals("fe81 2c12 f3ab 4ce6", ex.signerFingerprint)
    }

    @Test
    fun `refused release is cached without re-downloading`() = runBlocking {
        val bundle1 = createBundle("b1", 1, KEY)
        val (release1, remote1) = releaseWithAsset("v1.0.0", "patches-1.mpp", bundle1)
        val repo = PatchRepository(remote1)
        repo.downloadPatches(release1)

        val bundle2 = createBundle("b2", 2, OTHER_KEY)
        var downloadCount = 0
        val trackingRemote = object : RemotePatchSource by remote1 {
            override suspend fun downloadAsset(
                asset: ReleaseAsset,
                targetFile: File,
                onProgress: ((bytesRead: Long, contentLength: Long?) -> Unit)?
            ): Result<File> {
                downloadCount++
                targetFile.parentFile?.mkdirs()
                bundle2.copyTo(targetFile, overwrite = true)
                return Result.success(targetFile)
            }
        }
        val (release2, _) = releaseWithAsset("v2.0.0", "patches-2.mpp", bundle2)
        val repo2 = PatchRepository(trackingRemote)

        // First attempt downloads and records refusal
        val firstResult = repo2.downloadPatches(release2)
        assertTrue(firstResult.isFailure)
        assertEquals(1, downloadCount)

        // Second attempt fails immediately from cache without calling downloadAsset
        val secondResult = repo2.downloadPatches(release2)
        assertTrue(secondResult.isFailure)
        val ex = assertIs<PatchBundleSignerChangedException>(secondResult.exceptionOrNull())
        assertEquals(OTHER_PUBLIC_KEY, ex.signer)
        assertEquals(1, downloadCount, "Should not re-download a release already refused for its signer")
    }

    @Test
    fun `trusting new key allows installing release of that key`() = runBlocking {
        val bundle1 = createBundle("b1", 1, KEY)
        val (release1, remote1) = releaseWithAsset("v1.0.0", "patches-1.mpp", bundle1)
        val repo = PatchRepository(remote1)
        repo.downloadPatches(release1)

        val bundle2 = createBundle("b2", 2, OTHER_KEY)
        val (release2, remote2) = releaseWithAsset("v2.0.0", "patches-2.mpp", bundle2)
        val repo2 = PatchRepository(remote2)

        val failed = repo2.downloadPatches(release2)
        assertTrue(failed.isFailure)

        // Trust the new key
        repo2.trustSigningKey(OTHER_PUBLIC_KEY)
        assertEquals(OTHER_PUBLIC_KEY, repo2.signingKey)

        // Retry download
        val retry = repo2.downloadPatches(release2)
        assertTrue(retry.isSuccess)
    }

    @Test
    fun `tampered bundle throws SecurityException`() {
        runBlocking {
            val bundle = createBundle("tampered", 1, KEY)
            val bytes = bundle.readBytes()
            bytes[50] = (bytes[50].toInt() xor 0xFF).toByte()
            bundle.writeBytes(bytes)

            val (release, remote) = releaseWithAsset("v1.0.0", "tampered.mpp", bundle)
            val repo = PatchRepository(remote)

            val res = repo.downloadPatches(release)
            assertTrue(res.isFailure)
            assertIs<SecurityException>(res.exceptionOrNull())
        }
    }

    @Test
    fun `cached bundle with mismatched or missing signer throws PatchBundleSignerChangedException`() = runBlocking {
        // First bundle is signed and pins KEY
        val bundle1 = createBundle("b1", 1, KEY)
        val (release1, remote1) = releaseWithAsset("v1.0.0", "patches-1.mpp", bundle1)
        val repo = PatchRepository(remote1)
        repo.downloadPatches(release1)
        assertEquals(PUBLIC_KEY, repo.signingKey)

        // Second bundle is an unsigned bundle (like an older release) that was already cached on disk
        val bundle2 = createBundle("b2", 2) // unsigned
        val (release2, remote2) = releaseWithAsset("v2.0.0", "patches-2.mpp", bundle2)
        val asset2 = release2.assets.first()
        val cachedTarget = PatchCache.cachedFile(repoPath, release2, asset2)
        cachedTarget.parentFile?.mkdirs()
        bundle2.copyTo(cachedTarget, overwrite = true)
        assertTrue(PatchCache.getCachedFile(repoPath, release2) != null, "Cached file must exist in cache")

        val repo2 = PatchRepository(remote2)
        val res = repo2.downloadPatches(release2)
        assertTrue(res.isFailure, "Cached unsigned bundle must not be trusted when a key is pinned")
        val ex = assertIs<PatchBundleSignerChangedException>(res.exceptionOrNull())
        assertNull(ex.signer)
    }

    companion object {
        const val KEY = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="
        const val PUBLIC_KEY = "A6EHv/POEL4dcN0Y50vAmWfk1jCbpQ1fHdyGZBJVMbg="
        const val OTHER_KEY = "BwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwc="
        const val OTHER_PUBLIC_KEY = "6kpsY+KcUgq+9VB7Ey7F+ZVHdq6+vnuSQh7qaRRG0iw="
    }
}
