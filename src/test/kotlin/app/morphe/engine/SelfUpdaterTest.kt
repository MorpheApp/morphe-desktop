/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine

import app.morphe.engine.SelfUpdater.AppInstallation
import app.morphe.engine.util.FileChecksum
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.security.MessageDigest
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.io.TempDir

class SelfUpdaterTest {

    @Test
    fun `resolveDestinationJar renames versioned stable and dev jars`() {
        val dir = File("/tmp/morphe-test")
        val oldStable = File(dir, "morphe-desktop-2.4.0-all.jar")
        assertEquals(
            File(dir, "morphe-desktop-2.5.0-all.jar"),
            SelfUpdater.resolveDestinationJar(oldStable, "v2.5.0", isSymlink = false),
        )

        val oldDev = File(dir, "morphe-desktop-2.5.0-dev.1-all.jar")
        assertEquals(
            File(dir, "morphe-desktop-2.5.0-dev.2-all.jar"),
            SelfUpdater.resolveDestinationJar(oldDev, "2.5.0-dev.2", isSymlink = false),
        )
    }

    @Test
    fun `resolveDestinationJar keeps custom names and symlinked jars in place`() {
        val dir = File("/tmp/morphe-test")
        val customJar = File(dir, "morphe-desktop.jar")
        assertEquals(
            customJar,
            SelfUpdater.resolveDestinationJar(customJar, "2.5.0", isSymlink = false),
        )

        val versionedSymlinkTarget = File(dir, "morphe-desktop-2.4.0-all.jar")
        assertEquals(
            versionedSymlinkTarget,
            SelfUpdater.resolveDestinationJar(versionedSymlinkTarget, "2.5.0", isSymlink = true),
        )
    }

    @Test
    fun `detectInstallation classifies writable, unsigned, readonly, homebrew, and non-jar`(@TempDir tempDir: Path) {
        val jar = Files.createFile(tempDir.resolve("morphe-desktop-2.4.0-all.jar")).toFile()
        val testSigners = setOf("abc123")

        val writable = SelfUpdater.detectInstallation(
            codeSourceLocation = jar.toURI().toURL(),
            isDirWritable = { true },
            runningSignerFingerprints = { testSigners },
        )
        val writableJar = assertIs<AppInstallation.WritableJar>(writable)
        assertEquals(jar.canonicalFile, writableJar.jarFile)
        assertEquals(testSigners, writableJar.signerFingerprints)
        assertFalse(writableJar.isSymlink)

        val unsigned = SelfUpdater.detectInstallation(
            codeSourceLocation = jar.toURI().toURL(),
            isDirWritable = { true },
            runningSignerFingerprints = { emptySet() },
        )
        assertIs<AppInstallation.UnsignedJar>(unsigned)

        val readOnly = SelfUpdater.detectInstallation(
            codeSourceLocation = jar.toURI().toURL(),
            isDirWritable = { false },
            runningSignerFingerprints = { testSigners },
        )
        assertIs<AppInstallation.ReadOnlyJar>(readOnly)

        val classesDir = Files.createDirectory(tempDir.resolve("classes")).toFile()
        assertIs<AppInstallation.NotAJar>(SelfUpdater.detectInstallation(classesDir.toURI().toURL()))
        assertIs<AppInstallation.NotAJar>(SelfUpdater.detectInstallation(null))

        assertTrue(SelfUpdater.isHomebrewPath("/opt/homebrew/Cellar/morphe/2.4.0/libexec/morphe.jar"))
        assertTrue(SelfUpdater.isHomebrewPath("/usr/local/Cellar/morphe/2.4.0/libexec/morphe.jar"))
        assertTrue(SelfUpdater.isHomebrewPath("/home/linuxbrew/.linuxbrew/Cellar/morphe/2.4.0/libexec/morphe.jar"))
        assertFalse(SelfUpdater.isHomebrewPath("/home/deck/Apps/morphe-desktop-2.4.0-all.jar"))
    }

