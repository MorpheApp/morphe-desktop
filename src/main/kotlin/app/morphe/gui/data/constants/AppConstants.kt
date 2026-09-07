/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.data.constants

import java.util.Properties

/**
 * Centralized configuration for supported apps.
 */
object AppConstants {

    // ==================== APP INFO ====================
    private val pkg by lazy { AppConstants::class.java.`package` }

    val APP_VERSION: String by lazy {
        val resourceVersion = AppConstants::class.java
            .getResourceAsStream("/app/morphe/cli/version.properties")
            ?.use { stream ->
                Properties().apply { load(stream) }.getProperty("version")
            }

        val resolvedVersion = resourceVersion ?: pkg?.implementationVersion
        resolvedVersion?.let { "v$it" } ?: "dev"
    }

    // ==================== API ====================
    const val MORPHE_API_URL = "https://api.morphe.software"

    const val WEBSITE_URL = "https://morphe.software"
    const val DOCUMENTATION_URL = "https://github.com/MorpheApp/morphe-desktop/blob/main/docs/documentation.md"

    val FALLBACK_PACKAGES = listOf(
        "com.google.android.youtube",
        "com.google.android.apps.youtube.music",
        "com.reddit.frontpage",
    )

    // TODO: Checksum verification will be re-enabled when checksums are added to .mpp files
    // For now, checksums are not validated. See ChecksumUtils.kt for the verification logic.
}
