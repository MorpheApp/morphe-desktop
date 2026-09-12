/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.installation

sealed interface InstalledPackageOwnership {
    data object NotInstalled : InstalledPackageOwnership
    data object NoOwner : InstalledPackageOwnership
    data class OwnedBy(val packageName: String) : InstalledPackageOwnership
}

/**
 * Read-only package/update-owner probe shared by installation safeguards and
 * future device-state consumers. A missing owner is a valid result; malformed,
 * incomplete, or failed command output is an error.
 */
class AdbPackageOwnershipProbe(private val runner: AdbCommandRunner) {
    fun probe(
        adbPath: String,
        deviceSerial: String,
        packageName: String,
        onDebug: (String) -> Unit = {},
    ): InstalledPackageOwnership {
        val prefix = listOf(adbPath, "-s", deviceSerial, "shell")
        val packages = runner.run(prefix + listOf("pm", "list", "packages", packageName)) {}
        if (packages.exitCode != 0) {
            throw probeFailure(packageName, "pm list packages exited with code ${packages.exitCode}", packages.output)
        }

        val listedPackages = packages.output.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:").trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        if (listedPackages.isEmpty()) {
            if (packages.output.isNotBlank()) {
                throw probeFailure(packageName, "pm list packages returned unrecognized output", packages.output)
            }
            onDebug("Package $packageName is not installed on $deviceSerial")
            return InstalledPackageOwnership.NotInstalled
        }
        if (packageName !in listedPackages) {
            onDebug("Package $packageName is not installed on $deviceSerial")
            return InstalledPackageOwnership.NotInstalled
        }

        val dump = runner.run(prefix + listOf("dumpsys", "package", packageName)) {}
        if (dump.exitCode != 0) {
            throw probeFailure(packageName, "dumpsys package exited with code ${dump.exitCode}", dump.output)
        }
        if (dump.output.isBlank()) {
            throw probeFailure(packageName, "dumpsys package returned no output")
        }
        return when (val ownership = parseInstalledPackageDump(packageName, dump.output)) {
            InstalledPackageOwnership.NotInstalled -> throw probeFailure(
                packageName,
                "dumpsys package output did not contain the requested package record",
            )
            InstalledPackageOwnership.NoOwner -> {
                onDebug("Installed package $packageName has no update owner on $deviceSerial")
                InstalledPackageOwnership.NoOwner
            }
            is InstalledPackageOwnership.OwnedBy -> {
                onDebug("Installed package $packageName update owner on $deviceSerial: ${ownership.packageName}")
                ownership
            }
        }
    }

    companion object {
        /** Parse ownership from a dump already obtained by another read-only query. */
        fun parseInstalledPackageDump(packageName: String, output: String): InstalledPackageOwnership {
            val packageHeader = Regex(
                "(?m)^\\s*Package\\s+\\[${Regex.escape(packageName)}]\\s+\\(",
            )
            if (!packageHeader.containsMatchIn(output)) return InstalledPackageOwnership.NotInstalled

            val owner = Regex("(?m)^\\s*updateOwnerPackageName\\s*=\\s*(\\S+)\\s*$")
                .find(output)
                ?.groupValues
                ?.get(1)
                ?.takeUnless { it.equals("null", ignoreCase = true) }
            return owner?.let(InstalledPackageOwnership::OwnedBy)
                ?: InstalledPackageOwnership.NoOwner
        }
    }

    private fun probeFailure(packageName: String, reason: String, output: String = ""): AdbInstallException {
        val detail = output.trim().takeIf { it.isNotEmpty() }?.let { ": $it" }.orEmpty()
        return AdbInstallException(
            "Could not safely determine the current update owner for '$packageName'. " +
                "Installation was stopped to protect update ownership. $reason$detail"
        )
    }
}
