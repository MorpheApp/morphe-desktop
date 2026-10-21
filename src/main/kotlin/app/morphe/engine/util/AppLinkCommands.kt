/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.util

/** Builds shell command arguments to configure Android app link handling. */
object AppLinkCommands {

    /** Primary Android user ID. */
    const val DEFAULT_USER = "0"

    /** Enables link handling for all web domains declared by [patchedPackage]. */
    fun enablePatched(patchedPackage: String, user: String = DEFAULT_USER): List<List<String>> = listOf(
        listOf("pm", "set-app-links-allowed", "--user", user, "--package", patchedPackage, "true"),
        listOf("pm", "set-app-links-user-selection", "--user", user, "--package", patchedPackage, "true", "all"),
    )

    /** Disables web link handling on the unpatched [stockPackage]. */
    fun disableStock(stockPackage: String, user: String = DEFAULT_USER): List<List<String>> = listOf(
        listOf("pm", "set-app-links-allowed", "--user", user, "--package", stockPackage, "false"),
    )

    /** Restores default link handling for [patchedPackage]. */
    fun restorePatched(patchedPackage: String, user: String = DEFAULT_USER): List<List<String>> = listOf(
        listOf("pm", "set-app-links-user-selection", "--user", user, "--package", patchedPackage, "false", "all"),
    )

    /** Restores default link handling for [stockPackage]. */
    fun restoreStock(stockPackage: String, user: String = DEFAULT_USER): List<List<String>> = listOf(
        listOf("pm", "set-app-links-allowed", "--user", user, "--package", stockPackage, "true"),
    )
}
