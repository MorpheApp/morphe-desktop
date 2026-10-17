/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.config

import app.morphe.engine.PatchEngine.Config.Companion.DEFAULT_KEYSTORE_ALIAS
import app.morphe.engine.PatchEngine.Config.Companion.DEFAULT_KEYSTORE_PASSWORD
import app.morphe.engine.apk.AndroidArchitectures
import app.morphe.engine.util.PortablePaths
import app.morphe.patcher.apk.ApkUtils
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
) {
    /**
     * Resolved live [File] for [defaultOutputDirectory] via [PortablePaths.resolve].
     */
    fun resolvedDefaultOutputDirectory(): File? =
        defaultOutputDirectory?.let(PortablePaths::resolve)

    /**
     * Resolved live [File] for [keystorePath] via [PortablePaths.resolve].
     */
    fun resolvedKeystorePath(): File? =
        keystorePath?.let(PortablePaths::resolve)

    /**
     * Constructs [ApkUtils.KeyStoreDetails] from configured keystore fields, or null if no path configured.
     */
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
