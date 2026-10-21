/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine

/** Detects host operating system and architecture for dependency resolution. */
object PlatformDetector {
    /** Skiko runtime target identifier for the current platform and architecture. */
    val skikoTargetName: String by lazy {
        val osName = System.getProperty("os.name").lowercase()
        val osArch = System.getProperty("os.arch").lowercase()

        val isMac = osName.contains("mac")
        val isWindows = osName.contains("windows")
        val isLinux = osName.contains("linux")

        val isArm = osArch == "aarch64" || osArch == "arm64"
        val isX64 = osArch == "amd64" || osArch == "x86_64"

        when {
            isMac && isArm -> "macos-arm64"
            isMac && isX64 -> "macos-x64"
            isLinux && isArm -> "linux-arm64"
            isLinux && isX64 -> "linux-x64"
            isWindows && isX64 -> "windows-x64"
            else -> error("Unsupported OS/Architecture combination: $osName / $osArch")
        }
    }
}
