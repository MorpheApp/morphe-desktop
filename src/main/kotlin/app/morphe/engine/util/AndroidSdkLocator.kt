/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.util

import java.io.File

object AndroidSdkLocator {
    fun detectSdkPath(
        getenv: (String) -> String? = System::getenv,
        userHome: String? = System.getProperty("user.home"),
        osName: String? = System.getProperty("os.name"),
    ): File? {
        val envPath = getenv("ANDROID_HOME") ?: getenv("ANDROID_SDK_ROOT")
        if (!envPath.isNullOrEmpty()) {
            val dir = File(envPath)
            if (dir.isDirectory) return dir
        }

        if (userHome.isNullOrEmpty()) return null
        val os = osName?.lowercase().orEmpty()
        val defaultPath = when {
            os.contains("win") -> File("$userHome/AppData/Local/Android/Sdk")
            os.contains("mac") -> File("$userHome/Library/Android/sdk")
            else -> File("$userHome/Android/Sdk")
        }

        return defaultPath.takeIf { it.isDirectory }
    }
}
