/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 *
 * Keystore format conversion utilities ported from morphe-manager.
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
        /** Detects keystore format (PKCS12, JKS, or BKS) from the first 4 bytes of file header. */
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
    /** Successful BKS conversion result. */
    data class Success(val data: List<Byte>) : KeystoreConversionResult
    /** Conversion failure result. */
    data class Error(val cause: Exception) : KeystoreConversionResult
}

object KeystoreConversionUtils {

    /** Converts a keystore from [format] into BKS bytes, transferring matching [alias] entries. */
    fun convert(
        inputStream: InputStream,
        format: KeystoreInputFormat,
        alias: String,
        password: String
    ): KeystoreConversionResult = runCatching {
        val pass = password.toCharArray()

        // JKS uses custom parser for compatibility with legacy keystore variants.
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

        // Convert standard JCA formats (PKCS12, BKS).
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
