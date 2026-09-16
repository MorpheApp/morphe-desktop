/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe

internal const val STABLE_WINDOWS_RENDER_API = "SOFTWARE_FAST"

/**
 * Chooses the application-wide Skiko renderer before AWT or Compose is initialized.
 *
 * Direct3D can briefly expose its white swap-chain while translucent Compose layers
 * are invalidated or scrolled. The software renderer avoids that Windows-specific
 * presentation path. Explicit operator overrides always win.
 */
internal fun preferredWindowsRenderApi(
    osName: String?,
    environmentOverride: String?,
    systemOverride: String?,
): String? {
    val isWindows = osName?.startsWith("Windows", ignoreCase = true) == true
    val hasExplicitOverride = !environmentOverride.isNullOrBlank() || !systemOverride.isNullOrBlank()
    return if (isWindows && !hasExplicitOverride) STABLE_WINDOWS_RENDER_API else null
}

internal fun configureStableWindowsRenderer() {
    preferredWindowsRenderApi(
        osName = System.getProperty("os.name"),
        environmentOverride = System.getenv("SKIKO_RENDER_API"),
        systemOverride = System.getProperty("skiko.renderApi"),
    )?.let { System.setProperty("skiko.renderApi", it) }
}
