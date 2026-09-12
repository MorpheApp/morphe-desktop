package app.morphe.gui.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AdbInstalledApkHashTest {
    @Test
    fun `selects base apk from split package paths`() {
        val output = """
            package:/data/app/pkg/split_config.arm64_v8a.apk
            package:/data/app/pkg/base.apk
            package:/data/app/pkg/split_config.en.apk
        """.trimIndent()

        assertEquals("/data/app/pkg/base.apk", parseInstalledBaseApkPath(output))
    }

    @Test
    fun `accepts a single nonstandard apk path`() {
        assertEquals("/data/app/pkg/pkg.apk", parseInstalledBaseApkPath("package:/data/app/pkg/pkg.apk"))
    }

    @Test
    fun `rejects ambiguous paths without a base apk`() {
        assertNull(parseInstalledBaseApkPath("package:/a/one.apk\npackage:/a/two.apk"))
    }

    @Test
    fun `parses only a complete sha256sum digest`() {
        val hash = "A1".repeat(32)
        assertEquals(hash.lowercase(), parseSha256Sum("$hash  /data/app/pkg/base.apk"))
        assertNull(parseSha256Sum("permission denied"))
        assertNull(parseSha256Sum("abcd  /data/app/pkg/base.apk"))
    }

    @Test
    fun `parses package last update time for receipt invalidation`() {
        val dump = """
              firstInstallTime=2026-09-10 10:00:00
              lastUpdateTime=2026-09-11 18:03:02
        """.trimIndent()

        assertEquals("2026-09-11 18:03:02", parsePackageLastUpdateTime(dump))
        assertNull(parsePackageLastUpdateTime("versionName=10"))
    }
}
