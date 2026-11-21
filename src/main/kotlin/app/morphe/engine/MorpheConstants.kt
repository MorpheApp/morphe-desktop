/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine

object MorpheConstants {
    const val MORPHE_API_URL = "https://api.morphe.software"
    const val PEOPLE_URL = "$MORPHE_API_URL/v2/people"

    /** User agent of requests to [MORPHE_API_URL]. */
    val MORPHE_USER_AGENT: String by lazy {
        val version = UpdateChecker.currentVersion()?.takeIf { it.matches(Regex("[0-9A-Za-z.+-]{1,32}")) }
        "Morphe-Desktop/${version ?: "dev"}"
    }

    /**
     * Redirects /<package name> to the Play Store icon of the app, for apps with no icon from an
     * APK or a device. Found icons say to keep them for 30 days and missing apps for a day.
     */
    const val PLAY_STORE_ICON_URL = "https://publicmetadata.co.uk"

    val FALLBACK_PACKAGES = listOf(
        "com.google.android.youtube",
        "com.google.android.apps.youtube.music",
        "com.reddit.frontpage",
    )
}
