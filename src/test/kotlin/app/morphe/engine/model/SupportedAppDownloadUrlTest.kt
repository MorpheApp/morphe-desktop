/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.model

import app.morphe.engine.MorpheConstants.MORPHE_API_URL
import app.morphe.patcher.patch.FormFactor
import app.morphe.patcher.patch.SupportedAbi
import kotlin.test.Test
import kotlin.test.assertEquals

class SupportedAppDownloadUrlTest {

    private fun app(
        abiCodes: Map<String, Map<SupportedAbi, Int>> = emptyMap(),
        formFactor: FormFactor? = null,
    ) = SupportedApp(
        packageName = "com.example",
        displayName = "Example",
        supportedVersions = listOf("1.2.3"),
        recommendedVersion = "1.2.3",
        versionAbiBuildCodes = abiCodes,
        formFactor = formFactor,
    )

    private fun url(query: String) = "$MORPHE_API_URL/v2/web-search/$query"

    @Test
    fun phoneAppWithoutBuildCodesKeepsThePlainQuery() {
        assertEquals(url("com.example~1.2.3~all"), app().downloadUrl("1.2.3"))
        assertEquals(url("com.example~1.2.3~all"), app(formFactor = FormFactor.MOBILE).downloadUrl("1.2.3"))
    }

    @Test
    fun theArm64BuildCodeIsSentForEveryArchitecture() {
        val codes = mapOf("1.2.3" to mapOf(SupportedAbi.ARMEABI_V7A to 44, SupportedAbi.ARM64_V8A to 45))
        assertEquals(url("com.example~1.2.3~all~45"), app(codes).downloadUrl("1.2.3"))
        val noArm64 = mapOf("1.2.3" to mapOf(SupportedAbi.X86 to 46))
        assertEquals(url("com.example~1.2.3~all~46"), app(noArm64).downloadUrl("1.2.3"))
        assertEquals(url("com.example~2.0~all"), app(codes).downloadUrl("2.0"), "codes of another version")
    }

    @Test
    fun anotherFormFactorIsNamedLast() {
        val codes = mapOf("1.2.3" to mapOf(SupportedAbi.ARM64_V8A to 45))
        assertEquals(url("com.example~1.2.3~all~45~tv"), app(codes, FormFactor.TV).downloadUrl("1.2.3"))
        assertEquals(url("com.example~any~all~wearable"), app(formFactor = FormFactor.WEARABLE).downloadUrl("any"))
    }
}
