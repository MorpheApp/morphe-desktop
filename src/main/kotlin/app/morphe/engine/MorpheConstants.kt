/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine

object MorpheConstants {
    const val MORPHE_API_URL = "https://api.morphe.software"

    /** User agent of requests to [MORPHE_API_URL]. */
    val MORPHE_USER_AGENT: String by lazy {
        val version = UpdateChecker.currentVersion()?.takeIf { it.matches(Regex("[0-9A-Za-z.+-]{1,32}")) }
        "Morphe-Desktop/${version ?: "dev"}"
    }

    val FALLBACK_PACKAGES = listOf(
        "com.google.android.youtube",
        "com.google.android.apps.youtube.music",
        "com.reddit.frontpage",
    )
}
