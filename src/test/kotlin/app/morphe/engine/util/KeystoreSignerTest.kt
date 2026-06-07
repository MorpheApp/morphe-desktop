/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.engine.util

import app.morphe.engine.PatchEngine
import app.morphe.patcher.apk.ApkUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.io.File
import java.util.logging.Logger
import kotlin.io.path.createTempDirectory

class KeystoreSignerTest {
    private val logger = Logger.getLogger(KeystoreSignerTest::class.java.name)

    @Test
    fun `uses primary credentials when signing succeeds`() {
        val primary = primaryDetails(existingKeystore())
        val attempts = mutableListOf<ApkUtils.KeyStoreDetails>()

        signWithLegacyFallback(primary, allowLegacyFallback = true, logger = logger) { details ->
            attempts += details
        }

        assertEquals(listOf(primary), attempts)
    }

    @Test
    fun `retries with legacy credentials when default credentials fail`() {
        val primary = primaryDetails(existingKeystore())
        val attempts = mutableListOf<ApkUtils.KeyStoreDetails>()

        signWithLegacyFallback(primary, allowLegacyFallback = true, logger = logger) { details ->
            attempts += details
            if (attempts.size == 1) {
                throw IllegalArgumentException("Keystore does not contain entry with alias ${details.alias}")
            }
        }

        assertEquals(2, attempts.size)
        assertEquals(PatchEngine.Config.DEFAULT_KEYSTORE_ALIAS, attempts.first().alias)
        assertEquals(PatchEngine.Config.DEFAULT_KEYSTORE_PASSWORD, attempts.first().password)
        assertEquals(PatchEngine.Config.LEGACY_KEYSTORE_ALIAS, attempts.last().alias)
        assertEquals(PatchEngine.Config.LEGACY_KEYSTORE_PASSWORD, attempts.last().password)
        assertEquals(primary.keyStorePassword, attempts.last().keyStorePassword)
    }

    @Test
    fun `throws the primary failure when legacy retry also fails`() {
        val primary = primaryDetails(existingKeystore())
        val primaryError = IllegalArgumentException("Keystore does not contain entry with alias ${primary.alias}")
        val legacyError = IllegalArgumentException(
            "Keystore does not contain entry with alias ${PatchEngine.Config.LEGACY_KEYSTORE_ALIAS}"
        )

        val thrown = assertThrows(IllegalArgumentException::class.java) {
            signWithLegacyFallback(primary, allowLegacyFallback = true, logger = logger) { details ->
                if (details.alias == primary.alias) {
                    throw primaryError
                }

                throw legacyError
            }
        }

        assertSame(primaryError, thrown)
        assertEquals(listOf(legacyError), thrown.suppressed.toList())
    }

    @Test
    fun `does not retry when the keystore file does not exist`() {
        val primary = primaryDetails(missingKeystore())
        val primaryError = IllegalArgumentException("missing primary entry")
        var attempts = 0

        val thrown = assertThrows(IllegalArgumentException::class.java) {
            signWithLegacyFallback(primary, allowLegacyFallback = true, logger = logger) {
                attempts += 1
                throw primaryError
            }
        }

        assertSame(primaryError, thrown)
        assertEquals(1, attempts)
    }

    @Test
    fun `does not retry when legacy fallback is disabled`() {
        val primary = primaryDetails(existingKeystore())
        val primaryError = IllegalArgumentException("custom credentials failed")
        var attempts = 0

        val thrown = assertThrows(IllegalArgumentException::class.java) {
            signWithLegacyFallback(primary, allowLegacyFallback = false, logger = logger) {
                attempts += 1
                throw primaryError
            }
        }

        assertSame(primaryError, thrown)
        assertEquals(1, attempts)
    }

    private fun primaryDetails(keystore: File) = ApkUtils.KeyStoreDetails(
        keystore,
        "store-pass",
        PatchEngine.Config.DEFAULT_KEYSTORE_ALIAS,
        PatchEngine.Config.DEFAULT_KEYSTORE_PASSWORD,
    )

    private fun existingKeystore(): File {
        val tempDir = createTempDirectory().toFile().apply { deleteOnExit() }
        return File(tempDir, "morphe.keystore").apply {
            writeText("placeholder")
            deleteOnExit()
        }
    }

    private fun missingKeystore(): File {
        val tempDir = createTempDirectory().toFile().apply { deleteOnExit() }
        return File(tempDir, "missing.keystore")
    }
}
