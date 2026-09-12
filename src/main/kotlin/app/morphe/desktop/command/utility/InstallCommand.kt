/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.desktop.command.utility

import app.morphe.engine.util.ApkManifestReader
import app.morphe.engine.util.AppLinkCommands
import app.morphe.engine.installation.AdbApkInstaller
import app.morphe.engine.installation.AdbExecutableLocator
import app.morphe.engine.installation.AdbInstallRequest
import app.morphe.engine.installation.resolveAdbDeviceSerial
import app.morphe.library.installation.installer.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import picocli.CommandLine.*
import se.vidstige.jadb.JadbConnection
import java.io.File
import java.util.logging.Logger

@Command(
    name = "install",
    description = ["Install an APK file."],
)
internal object InstallCommand : Runnable {
    private val logger = Logger.getLogger(this::class.java.name)

    @Parameters(
        description = ["Serial of ADB devices. If not supplied, the first connected device will be used."],
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

    override fun run() {
        suspend fun install(deviceSerial: String? = null) {
            if (packageName != null) {
                val result = try {
                    AdbRootInstaller(deviceSerial).install(Installer.Apk(apk, packageName))
                } catch (e: Exception) {
                    logger.severe(e.toString())
                    return
                }
                if (result == RootInstallerResult.FAILURE) {
                    logger.severe("Failed to mount the APK file")
                    return
                }
            } else {
                val result = runCatching {
                    val adb = AdbExecutableLocator.find()
                        ?: throw IllegalStateException("ADB not found. Please install Android SDK Platform Tools.")
                    val serial = resolveAdbDeviceSerial(adb, deviceSerial)
                    AdbApkInstaller().install(
                        AdbInstallRequest(adb, apk, serial),
                        onDebug = logger::fine,
                    ).getOrThrow()
                }
                if (result.isFailure) {
                    logger.severe(result.exceptionOrNull().toString())
                    return
                }
            }
            logger.info("Installed the APK file")

            if (routeLinks) routeLinks(deviceSerial)
        }

        runBlocking {
            deviceSerials?.map { async { install(it) } }?.awaitAll() ?: install()
        }
    }

    private fun routeLinks(deviceSerial: String?) {
        val patched = ApkManifestReader.read(apk)?.packageName ?: run {
            logger.severe("Could not read package name from APK; skipping link routing")
            return
        }
        val commands = AppLinkCommands.enablePatched(patched) +
            (stockPackage?.let { AppLinkCommands.disableStock(it) } ?: emptyList())

        val devices = JadbConnection().devices
        val device = deviceSerial?.let { s -> devices.firstOrNull { it.serial == s } }
            ?: devices.firstOrNull()
            ?: run { logger.severe("No ADB device for link routing"); return }

        commands.forEach { argv ->
            val cmd = argv.joinToString(" ")
            val process = device.shellProcessBuilder(cmd).start()
            val out = process.inputStream.bufferedReader().readText().trim()
            val exit = process.waitFor()
            if (exit != 0 || out.contains("Error", true) || out.contains("Failure", true)) {
                logger.severe("Link command failed: $cmd -> ${out.ifBlank { "exit $exit" }}")
            }
        }
        logger.info("Routed links to $patched")
    }
}