    @Test
    fun `verifyUpdateJar accepts valid signed jar and rejects unsigned, tampered, partial, or foreign-signed jars`(
        @TempDir tempDir: Path,
    ) {
        val keystoreA = tempDir.resolve("keystore-a.jks").toFile()
        val fpA = createTestKeystore(keystoreA, "storepassA", "morphe-a")
        val keystoreB = tempDir.resolve("keystore-b.jks").toFile()
        createTestKeystore(keystoreB, "storepassB", "morphe-b")

        // Valid signed JAR matching signer A
        val validSignedJar = tempDir.resolve("valid-signed.jar").toFile()
        writeSyntheticMorpheJar(validSignedJar, version = "2.5.0", javaMajorVersion = 65)
        signJarWithJarsigner(validSignedJar, keystoreA, "storepassA", "morphe-a")
        SelfUpdater.verifyUpdateJar(
            file = validSignedJar,
            expectedVersion = "2.5.0",
            expectedSignerFingerprints = setOf(fpA),
            currentJavaFeature = 21,
        )
        assertTrue(validSignedJar.exists())

        // Unsigned JAR rejected
        val unsignedJar = tempDir.resolve("unsigned.jar").toFile()
        writeSyntheticMorpheJar(unsignedJar, version = "2.5.0", javaMajorVersion = 65)
        assertFailsWith<SecurityException> {
            SelfUpdater.verifyUpdateJar(unsignedJar, "2.5.0", setOf(fpA), currentJavaFeature = 21)
        }
        assertFalse(unsignedJar.exists())

        // Signed JAR with modified entry rejected
        val tamperedJar = tempDir.resolve("tampered.jar").toFile()
        validSignedJar.copyTo(tamperedJar, overwrite = true)
        putJarEntry(tamperedJar, "app/morphe/cli/version.properties", "version=9.9.9\n".toByteArray())
        assertFailsWith<SecurityException> {
            SelfUpdater.verifyUpdateJar(tamperedJar, "2.5.0", setOf(fpA), currentJavaFeature = 21)
        }
        assertFalse(tamperedJar.exists())

        // Signed JAR with injected unsigned entry rejected
        val partialJar = tempDir.resolve("partial.jar").toFile()
        validSignedJar.copyTo(partialJar, overwrite = true)
        putJarEntry(partialJar, "injected/Payload.class", byteArrayOf(1, 2, 3, 4))
        assertFailsWith<SecurityException> {
            SelfUpdater.verifyUpdateJar(partialJar, "2.5.0", setOf(fpA), currentJavaFeature = 21)
        }
        assertFalse(partialJar.exists())

        // Signed with a different key (B instead of A) rejected
        val foreignJar = tempDir.resolve("foreign-signed.jar").toFile()
        writeSyntheticMorpheJar(foreignJar, version = "2.5.0", javaMajorVersion = 65)
        signJarWithJarsigner(foreignJar, keystoreB, "storepassB", "morphe-b")
        assertFailsWith<SecurityException> {
            SelfUpdater.verifyUpdateJar(foreignJar, "2.5.0", setOf(fpA), currentJavaFeature = 21)
        }
        assertFalse(foreignJar.exists())
    }

