/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 *
 * Ported from morphe-manager. Manager wrote this because morphe-patcher only
 * speaks BKS but users (including URV cross-over users) often bring keystores
 * in PKCS12, JKS, or BKS-with-misleading-extensions. Conversion is byte-
 * sniffed, not extension-trusted — URV ships the same key bytes under
 * different extensions, so trusting the suffix would mis-route. Keep this
 * code as close to Manager's as possible so a key converted here and a key
 * converted in Manager produce byte-identical BKS output.
 */

package app.morphe.engine.util

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.KeyFactory
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec

enum class KeystoreInputFormat(
    val displayName: String,
    val extensions: List<String>,
    val jcaType: String
) {
    KEYSTORE(".keystore (BKS)", listOf("keystore"), "BKS"),
    BKS(".bks (BKS)", listOf("bks"), "BKS"),
    PKCS12(".p12 / .pfx (PKCS12)", listOf("p12", "pfx"), "PKCS12"),
    JKS(".jks (JKS)", listOf("jks"), "JKS");

    companion object {
        fun fromExtension(ext: String): KeystoreInputFormat? =
            entries.firstOrNull { ext.lowercase() in it.extensions }

        /**
         * Sniff the keystore format from the first 4 bytes of the file.
         * Returns null if the header is not recognized.
         *
         * PKCS12 - ASN.1 SEQUENCE: 0x30 + any BER length byte (0x80–0x84)
         * JKS    - magic: 0xFEEDFEED
         * BKS    - 4-byte big-endian version int, value 1 or 2
         */
        fun detectFromBytes(header: ByteArray): KeystoreInputFormat? {
            if (header.size < 4) return null
            return when {
                header[0] == 0x30.toByte() && header[1] in byteArrayOf(
                    0x80.toByte(), 0x81.toByte(), 0x82.toByte(), 0x83.toByte(), 0x84.toByte()
                ) -> PKCS12
                header[0] == 0xFE.toByte() && header[1] == 0xED.toByte() &&
                        header[2] == 0xFE.toByte() && header[3] == 0xED.toByte() -> JKS
                header[0] == 0x00.toByte() && header[1] == 0x00.toByte() &&
                        header[2] == 0x00.toByte() &&
                        (header[3] == 0x01.toByte() || header[3] == 0x02.toByte()) -> BKS
                else -> null
            }
        }
    }
}

sealed interface KeystoreConversionResult {
    /** Conversion succeeded — [data] is a BKS keystore ready for the patcher. */
    data class Success(val data: List<Byte>) : KeystoreConversionResult
    /** Wrong password/alias, corrupt file, or unsupported format variant. */
    data class Error(val cause: Exception) : KeystoreConversionResult
}

object KeystoreConversionUtils {

    /**
     * Loads a keystore of [format] from [inputStream] and re-encodes all entries into a
     * new BKS keystore, returning the raw bytes. The stream is read but not closed.
     *
     * When [alias] is blank all entries are transferred. Otherwise, the matching entry is
     * looked up case-insensitively, falling back to transferring everything if not found.
     *
     * [password] is used for both the keystore and the individual key entries.
     */
    fun convert(
        inputStream: InputStream,
        format: KeystoreInputFormat,
        alias: String,
        password: String
    ): KeystoreConversionResult = runCatching {
        val pass = password.toCharArray()

        // JKS requires manual parsing — see JksKeyStoreParser. Manager originally added
        // this because Android's BC has no JKSKeyStoreSpi; we keep it as a fallback for
        // URV-style JKS variants the stock SPI has historically choked on.
        if (format == KeystoreInputFormat.JKS) {
            val entries = JksKeyStoreParser.parse(inputStream, password)
            check(entries.isNotEmpty()) { "No entries found in JKS keystore" }

            val jksTarget = KeyStore.getInstance("BKS").apply { load(null, pass) }
            val cf = CertificateFactory.getInstance("X.509")
            val kf = KeyFactory.getInstance("RSA")

            val filtered = if (alias.isBlank()) entries
            else entries.filter { it.alias.equals(alias, ignoreCase = true) }
                .ifEmpty { entries }

            filtered.forEach { entry ->
                val privateKey = kf.generatePrivate(PKCS8EncodedKeySpec(entry.privateKeyDer.toByteArray()))
                val certs = entry.certificatesDer.map {
                    cf.generateCertificate(it.toByteArray().inputStream())
                }.toTypedArray()
                jksTarget.setKeyEntry(entry.alias, privateKey, pass, certs)
            }

            val out = ByteArrayOutputStream()
            jksTarget.store(out, pass)
            return@runCatching KeystoreConversionResult.Success(out.toByteArray().toList())
        }

        // PKCS12 / BKS go through standard JCA. BKS source paths shouldn't normally
        // reach here — callers should short-circuit on BKS detection — but kept as a
        // safety net so calling convert() with BKS still produces a valid result.
        val source = KeyStore.getInstance(format.jcaType).apply { load(inputStream, pass) }

        val entriesToMigrate = if (alias.isBlank()) {
            source.aliases().toList()
        } else {
            source.aliases().toList()
                .filter { it.equals(alias, ignoreCase = true) }
                .ifEmpty { source.aliases().toList() }
        }

        check(entriesToMigrate.isNotEmpty()) { "No entries found in keystore" }

        val target = KeyStore.getInstance("BKS").apply { load(null, pass) }

        for (entryAlias in entriesToMigrate) {
            val protection = KeyStore.PasswordProtection(pass)
            when {
                source.isKeyEntry(entryAlias) -> {
                    val entry = source.getEntry(entryAlias, protection) ?: continue
                    target.setEntry(entryAlias, entry, protection)
                }
                source.isCertificateEntry(entryAlias) ->
                    target.setCertificateEntry(entryAlias, source.getCertificate(entryAlias))
            }
        }

        val out = ByteArrayOutputStream()
        target.store(out, pass)
        KeystoreConversionResult.Success(out.toByteArray().toList())
    }.getOrElse { e ->
        KeystoreConversionResult.Error(e as? Exception ?: RuntimeException(e))
    }
}
