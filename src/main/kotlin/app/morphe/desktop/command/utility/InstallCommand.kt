/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.desktop.command.utility

import app.morphe.engine.util.ApkManifestReader
import app.morphe.engine.util.AdbManager
import java.io.File
import java.util.logging.Logger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import picocli.CommandLine.*

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
        val adbManager = AdbManager()

        suspend fun install(deviceSerial: String? = null) {
            val targetDevice = adbManager.resolveTargetDevice(deviceSerial).getOrElse { e ->
                logger.severe(e.message ?: e.toString())
                return
            }

            val installResult = if (packageName != null) {
                adbManager.mountApk(apk, packageName!!, targetDevice.id)
            } else {
                val spoof = adbManager.resolveSpoofInstaller(targetDevice.id)
                adbManager.installApk(
                    apkPath = apk.absolutePath,
                    deviceId = targetDevice.id,
                    installerPackage = spoof,
                )
            }

            if (installResult.isFailure) return

            if (routeLinks) {
                val patched = ApkManifestReader.read(apk)?.packageName ?: run {
                    logger.severe("Could not read package name from APK; skipping link routing")
                    return
                }
                adbManager.setLinkHandling(
                    deviceId = targetDevice.id,
                    patchedPackage = patched,
                    stockPackage = stockPackage,
                    enable = true,
                ).onFailure { e ->
                    logger.severe(e.message ?: e.toString())
                }
            }
        }

        runBlocking {
            adbManager.startServer().onFailure { e ->
                logger.severe(e.message ?: "Failed to start ADB server")
                return@runBlocking
            }
            try {
                deviceSerials?.takeIf { it.isNotEmpty() }?.map { async { install(it) } }?.awaitAll() ?: install()
            } finally {
                adbManager.killServerIfOwned()
            }
        }
    }
}
