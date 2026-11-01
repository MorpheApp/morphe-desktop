/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.util

import app.morphe.engine.MorpheData
import app.morphe.engine.PatchEngine.Config.Companion.DEFAULT_KEYSTORE_ALIAS
import app.morphe.engine.PatchEngine.Config.Companion.DEFAULT_KEYSTORE_PASSWORD
import app.morphe.engine.config.EngineConfigRepository
import app.morphe.patcher.apk.ApkSigner
import app.morphe.patcher.apk.ApkUtils
import java.io.File
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class KeystoreService(
    private val configRepository: EngineConfigRepository = EngineConfigRepository.shared,
) {
    /** Resolves keystore signing details from explicit inputs, configuration, or default fallback. */
    suspend fun resolveSigningDetails(explicitDetails: ApkUtils.KeyStoreDetails? = null): ApkUtils.KeyStoreDetails {
        if (explicitDetails != null) return explicitDetails

        val config = configRepository.loadConfig()
        val userDetails = config.toKeyStoreDetails()
        if (userDetails != null && userDetails.keyStore.exists()) {
            return userDetails
        }

        return defaultSigningDetails
    }

    /** The type of keystore [file] holds, or null if it is none Morphe can sign with. */
    fun keystoreType(file: File): ApkSigner.KeyStoreType? =
        ApkSigner.KeyStoreType.of(file.inputStream().use { it.readNBytes(4) })

    /**
     * Generates a new PKCS12 keystore with a self-signed certificate and stores it at [destination].
     *
     * PKCS12 has a single password, so [storePassword] protects the key as well, and
     * [entryPassword] is used when no store password is given.
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
        destination.outputStream().use {
            ApkSigner.writeKeyStore(
                it,
                storePassword?.ifEmpty { null } ?: entryPassword.ifEmpty { DEFAULT_KEYSTORE_PASSWORD },
                mapOf(alias.ifEmpty { DEFAULT_KEYSTORE_ALIAS } to keyPair),
            )
        }
    }

    /**
     * Writes the key [details] sign with to [destination] as a PKCS12 keystore that opens with the
     * key's password alone, the form other signing tools expect.
     */
    fun exportKeystore(details: ApkUtils.KeyStoreDetails, destination: File) {
        destination.outputStream().use { ApkUtils.exportKeyStore(details, it) }
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

        val ks = try {
            ApkUtils.readKeyStore(ApkUtils.KeyStoreDetails(keystoreFile, password, alias, entryPassword.orEmpty()))
        } catch (_: Exception) {
            return null
        }

        fun warning(warning: KeystoreWarning) = KeystoreInspectionResult(
            alias = alias,
            issuer = "",
            validFrom = "",
            validTo = "",
            sha256Fingerprint = "",
            sha1Fingerprint = "",
            warnings = listOf(warning),
        )

        if (!ks.containsAlias(alias)) return warning(KeystoreWarning.AliasNotFound(alias))
        val cert = ks.getCertificate(alias) as? X509Certificate ?: return null

        try {
            ks.getKey(alias, entryPassword?.toCharArray() ?: charArrayOf())
        } catch (_: Exception) {
            return warning(KeystoreWarning.KeyPasswordIncorrect(alias))
        }

        fun fingerprint(algorithm: String) = MessageDigest.getInstance(algorithm)
            .digest(cert.encoded)
            .joinToString(":") { "%02X".format(it) }

        val dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM, locale)
        return KeystoreInspectionResult(
            alias = alias,
            issuer = cert.issuerX500Principal.name,
            validFrom = dateFormat.format(cert.notBefore),
            validTo = dateFormat.format(cert.notAfter),
            sha256Fingerprint = fingerprint("SHA-256"),
            sha1Fingerprint = fingerprint("SHA-1"),
            warnings = emptyList(),
        )
    }

    /**
     * Returns known Morphe signature hashes for both the default keystore and the configured keystore.
     */
    suspend fun getKnownSignatureIds(): Set<String> = buildSet {
        SignatureIdentity.idForKeystore(defaultSigningDetails)?.let { add(it) }
        configRepository.loadConfig().toKeyStoreDetails()?.let { details ->
            SignatureIdentity.idForKeystore(details)?.let { add(it) }
        }
    }

    companion object {
        val shared: KeystoreService by lazy { KeystoreService() }

        private val defaultSigningDetails
            get() = ApkUtils.KeyStoreDetails(
                keyStore = MorpheData.defaultKeystoreFile,
                keyStorePassword = null,
                alias = DEFAULT_KEYSTORE_ALIAS,
                password = DEFAULT_KEYSTORE_PASSWORD,
            )
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
