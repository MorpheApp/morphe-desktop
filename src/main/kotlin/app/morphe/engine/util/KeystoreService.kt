/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.util

import app.morphe.engine.MorpheData
import app.morphe.engine.PatchEngine.Config.Companion.DEFAULT_KEYSTORE_ALIAS
import app.morphe.engine.PatchEngine.Config.Companion.DEFAULT_KEYSTORE_PASSWORD
import app.morphe.engine.config.EngineConfigRepository
import app.morphe.engine.util.SignatureIdentity
import app.morphe.patcher.apk.ApkSigner
import app.morphe.patcher.apk.ApkUtils
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Provider
import java.security.Security
import java.security.cert.X509Certificate
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class KeystoreService(
    private val configRepository: EngineConfigRepository = EngineConfigRepository.shared,
) {
    /**
     * Resolves the keystore details for signing:
     * 1. [explicitDetails] if non-null (e.g., CLI --keystore flag).
     * 2. Configured keystore from [EngineConfigRepository] if valid and file exists.
     * 3. Fallback to [MorpheData.defaultKeystoreFile] with default alias and password.
     */
    suspend fun resolveSigningDetails(explicitDetails: ApkUtils.KeyStoreDetails? = null): ApkUtils.KeyStoreDetails {
        if (explicitDetails != null) return explicitDetails

        val config = configRepository.loadConfig()
        val userDetails = config.toKeyStoreDetails()
        if (userDetails != null && userDetails.keyStore.exists()) {
            return userDetails
        }

        return ApkUtils.KeyStoreDetails(
            keyStore = MorpheData.defaultKeystoreFile,
            keyStorePassword = null,
            alias = DEFAULT_KEYSTORE_ALIAS,
            password = DEFAULT_KEYSTORE_PASSWORD,
        )
    }

    /**
     * Generates a new BKS keystore with a self-signed certificate and stores it at [destination].
     */
    fun generateKeystore(
        destination: File,
        alias: String = DEFAULT_KEYSTORE_ALIAS,
        storePassword: String? = null,
        entryPassword: String = DEFAULT_KEYSTORE_PASSWORD,
        validityDays: Long = 8L * 365,
    ) {
        destination.parentFile?.mkdirs()
        val keyPair = ApkSigner.newPrivateKeyCertificatePair(
            "Morphe",
            Date(System.currentTimeMillis() + validityDays * 24 * 60 * 60 * 1000),
        )
        val ks = ApkSigner.newKeyStore(setOf(
            ApkSigner.KeyStoreEntry(
                alias.ifEmpty { DEFAULT_KEYSTORE_ALIAS },
                entryPassword.ifEmpty { DEFAULT_KEYSTORE_PASSWORD },
                keyPair,
            ),
        ))
        destination.outputStream().use {
            ks.store(it, storePassword?.ifEmpty { null }?.toCharArray())
        }
    }

    /**
     * Inspects a keystore file and extracts certificate metadata, fingerprints, and warnings.
     */
    fun inspectKeystore(
        keystoreFile: File,
        password: String?,
        alias: String,
        entryPassword: String? = null,
        locale: Locale = Locale.getDefault(),
    ): KeystoreInspectionResult? {
        if (!keystoreFile.exists()) return null
        ensureBouncyCastleProvider()

        val passwordChars = password?.toCharArray() ?: charArrayOf()
        val types = listOf("BKS" to "BC", "BKS" to null, "JKS" to null, "PKCS12" to null)
        val dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM, locale)

        for ((type, provider) in types) {
            try {
                val ks = if (provider != null) KeyStore.getInstance(type, provider) else KeyStore.getInstance(type)
                keystoreFile.inputStream().use { ks.load(it, passwordChars) }

                if (!ks.containsAlias(alias)) {
                    return KeystoreInspectionResult(
                        alias = alias,
                        issuer = "",
                        validFrom = "",
                        validTo = "",
                        sha256Fingerprint = "",
                        sha1Fingerprint = "",
                        warnings = listOf(KeystoreWarning.AliasNotFound(alias)),
                    )
                }

                val cert = ks.getCertificate(alias) as? X509Certificate ?: continue

                try {
                    ks.getKey(alias, entryPassword?.toCharArray() ?: charArrayOf())
                } catch (_: Exception) {
                    return KeystoreInspectionResult(
                        alias = alias,
                        issuer = "",
                        validFrom = "",
                        validTo = "",
                        sha256Fingerprint = "",
                        sha1Fingerprint = "",
                        warnings = listOf(KeystoreWarning.KeyPasswordIncorrect(alias)),
                    )
                }

                val sha256 = MessageDigest.getInstance("SHA-256")
                    .digest(cert.encoded)
                    .joinToString(":") { "%02X".format(it) }

                val sha1 = MessageDigest.getInstance("SHA-1")
                    .digest(cert.encoded)
                    .joinToString(":") { "%02X".format(it) }

                return KeystoreInspectionResult(
                    alias = alias,
                    issuer = cert.issuerX500Principal.name,
                    validFrom = dateFormat.format(cert.notBefore),
                    validTo = dateFormat.format(cert.notAfter),
                    sha256Fingerprint = sha256,
                    sha1Fingerprint = sha1,
                    warnings = emptyList(),
                )
            } catch (_: Exception) {
                continue
            }
        }
        return null
    }

    /**
     * Returns known Morphe signature hashes for both the default keystore and the configured keystore.
     */
    suspend fun getKnownSignatureIds(): Set<String> = buildSet {
        SignatureIdentity.idForKeystore(
            MorpheData.defaultKeystoreFile,
            storePassword = null,
            alias = DEFAULT_KEYSTORE_ALIAS,
        )?.let { add(it) }

        val config = configRepository.loadConfig()
        config.resolvedKeystorePath()?.let { ks ->
            SignatureIdentity.idForKeystore(ks, config.keystorePassword, config.keystoreAlias)?.let { add(it) }
        }
    }

    private fun ensureBouncyCastleProvider() {
        if (Security.getProvider("BC") != null) return
        try {
            val provider = Class.forName("org.bouncycastle.jce.provider.BouncyCastleProvider")
                .getDeclaredConstructor().newInstance() as Provider
            Security.addProvider(provider)
        } catch (_: Exception) {}
    }

    companion object {
        val shared: KeystoreService by lazy { KeystoreService() }
    }
}

sealed interface KeystoreWarning {
    data class AliasNotFound(val alias: String) : KeystoreWarning
    data class KeyPasswordIncorrect(val alias: String) : KeystoreWarning
}

data class KeystoreInspectionResult(
    val alias: String,
    val issuer: String,
    val validFrom: String,
    val validTo: String,
    val sha256Fingerprint: String,
    val sha1Fingerprint: String,
    val warnings: List<KeystoreWarning> = emptyList(),
)
