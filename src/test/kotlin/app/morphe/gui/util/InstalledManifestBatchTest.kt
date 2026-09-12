package app.morphe.gui.util

import kotlin.test.*

class InstalledManifestBatchTest {
    @Test
    fun `binary payload and unavailable item retain exact association`() {
        val payload = byteArrayOf(0, 10, 58, -1, 1)
        val wire = "M1:3\n0:5\n".toByteArray() + payload + "1:-1\n2:2\n".toByteArray() + byteArrayOf(10, 0)
        val decoded = InstalledManifestBatch.decode(DeviceEntryResult(0, wire, ""), 3)!!
        assertContentEquals(payload, decoded[0].stdout)
        assertEquals(1, decoded[1].exitCode)
        assertContentEquals(byteArrayOf(10, 0), decoded[2].stdout)
    }

    @Test
    fun `malformed batches fail all entries without fallback or cross assignment`() {
        listOf("", "M1:2\n0:1\na", "M1:2\n1:1\na0:1\nb", "M1:2\n0:-2\n1:-1\n",
            "M1:2\n0:99999999\na1:-1\n", "M1:2\n0:-1\n1:-1\nEXTRA", "M1:1\n0:-1\n").forEach {
            val decoded = InstalledManifestBatch.decode(DeviceEntryResult(0, it.toByteArray(), ""), 2)!!
            assertEquals(2, decoded.size)
            assertTrue(decoded.all { it.exitCode != 0 && it.stdout.isEmpty() })
        }
        assertNull(InstalledManifestBatch.decode(DeviceEntryResult(0, "U\n".toByteArray(), ""), 2))
        assertNotNull(InstalledManifestBatch.decode(DeviceEntryResult(1, "U\n".toByteArray(), "offline"), 2))
    }

    @Test
    fun `batch command bounds and quotes paths before dispatch`() {
        val command = InstalledManifestBatch.command(listOf("/data/app/~~abc==/app.pkg/base.apk"))
        assertTrue(command.contains("read_manifest 0 '/data/app/~~abc==/app.pkg/base.apk'"))
        assertTrue(command.contains("unzip -p"))
        assertFailsWith<IllegalArgumentException> { InstalledManifestBatch.command(emptyList()) }
        assertFailsWith<IllegalArgumentException> { InstalledManifestBatch.command(List(9) { "/data/app/a.apk" }) }
        assertFailsWith<IllegalArgumentException> { InstalledManifestBatch.command(listOf("/data/app/a';bad.apk")) }
    }
}
