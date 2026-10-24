/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.util

import java.io.File
import java.security.Provider
import java.security.Security
import java.security.UnrecoverableKeyException

/** Entry point to validate and convert imported keystores to BKS format. */
object KeystoreImporter {

    /** Outcome of [ensureBks]. */
    sealed interface Result {
        /** Source is already BKS; [file] can be used directly. */
        data class AlreadyBks(val file: File) : Result
        /** Source was converted and written to [file]. */
        data class Converted(val file: File, val sourceFormat: KeystoreInputFormat) : Result
        /** Detection or conversion failed with user-facing [reason]. */
        data class Failed(val reason: String, val cause: Throwable? = null) : Result
    }

    /** Ensures [source] is in BKS format, converting to [convertedOutput] if necessary. */
    fun ensureBks(
        source: File,
        convertedOutput: File,
        alias: String,
        password: String,
    ): Result {
        if (!source.exists()) {
            return Result.Failed("Keystore file not found: ${source.absolutePath}")
        }
        if (!source.canRead()) {
            return Result.Failed("Keystore file is not readable: ${source.absolutePath}")
        }

        val header = try {
            source.inputStream().use { stream ->
                val buf = ByteArray(4)
                val read = stream.read(buf)
                if (read < 4) return Result.Failed("Keystore file is too small to identify")
                buf
            }
        } catch (e: Exception) {
            return Result.Failed("Couldn't read keystore header: ${e.message ?: e.javaClass.simpleName}", e)
        }

        val format = KeystoreInputFormat.detectFromBytes(header)
            ?: return Result.Failed(
                "Unrecognized keystore format. Supported: BKS, PKCS12 (.p12/.pfx), JKS."
            )

        if (format == KeystoreInputFormat.BKS || format == KeystoreInputFormat.KEYSTORE) {
            return Result.AlreadyBks(source)
        }

        ensureBouncyCastleProvider()

        val converted = source.inputStream().use {
            KeystoreConversionUtils.convert(it, format, alias, password)
        }
        return when (converted) {
            is KeystoreConversionResult.Error -> Result.Failed(
                friendlyConversionError(converted.cause),
                converted.cause,
            )
            is KeystoreConversionResult.Success -> {
                try {
                    convertedOutput.parentFile?.mkdirs()
                    convertedOutput.writeBytes(converted.data.toByteArray())
                    Result.Converted(convertedOutput, format)
                } catch (e: Exception) {
                    Result.Failed(
                        "Couldn't save converted keystore: ${e.message ?: e.javaClass.simpleName}",
                        e,
                    )
                }
            }
        }
    }

    /** Registers the BouncyCastle security provider if not already installed. */
    private fun ensureBouncyCastleProvider() {
        if (Security.getProvider("BC") != null) return
        try {
            val provider = Class.forName("org.bouncycastle.jce.provider.BouncyCastleProvider")
                .getDeclaredConstructor()
                .newInstance() as Provider
            Security.addProvider(provider)
        } catch (_: Exception) {
        }
    }

    private fun friendlyConversionError(cause: Throwable): String {
        val msg = cause.message.orEmpty()
        return when {
            cause is UnrecoverableKeyException ||
                msg.contains("password", ignoreCase = true) ->
                "Couldn't decrypt keystore, wrong password or alias."
            msg.contains("No entries found", ignoreCase = true) ->
                "Keystore has no usable key entries."
            else -> "Conversion failed: ${msg.ifBlank { cause.javaClass.simpleName }}"
        }
    }
}
