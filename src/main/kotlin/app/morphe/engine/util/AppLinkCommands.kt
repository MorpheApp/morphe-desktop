/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.engine.util

/**
 * Builds the `pm` shell command lines that route web links ("open with") to a
 * patched app and, optionally, stop the stock app from grabbing those same
 * links.
 *
 * Pure argv construction only — no process execution. Each frontend runs these
 * through its own adb path (the GUI's `AdbManager`, the CLI's installer), so the
 * "what commands to run" decision lives in one tested place while "how to exec"
 * stays per-frontend.
 *
 * Each returned entry is one `adb shell` invocation's arguments (i.e. everything
 * after `adb -s <serial> shell`). Run them in order.
 *
 * Background: the patched app's web intent filters are NOT `autoVerify`, so
 * Android's domain-verification commands no-op on them. The *user-selection*
 * state, however, can be set for any host the app declares — that's the route
 * used here (and the one Morphe's own docs prescribe).
 */
object AppLinkCommands {

    /** Default Android user. Primary user is always 0; `cur` also works. */
    const val DEFAULT_USER = "0"

    /**
     * Approve every web host the patched app declares so it handles those links.
     *
     * `set-app-links-allowed true` flips the per-app "open supported links"
     * master switch on; `set-app-links-user-selection true all` then approves
     * all declared hosts (verified or not). Both are needed: the master switch
     * alone doesn't approve unverified hosts.
     */
    fun enablePatched(patchedPackage: String, user: String = DEFAULT_USER): List<List<String>> = listOf(
        listOf("pm", "set-app-links-allowed", "--user", user, "--package", patchedPackage, "true"),
        listOf("pm", "set-app-links-user-selection", "--user", user, "--package", patchedPackage, "true", "all"),
    )

    /**
     * Stop the stock app from handling its web links, without disabling the app
     * itself. The master "open supported links" switch off is surgical and
     * reversible — far lighter than `pm disable-user`.
     *
     * Only meaningful when a rename patch was used (stock + patched coexist as
     * different packages) and the stock package is actually installed.
     */
    fun disableStock(stockPackage: String, user: String = DEFAULT_USER): List<List<String>> = listOf(
        listOf("pm", "set-app-links-allowed", "--user", user, "--package", stockPackage, "false"),
    )

    /**
     * Reverse [enablePatched]: revoke the patched app's host approvals so link
     * routing returns to Android's defaults.
     */
    fun restorePatched(patchedPackage: String, user: String = DEFAULT_USER): List<List<String>> = listOf(
        listOf("pm", "set-app-links-user-selection", "--user", user, "--package", patchedPackage, "false", "all"),
    )

    /**
     * Reverse [disableStock]: hand link handling back to the stock app.
     */
    fun restoreStock(stockPackage: String, user: String = DEFAULT_USER): List<List<String>> = listOf(
        listOf("pm", "set-app-links-allowed", "--user", user, "--package", stockPackage, "true"),
    )
}
