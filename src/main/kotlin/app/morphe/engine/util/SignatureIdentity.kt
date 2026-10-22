/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.util

import java.io.File
import java.security.KeyStore
import java.security.Provider
import java.security.Security
import java.security.cert.Certificate

/** Computes and parses Android package signature hashes (`dumpsys package`) for device matching. */
object SignatureIdentity {

    /** Computes Android signature hash for [cert]. */
    fun idForCert(cert: Certificate): String =
        Integer.toHexString(cert.encoded.contentHashCode())

    /** Computes signature hash for [alias] in [keystoreFile], or null if unreadable. */
    fun idForKeystore(keystoreFile: File, storePassword: String?, alias: String): String? = try {
        if (!keystoreFile.exists()) {
            null
        } else {
            ensureBouncyCastle()
            val ks = KeyStore.getInstance("BKS")
            keystoreFile.inputStream().use { ks.load(it, storePassword?.toCharArray()) }
            val realAlias = ks.aliases().toList().firstOrNull { it.equals(alias, ignoreCase = true) } ?: alias
            ks.getCertificate(realAlias)?.let { idForCert(it) }
        }
    } catch (_: Exception) {
        null
    }

    /** Parses signing certificate hash from `dumpsys package` output. */
    fun parseDeviceSignatureId(dumpsysOutput: String): String? =
        Regex("""signatures:\[([0-9a-fA-F]+)]""").find(dumpsysOutput)?.groupValues?.get(1)?.lowercase()

    private fun ensureBouncyCastle() {
        if (Security.getProvider("BC") != null) return
        try {
            val provider = Class.forName("org.bouncycastle.jce.provider.BouncyCastleProvider")
                .getDeclaredConstructor().newInstance() as Provider
            Security.addProvider(provider)
        } catch (_: Exception) {}
    }
}
