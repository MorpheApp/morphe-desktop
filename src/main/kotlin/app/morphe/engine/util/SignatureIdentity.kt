/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.util

import com.android.apksig.ApkVerifier
import java.io.File
import java.security.MessageDigest
import java.security.KeyStore
import java.security.Security
import java.security.cert.Certificate

/**
 * Identifies a signing certificate by the same value Android reports in
 * `dumpsys package <pkg>`:
 *
 * ```
 * signatures=PackageSignatures{… signatures:[a7001add], past signatures:[]}
 * ```
 *
 * That `a7001add` is `Integer.toHexString(certDER.contentHashCode())` of the
 * signing cert (Android's `Signature.hashCode()`). Computing the same value from
 * Morphe's keystore cert lets us tell, from a connected device, whether an
 * installed app was signed by Morphe — without pulling the APK.
 *
 * The `signatures:[HEX]` format has been stable across Android 7→15. If a device
 * emits something we don't recognise, [parseDeviceSignatureId] returns null and
 * callers fall back to the version-robust "installed?" check (no false verdicts).
 */
object SignatureIdentity {

    /** Android's signature id for [cert]: `toHexString(DER.contentHashCode())`. */
    fun idForCert(cert: Certificate): String =
        Integer.toHexString(cert.encoded.contentHashCode())

    /** Collision-resistant identity used for local APK-to-keystore comparisons. */
    fun sha256ForCert(cert: Certificate): String =
        MessageDigest.getInstance("SHA-256").digest(cert.encoded)
            .joinToString("") { "%02x".format(it) }

    /**
     * Signature id of the cert under [alias] in the BKS [keystoreFile], or null
     * if it can't be read. Only the public certificate is needed, so the key
     * (entry) password is irrelevant — just the store password.
     */
    fun idForKeystore(keystoreFile: File, storePassword: String?, alias: String): String? = try {
        certificateForKeystore(keystoreFile, storePassword, alias)?.let(::idForCert)
    } catch (e: Exception) {
        null
    }

    fun sha256ForKeystore(keystoreFile: File, storePassword: String?, alias: String): String? = try {
        certificateForKeystore(keystoreFile, storePassword, alias)?.let(::sha256ForCert)
    } catch (e: Exception) {
        null
    }

    /** Verified current APK signer fingerprints, or null when no reliable verdict is possible. */
    fun sha256ForApkSigners(apkFile: File): Set<String>? = try {
        val result = ApkVerifier.Builder(apkFile).build().verify()
        if (!result.isVerified || result.signerCertificates.isEmpty()) null
        else result.signerCertificates.mapTo(linkedSetOf(), ::sha256ForCert)
    } catch (e: Exception) {
        null
    }

    /**
     * Parse the signing-cert id from `dumpsys package` output. Matches the
     * `signatures:[HEX]` form (lowercased). Null if not present/unrecognised.
     */
    fun parseDeviceSignatureId(dumpsysOutput: String): String? =
        Regex("""signatures:\[([0-9a-fA-F]+)\]""").find(dumpsysOutput)?.groupValues?.get(1)?.lowercase()

    private fun certificateForKeystore(
        keystoreFile: File,
        storePassword: String?,
        alias: String,
    ): Certificate? {
        if (!keystoreFile.exists()) return null
        ensureBouncyCastle()
        val ks = KeyStore.getInstance("BKS")
        keystoreFile.inputStream().use { ks.load(it, storePassword?.toCharArray()) }
        val realAlias = ks.aliases().toList().firstOrNull { it.equals(alias, ignoreCase = true) } ?: alias
        return ks.getCertificate(realAlias)
    }

    private fun ensureBouncyCastle() {
        if (Security.getProvider("BC") != null) return
        try {
            val provider = Class.forName("org.bouncycastle.jce.provider.BouncyCastleProvider")
                .getDeclaredConstructor().newInstance() as java.security.Provider
            Security.addProvider(provider)
        } catch (_: Exception) {
            // BC ships transitively with the patcher; if absent, BKS load fails
            // and idForKeystore returns null — graceful.
        }
    }
}
