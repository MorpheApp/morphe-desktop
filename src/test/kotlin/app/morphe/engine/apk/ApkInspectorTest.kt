/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.apk

import com.reandroid.arsc.chunk.TableBlock
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ApkInspectorTest {

    private val tmpDir: File = Files.createTempDirectory("morphe-inspector-test").toFile()

    @AfterTest
    fun cleanup() {
        tmpDir.deleteRecursively()
    }

    @Test
    fun `reads literal application label`() {
        val manifest = AndroidManifestBlock().apply {
            packageName = "com.example.literal"
            setApplicationLabel("Literal App")
            refresh()
        }
        val apk = writeZip("literal.apk", listOf(AndroidManifestBlock.FILE_NAME to manifest.bytes))

        assertEquals("Literal App", ApkInspector.inspect(apk)?.applicationLabel)
    }

    @Test
    fun `resolves string resource label in APK`() {
        val (manifest, table) = referencedLabel("com.lemon.lvoverseas", "CapCut")
        val apk = writeZip(
            "capcut.apk",
            listOf(AndroidManifestBlock.FILE_NAME to manifest, TableBlock.FILE_NAME to table),
        )

        assertEquals("CapCut", ApkInspector.inspect(apk)?.applicationLabel)
    }

    @Test
    fun `resolves string resource label in bundle`() {
        val (manifest, table) = referencedLabel("com.zhiliaoapp.musically", "TikTok")
        val baseApk = zipBytes(listOf(AndroidManifestBlock.FILE_NAME to manifest, TableBlock.FILE_NAME to table))
        val bundle = writeZip("tiktok.apkm", listOf("base.apk" to baseApk))

        assertEquals("TikTok", ApkInspector.inspect(bundle)?.applicationLabel)
    }

    /** Returns manifest and resources table bytes where the label is `@string/app_name` = [label]. */
    private fun referencedLabel(packageName: String, label: String): Pair<ByteArray, ByteArray> {
        val table = TableBlock()
        val labelEntry = table.newPackage(0x7f, packageName).getOrCreate("", "string", "app_name")
        labelEntry.setDataAsString(label)
        table.refresh()

        val manifest = AndroidManifestBlock().apply {
            this.packageName = packageName
            setApplicationLabel(labelEntry.resourceId)
            refresh()
        }
        return manifest.bytes to table.bytes
    }

    private fun writeZip(name: String, entries: List<Pair<String, ByteArray>>): File =
        File(tmpDir, name).apply { writeBytes(zipBytes(entries)) }

    private fun zipBytes(entries: List<Pair<String, ByteArray>>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((entryName, bytes) in entries) {
                zip.putNextEntry(ZipEntry(entryName))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
