/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.desktop.command.utility

import app.morphe.engine.installation.AdbDeviceTarget
import app.morphe.engine.installation.AdbExecutableLocator
import app.morphe.engine.installation.resolveAdbDeviceTargets
import app.morphe.library.installation.installer.AdbInstaller
import app.morphe.library.installation.installer.AdbInstallerResult
import app.morphe.library.installation.installer.AdbRootInstaller
import app.morphe.library.installation.installer.RootInstallerResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import picocli.CommandLine.*
import picocli.CommandLine.Help.Visibility.ALWAYS
import java.util.concurrent.Callable
import java.util.logging.Logger

@Command(
    name = "uninstall",
    description = ["Uninstall a patched app."],
)
internal object UninstallCommand : Callable<Int> {
    private val logger = Logger.getLogger(this::class.java.name)
    private const val EXIT_CODE_SUCCESS = 0
    private const val EXIT_CODE_ERROR = 1

    @Parameters(
        description = ["Serials of ADB devices. If omitted, exactly one ready device must be connected."],
        arity = "0..*",
    )
    private var deviceSerials: Array<String>? = null

    @Option(
        names = ["-p", "--package-name"],
        description = ["Package name of the app to uninstall."],
        required = true,
    )
    private lateinit var packageName: String

    @Option(
        names = ["-u", "--unmount"],
        description = ["Uninstall the patched APK file by unmounting."],
        showDefaultValue = ALWAYS,
    )
    private var unmount: Boolean = false

    override fun call(): Int {
        val adb = AdbExecutableLocator.find() ?: run {
            logger.severe("ADB not found. Please install Android SDK Platform Tools.")
            return EXIT_CODE_ERROR
        }
        val targets = try {
            resolveAdbDeviceTargets(adb, deviceSerials?.toList().orEmpty())
        } catch (e: Exception) {
            logger.severe(e.message ?: "Could not resolve an ADB device target")
            return EXIT_CODE_ERROR
        }

        suspend fun uninstall(target: AdbDeviceTarget): Boolean {
            val result = try {
                if (unmount) {
                    AdbRootInstaller(target.serial)
                } else {
                    AdbInstaller(target.serial)
                }.uninstall(packageName)
            } catch (e: Exception) {
                logger.severe("Uninstall failed on ${target.serial}: ${e.message ?: e::class.simpleName}")
                return false
            }

            return when (result) {
                RootInstallerResult.FAILURE -> {
                    logger.severe("Failed to unmount the patched APK file on ${target.serial}")
                    false
                }
                is AdbInstallerResult.Failure -> {
                    logger.severe("Uninstall failed on ${target.serial}: ${result.exception.message}")
                    false
                }
                else -> {
                    logger.info("Uninstalled the patched APK file from ${target.serial}")
                    true
                }
            }
        }

        val results = runBlocking {
            targets.map { target -> async { uninstall(target) } }.awaitAll()
        }
        return if (results.all { it }) EXIT_CODE_SUCCESS else EXIT_CODE_ERROR
    }
}