    @Test
    fun `verifyUpdateJar rejects corrupt, missing launcher, future java, and wrong version signed jars`(
        @TempDir tempDir: Path,
    ) {
        val keystore = tempDir.resolve("keystore.jks").toFile()
        val fp = createTestKeystore(keystore, "storepass", "morphe")

        val htmlFile = tempDir.resolve("error.jar").toFile().apply {
            writeText("<html><body>502 Bad Gateway</body></html>")
        }
        assertFailsWith<IOException> {
            SelfUpdater.verifyUpdateJar(htmlFile, "2.5.0", setOf(fp), currentJavaFeature = 21)
        }
        assertFalse(htmlFile.exists())

        val truncatedZip = tempDir.resolve("truncated.jar").toFile().apply {
            writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00))
        }
        assertFailsWith<IOException> {
            SelfUpdater.verifyUpdateJar(truncatedZip, "2.5.0", setOf(fp), currentJavaFeature = 21)
        }
        assertFalse(truncatedZip.exists())

        val missingLauncherJar = tempDir.resolve("no-launcher.jar").toFile()
        writeSyntheticMorpheJar(missingLauncherJar, version = "2.5.0", includeLauncherClass = false)
        signJarWithJarsigner(missingLauncherJar, keystore, "storepass", "morphe")
        assertFailsWith<IOException> {
            SelfUpdater.verifyUpdateJar(missingLauncherJar, "2.5.0", setOf(fp), currentJavaFeature = 21)
        }
        assertFalse(missingLauncherJar.exists())

        val futureJavaJar = tempDir.resolve("java26.jar").toFile()
        writeSyntheticMorpheJar(futureJavaJar, version = "2.5.0", javaMajorVersion = 70)
        signJarWithJarsigner(futureJavaJar, keystore, "storepass", "morphe")
        val javaEx = assertFailsWith<SelfUpdater.IncompatibleJavaVersionException> {
            SelfUpdater.verifyUpdateJar(futureJavaJar, "2.5.0", setOf(fp), currentJavaFeature = 21)
        }
        assertEquals(26, javaEx.requiredJavaVersion)
        assertEquals(21, javaEx.currentJavaVersion)
        assertFalse(futureJavaJar.exists())

        val wrongVersionJar = tempDir.resolve("wrong-version.jar").toFile()
        writeSyntheticMorpheJar(wrongVersionJar, version = "2.4.0", javaMajorVersion = 65)
        signJarWithJarsigner(wrongVersionJar, keystore, "storepass", "morphe")
        assertFailsWith<IOException> {
            SelfUpdater.verifyUpdateJar(wrongVersionJar, "2.5.0", setOf(fp), currentJavaFeature = 21)
        }
        assertFalse(wrongVersionJar.exists())
    }

    @Test
    fun `replaceJarAtomically supports in-place overwrite, smart rename, and rejects sha mismatch`(@TempDir tempDir: Path) {
        val installDir = Files.createDirectory(tempDir.resolve("install")).toFile()
        val stagingDir = Files.createDirectory(tempDir.resolve("updates")).toFile()

        // Smart rename: old versioned JAR deleted, new versioned JAR written
        val oldJar = File(installDir, "morphe-desktop-2.4.0-all.jar").apply { writeText("old-v240") }
        val newDestJar = File(installDir, "morphe-desktop-2.5.0-all.jar")
        val staged1 = File(stagingDir, "morphe-desktop-2.5.0-all.jar")
        writeSyntheticMorpheJar(staged1, version = "2.5.0")
        val sha1 = FileChecksum.sha256(staged1)

        SelfUpdater.replaceJarAtomically(staged1, oldJar, newDestJar, sha1)
        assertFalse(oldJar.exists())
        assertTrue(newDestJar.exists())
        assertEquals(sha1, FileChecksum.sha256(newDestJar))
        assertFalse(staged1.exists())

        // In-place overwrite
        val customJar = File(installDir, "morphe-desktop.jar").apply { writeText("old-custom") }
        val staged2 = File(stagingDir, "morphe-desktop-2.5.1-all.jar")
        writeSyntheticMorpheJar(staged2, version = "2.5.1")
        val sha2 = FileChecksum.sha256(staged2)

        SelfUpdater.replaceJarAtomically(staged2, customJar, customJar, sha2)
        assertTrue(customJar.exists())
        assertEquals(sha2, FileChecksum.sha256(customJar))

        // SHA-256 mismatch leaves original intact
        val staged3 = File(stagingDir, "morphe-desktop-2.5.2-all.jar")
        writeSyntheticMorpheJar(staged3, version = "2.5.2")
        assertFailsWith<IOException> {
            SelfUpdater.replaceJarAtomically(staged3, customJar, customJar, "0".repeat(64))
        }
        assertEquals(sha2, FileChecksum.sha256(customJar))
    }

    @Test
    fun `replaceJarAtomically rejects cross-directory destination`(@TempDir tempDir: Path) {
        val dirA = Files.createDirectory(tempDir.resolve("a")).toFile()
        val dirB = Files.createDirectory(tempDir.resolve("b")).toFile()
        val oldJar = File(dirA, "morphe-desktop.jar").apply { writeText("old") }
        val badDest = File(dirB, "morphe-desktop.jar")
        val staged = File(tempDir.toFile(), "staged.jar").apply { writeText("staged") }
        val sha = FileChecksum.sha256(staged)

        assertFailsWith<IllegalArgumentException> {
            SelfUpdater.replaceJarAtomically(staged, oldJar, badDest, sha)
        }
    }

    @Test
    fun `runApplyUpdateFromJson applies replacement end-to-end`(@TempDir tempDir: Path) {
        val installDir = Files.createDirectory(tempDir.resolve("app")).toFile()
        val oldJar = File(installDir, "morphe-desktop-2.4.0-all.jar").apply { writeText("old") }
        val destJar = File(installDir, "morphe-desktop-2.5.0-all.jar")
        val staged = File(tempDir.toFile(), "staged.jar")
        writeSyntheticMorpheJar(staged, version = "2.5.0")
        val sha = FileChecksum.sha256(staged)

        val planJson = Json.encodeToString(
            SelfUpdater.ApplyPlan.serializer(),
            SelfUpdater.ApplyPlan(
                source = staged.absolutePath,
                oldJar = oldJar.absolutePath,
                destJar = destJar.absolutePath,
                sha256 = sha,
            ),
        )
        val exitCode = SelfUpdater.runApplyUpdateFromJson(planJson)
        assertEquals(0, exitCode)
        assertTrue(destJar.exists())
        assertEquals(sha, FileChecksum.sha256(destJar))
        assertFalse(oldJar.exists())
    }

    @Test
    fun `runApplyUpdateFromJson rejects invalid or blank payload`() {
        assertEquals(1, SelfUpdater.runApplyUpdateFromJson(""))
        assertEquals(1, SelfUpdater.runApplyUpdateFromJson("{not-json"))

        val blankHashJson = Json.encodeToString(
            SelfUpdater.ApplyPlan.serializer(),
            SelfUpdater.ApplyPlan(
                source = "/tmp/source.jar",
                oldJar = "/tmp/old.jar",
                destJar = "/tmp/dest.jar",
                sha256 = "   ",
            ),
        )
        assertEquals(1, SelfUpdater.runApplyUpdateFromJson(blankHashJson))
    }

    @Test
    fun `cleanupUpdatesDir removes leftover files, removes empty directory, and skips symlinks`(@TempDir tempDir: Path) {
        val updatesDir = Files.createDirectory(tempDir.resolve("updates")).toFile()
        val externalDir = Files.createDirectory(tempDir.resolve("external")).toFile()
        val externalFile = File(externalDir, "keep-me.txt").apply { writeText("important") }

        File(updatesDir, "morphe-desktop-2.5.0-all.jar").writeText("staged")
        File(updatesDir, "morphe-desktop-2.5.0-all.jar.part").writeText("partial")
        val symlink = updatesDir.toPath().resolve("link-to-external")
        Files.createSymbolicLink(symlink, externalDir.toPath())

        SelfUpdater.cleanupUpdatesDir(updatesDir)
        assertFalse(File(updatesDir, "morphe-desktop-2.5.0-all.jar").exists())
        assertFalse(File(updatesDir, "morphe-desktop-2.5.0-all.jar.part").exists())
        assertTrue(externalFile.exists())
        // Symlink kept updatesDir non-empty; removing symlink allows directory cleanup.
        Files.delete(symlink)
        SelfUpdater.cleanupUpdatesDir(updatesDir)
        assertFalse(updatesDir.exists())
    }

    private fun writeSyntheticMorpheJar(
        target: File,
        version: String,
        javaMajorVersion: Int = 65,
        includeLauncherClass: Boolean = true,
    ) {
        JarOutputStream(target.outputStream()).use { jar ->
            if (includeLauncherClass) {
                jar.putNextEntry(JarEntry("app/morphe/MorpheLauncherKt.class"))
                jar.write(
                    byteArrayOf(
                        0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte(),
                        0x00, 0x00,
                        ((javaMajorVersion ushr 8) and 0xFF).toByte(),
                        (javaMajorVersion and 0xFF).toByte(),
                    )
                )
                jar.closeEntry()
            }
            jar.putNextEntry(JarEntry("app/morphe/cli/version.properties"))
            jar.write("version=$version\n".toByteArray())
            jar.closeEntry()
        }
    }

    private fun putJarEntry(jarFile: File, entryName: String, bytes: ByteArray) {
        val tempFile = File(jarFile.parentFile, "${jarFile.name}.rewrite")
        JarFile(jarFile).use { source ->
            JarOutputStream(tempFile.outputStream()).use { out ->
                var replaced = false
                val entries = source.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    out.putNextEntry(JarEntry(entry.name))
                    if (entry.name == entryName) {
                        out.write(bytes)
                        replaced = true
                    } else {
                        source.getInputStream(entry).use { it.copyTo(out) }
                    }
                    out.closeEntry()
                }
                if (!replaced) {
                    out.putNextEntry(JarEntry(entryName))
                    out.write(bytes)
                    out.closeEntry()
                }
            }
        }
        tempFile.copyTo(jarFile, overwrite = true)
        tempFile.delete()
    }

    private fun createTestKeystore(keystoreFile: File, password: String, alias: String): String {
        val keytool = jdkBinary("keytool")
        val proc = ProcessBuilder(
            keytool,
            "-genkeypair",
            "-alias", alias,
            "-keyalg", "RSA",
            "-keysize", "2048",
            "-validity", "1",
            "-storetype", "PKCS12",
            "-keystore", keystoreFile.absolutePath,
            "-storepass", password,
            "-keypass", password,
            "-dname", "CN=Morphe Test",
        ).redirectErrorStream(true).start()
        val output = proc.inputStream.bufferedReader().readText()
        check(proc.waitFor() == 0) { "keytool failed: $output" }

        val ks = KeyStore.getInstance(keystoreFile, password.toCharArray())
        val cert = ks.getCertificate(alias)
        return MessageDigest.getInstance("SHA-256")
            .digest(cert.encoded)
            .joinToString("") { "%02x".format(it) }
    }

    private fun signJarWithJarsigner(jarFile: File, keystoreFile: File, password: String, alias: String) {
        val jarsigner = jdkBinary("jarsigner")
        val proc = ProcessBuilder(
            jarsigner,
            "-keystore", keystoreFile.absolutePath,
            "-storepass", password,
            "-keypass", password,
            "-digestalg", "SHA-256",
            jarFile.absolutePath,
            alias,
        ).redirectErrorStream(true).start()
        val output = proc.inputStream.bufferedReader().readText()
        check(proc.waitFor() == 0) { "jarsigner failed: $output" }
    }

    private fun jdkBinary(name: String): String {
        val binDir = File(System.getProperty("java.home"), "bin")
        val exe = File(binDir, "$name.exe")
        if (exe.isFile) return exe.absolutePath
        val unix = File(binDir, name)
        if (unix.isFile) return unix.absolutePath
        return name
    }
}
