/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.data.constants

import java.util.Properties

/**
 * Centralized configuration for supported apps.
 * This file is massively outdated. Could be used for other things in the future but kinda useless now.
 */
object AppConstants {

    // ==================== APP INFO ====================
    private val pkg by lazy { AppConstants::class.java.`package` }

    val APP_NAME: String by lazy {
        pkg?.implementationTitle ?: "Morphe GUI"
    }

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

    // ==================== YOUTUBE ====================
    object YouTube {
        const val DISPLAY_NAME = "YouTube"
        const val PACKAGE_NAME = "com.google.android.youtube"
    }

    // ==================== YOUTUBE MUSIC ====================
    object YouTubeMusic {
        const val DISPLAY_NAME = "YouTube Music"
        const val PACKAGE_NAME = "com.google.android.apps.youtube.music"
    }

    // ==================== REDDIT ====================
    object Reddit {
        const val DISPLAY_NAME = "Reddit"
        const val PACKAGE_NAME = "com.reddit.frontpage"
    }

    /**
     * List of all supported package names for quick lookup.
     */
    val SUPPORTED_PACKAGES = listOf(
        YouTube.PACKAGE_NAME,
        YouTubeMusic.PACKAGE_NAME,
        Reddit.PACKAGE_NAME
    )

    // TODO: Checksum verification will be re-enabled when checksums are added to .mpp files
    // For now, checksums are not validated. See ChecksumUtils.kt for the verification logic.
}
