/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.installation

/** Runs post-install app-link commands only on the already captured target. */
class AdbAppLinkRouter(
    private val runner: AdbCommandRunner = ProcessAdbCommandRunner(),
) {
    fun route(
        target: AdbDeviceTarget,
        commands: List<List<String>>,
        onDebug: (String) -> Unit = {},
    ): Result<Unit> = runCatching {
        require(target.serial.isNotBlank()) { "ADB device serial must not be blank" }
        val prefix = listOf(target.adbPath, "-s", target.serial, "shell")
        commands.forEach { argv ->
            val command = prefix + argv
            onDebug("Running: ${command.joinToString(" ")}")
            val result = runner.run(command) { line -> onDebug("ADB: $line") }
            if (
                result.exitCode != 0 ||
                result.output.contains("Error", ignoreCase = true) ||
                result.output.contains("Failure", ignoreCase = true)
            ) {
                throw AdbInstallException(
                    "Link routing failed on ${target.serial}: " +
                        result.output.trim().ifBlank { "ADB command exited with code ${result.exitCode}" }
                )
            }
        }
    }
}
