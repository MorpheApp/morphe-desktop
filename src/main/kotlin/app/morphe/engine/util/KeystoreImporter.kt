/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.util

import java.io.File
import java.security.Security

/**
 * The user-facing entry point for "I have a keystore file, make it work with
 * morphe-patcher" — which only loads BKS. Wraps [KeystoreConversionUtils.convert]
 * with the safeguards we want everywhere:
 *
 *  1. **Byte-sniff, not extension-trust.** URV ships the same key bytes under
 *     different extensions; extensions lie.
 *  2. **BKS short-circuit.** If the source is already BKS, return the original
 *     file untouched — never round-trip a working keystore through convert(),
 *     no risk of provider differences silently rewriting it.
 *  3. **Never mutate the original.** Conversions write to a separate output
 *     file the caller provides; the user's source file is read-only.
 *  4. **Register BouncyCastle on demand.** convert() needs `KeyStore.getInstance("BKS")`
 *     which requires the BC provider — ensure it's installed before calling.
 */
object KeystoreImporter {

    /** Outcome of [ensureBks]. */
    sealed interface Result {
        /** The provided source was already BKS — no conversion needed. Use [file] directly. */
        data class AlreadyBks(val file: File) : Result
        /** Source was a different format; converted bytes were written to [file]. */
        data class Converted(val file: File, val sourceFormat: KeystoreInputFormat) : Result
        /** Detection or conversion failed. [reason] is safe to surface to users. */
        data class Failed(val reason: String, val cause: Throwable? = null) : Result
    }

    /**
     * Ensure [source] can be passed to morphe-patcher. If already BKS, returns
     * [Result.AlreadyBks] pointing at the original. Otherwise converts to BKS
     * and writes the bytes to [convertedOutput], returning [Result.Converted].
     *
     * [alias] and [password] are used to read the source. Pass the user's
     * configured values; an empty alias means "transfer every entry" (Manager's
     * default behavior — usually what you want for unknown third-party keys).
     */
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
            // BKS short-circuit — never re-encode an already-BKS file.
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

    /**
     * Register BouncyCastle if it isn't already — needed for
     * `KeyStore.getInstance("BKS")`. The patcher already pulls BC transitively,
     * so the class is always on the classpath; we just have to install it as a
     * JCA provider. Idempotent.
     */
    private fun ensureBouncyCastleProvider() {
        if (Security.getProvider("BC") != null) return
        try {
            val provider = Class.forName("org.bouncycastle.jce.provider.BouncyCastleProvider")
                .getDeclaredConstructor()
                .newInstance() as java.security.Provider
            Security.addProvider(provider)
        } catch (_: Exception) {
            // If BC isn't on the classpath the patcher itself would fail anyway —
            // let conversion try and surface its own error rather than guessing here.
        }
    }

    private fun friendlyConversionError(cause: Throwable): String {
        val msg = cause.message.orEmpty()
        return when {
            cause is java.security.UnrecoverableKeyException ||
                msg.contains("password", ignoreCase = true) ->
                "Couldn't decrypt keystore — wrong password or alias."
            msg.contains("No entries found", ignoreCase = true) ->
                "Keystore has no usable key entries."
            else -> "Conversion failed: ${msg.ifBlank { cause.javaClass.simpleName }}"
        }
    }
}
