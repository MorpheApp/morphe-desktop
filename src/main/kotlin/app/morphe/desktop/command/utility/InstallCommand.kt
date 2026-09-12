/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.desktop.command.utility

import app.morphe.engine.util.ApkManifestReader
import app.morphe.engine.util.AppLinkCommands
import app.morphe.engine.installation.AdbAppLinkRouter
import app.morphe.engine.installation.AdbApkInstaller
import app.morphe.engine.installation.AdbDeviceTarget
import app.morphe.engine.installation.AdbExecutableLocator
import app.morphe.engine.installation.AdbInstallRequest
import app.morphe.engine.installation.resolveAdbDeviceTargets
import app.morphe.library.installation.installer.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import picocli.CommandLine.*
import java.io.File
import java.util.concurrent.Callable
import java.util.logging.Logger

@Command(
    name = "install",
    description = ["Install an APK file."],
)
internal object InstallCommand : Callable<Int> {
    private val logger = Logger.getLogger(this::class.java.name)
    private const val EXIT_CODE_SUCCESS = 0
    private const val EXIT_CODE_ERROR = 1

    @Parameters(
        description = ["Serials of ADB devices. If omitted, exactly one ready device must be connected."],
        arity = "0..*",
    )
    private var deviceSerials: Array<String>? = null

    @Option(
        names = ["-a", "--apk"],
        description = ["APK file to be installed."],
        required = true,
    )
    private lateinit var apk: File

    @Option(
        names = ["-m", "--mount"],
        description = ["Mount the supplied APK file over the app with the supplied package name."],
    )
    private var packageName: String? = null

    @Option(
        names = ["--route-links"],
        description = ["After installing, route this app's supported web links to it (\"open with\")."],
    )
    private var routeLinks: Boolean = false

    @Option(
        names = ["--disable-stock"],
        description = ["With --route-links: also stop this stock package from handling the links."],
    )
    private var stockPackage: String? = null

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
        val linkPlan = if (routeLinks) {
            val patched = ApkManifestReader.read(apk)?.packageName ?: run {
                logger.severe("Could not read package name from APK; installation was stopped before link routing")
                return EXIT_CODE_ERROR
            }
            patched to (
                AppLinkCommands.enablePatched(patched) +
                    (stockPackage?.let { AppLinkCommands.disableStock(it) } ?: emptyList())
                )
        } else {
            null
        }

        suspend fun install(target: AdbDeviceTarget): Boolean {
            if (packageName != null) {
                val result = try {
                    AdbRootInstaller(target.serial).install(Installer.Apk(apk, packageName))
                } catch (e: Exception) {
                    logger.severe("Installation failed on ${target.serial}: ${e.message ?: e::class.simpleName}")
                    return false
                }
                if (result == RootInstallerResult.FAILURE) {
                    logger.severe("Failed to mount the APK file on ${target.serial}")
                    return false
                }
            } else {
                val result = runCatching {
                    AdbApkInstaller().install(
                        AdbInstallRequest(target.adbPath, apk, target.serial),
                        onDebug = logger::fine,
                    ).getOrThrow()
                }
                if (result.isFailure) {
                    val error = result.exceptionOrNull()
                    logger.severe("Installation failed on ${target.serial}: ${error?.message ?: error.toString()}")
                    return false
                }
            }
            logger.info("Installed the APK file on ${target.serial}")

            return linkPlan?.let { (patched, commands) -> routeLinks(target, patched, commands) } ?: true
        }

        val results = runBlocking {
            targets.map { target -> async { install(target) } }.awaitAll()
        }
        return if (results.all { it }) EXIT_CODE_SUCCESS else EXIT_CODE_ERROR
    }

    private fun routeLinks(
        target: AdbDeviceTarget,
        patched: String,
        commands: List<List<String>>,
    ): Boolean {
        val result = AdbAppLinkRouter().route(target, commands, logger::fine)
        if (result.isFailure) {
            logger.severe(result.exceptionOrNull()?.message ?: "Link routing failed on ${target.serial}")
            return false
        }
        logger.info("Routed links to $patched on ${target.serial}")
        return true
    }
}
