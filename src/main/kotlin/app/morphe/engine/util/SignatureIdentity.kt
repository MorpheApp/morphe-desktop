/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.util

import app.morphe.patcher.apk.ApkUtils
import java.security.cert.Certificate

/** Computes and parses Android package signature hashes (`dumpsys package`) for device matching. */
object SignatureIdentity {

    /** Computes Android signature hash for [cert]. */
    fun idForCert(cert: Certificate): String =
        Integer.toHexString(cert.encoded.contentHashCode())

    /** Computes signature hash for the key [details] sign with, or null if unreadable. */
    fun idForKeystore(details: ApkUtils.KeyStoreDetails): String? = try {
        if (!details.keyStore.exists()) {
            null
        } else {
            val ks = ApkUtils.readKeyStore(details)
            val realAlias = ks.aliases().toList().firstOrNull { it.equals(details.alias, ignoreCase = true) } ?: details.alias
            ks.getCertificate(realAlias)?.let { idForCert(it) }
        }
    } catch (_: Exception) {
        null
    }

    /** Parses signing certificate hash from `dumpsys package` output. */
    fun parseDeviceSignatureId(dumpsysOutput: String): String? =
        Regex("""signatures:\[([0-9a-fA-F]+)]""").find(dumpsysOutput)?.groupValues?.get(1)?.lowercase()
}
