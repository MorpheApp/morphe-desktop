/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.config

import app.morphe.engine.PatchEngine.Config.Companion.DEFAULT_KEYSTORE_ALIAS
import app.morphe.engine.PatchEngine.Config.Companion.DEFAULT_KEYSTORE_PASSWORD
import app.morphe.engine.apk.AndroidArchitectures
import app.morphe.engine.util.AndroidSdkLocator
import app.morphe.engine.util.PortablePaths
import app.morphe.patcher.apk.ApkUtils
import app.morphe.patcher.dex.BytecodeMode
import java.io.File
import kotlinx.serialization.Serializable

@Serializable
data class EngineConfig(
    val keystorePath: String? = null,
    val keystorePassword: String? = null,
    val keystoreAlias: String = DEFAULT_KEYSTORE_ALIAS,
    val keystoreEntryPassword: String = DEFAULT_KEYSTORE_PASSWORD,
    val defaultOutputDirectory: String? = null,
    val keepArchitectures: Set<String> = AndroidArchitectures.ALL,
    val gitHubPat: String = "",
    val autoStartAdb: Boolean = false,
    val autoRouteLinksAfterInstall: Boolean = false,
    val disableStockLinksAfterInstall: Boolean = false,
    val autoCleanupTempFiles: Boolean = true,
    val developerOptions: Boolean = false,
    val excludedMppPatterns: List<String> = emptyList(),
    val lastLocalPatchDir: String? = null,
    val bytecodeMode: String = "STRIP_FAST",
    val validateFingerprints: Boolean = false,
    val verifyWithSdk: Boolean = false,
    val customAndroidSdkPath: String? = null,
) {
    fun resolvedDefaultOutputDirectory(): File? =
        defaultOutputDirectory?.let(PortablePaths::resolve)

    fun resolvedKeystorePath(): File? =
        keystorePath?.let(PortablePaths::resolve)

    fun resolvedCustomAndroidSdkPath(): File? =
        customAndroidSdkPath?.let(PortablePaths::resolve)?.takeIf { it.isDirectory }

    fun resolvedAndroidSdkPath(): File? {
        if (!verifyWithSdk) return null
        return resolvedCustomAndroidSdkPath() ?: AndroidSdkLocator.detectSdkPath()
    }

    fun resolvedBytecodeMode(): BytecodeMode =
        runCatching { BytecodeMode.valueOf(bytecodeMode) }.getOrDefault(BytecodeMode.STRIP_FAST)

    fun toKeyStoreDetails(): ApkUtils.KeyStoreDetails? {
        val ks = resolvedKeystorePath() ?: return null
        return ApkUtils.KeyStoreDetails(
            keyStore = ks,
            keyStorePassword = keystorePassword,
            alias = keystoreAlias.ifEmpty { DEFAULT_KEYSTORE_ALIAS },
            password = keystoreEntryPassword.ifEmpty { DEFAULT_KEYSTORE_PASSWORD },
        )
    }
}
