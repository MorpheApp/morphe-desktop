/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.desktop.command.utility

import app.morphe.engine.util.AdbManager
import app.morphe.engine.util.Logger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import picocli.CommandLine.*
import picocli.CommandLine.Help.Visibility.ALWAYS

@Command(
    name = "uninstall",
    description = ["Uninstall a patched app."],
)
internal object UninstallCommand : Runnable {
    @Parameters(
        description = ["Serial of ADB devices. If not supplied, the first connected device will be used."],
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

    override fun run() {
        val adbManager = AdbManager()

        suspend fun uninstall(deviceSerial: String? = null) {
            val targetDevice = adbManager.resolveTargetDevice(deviceSerial).getOrElse { e ->
                Logger.error(e.message ?: e.toString())
                return
            }

            if (unmount) {
                adbManager.unmountApk(packageName, targetDevice.id)
            } else {
                adbManager.uninstallApk(packageName, targetDevice.id)
            }
        }

        runBlocking {
            adbManager.startServer().onFailure { e ->
                Logger.error(e.message ?: "Failed to start ADB server")
                return@runBlocking
            }
            try {
                deviceSerials?.takeIf { it.isNotEmpty() }?.map { async { uninstall(it) } }?.awaitAll() ?: uninstall()
            } finally {
                adbManager.killServerIfOwned()
            }
        }
    }
}
