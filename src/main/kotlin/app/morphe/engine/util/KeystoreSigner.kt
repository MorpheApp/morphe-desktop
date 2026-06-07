/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.engine.util

import app.morphe.engine.PatchEngine
import app.morphe.patcher.apk.ApkUtils
import java.util.logging.Logger

/**
 * Signs with [primary] credentials, then retries the legacy default alias when
 * the existing shared keystore predates the current defaults.
 *
 * If both attempts fail, keep the original exception as the primary failure
 * because it describes the credentials the user actually expected to work.
 */
fun signWithLegacyFallback(
    primary: ApkUtils.KeyStoreDetails,
    allowLegacyFallback: Boolean,
    logger: Logger,
    sign: (ApkUtils.KeyStoreDetails) -> Unit,
) {
    try {
        sign(primary)
    } catch (primaryError: Exception) {
        if (!allowLegacyFallback || !primary.keyStore.exists()) throw primaryError

        logger.info(
            "Default keystore credentials failed (${primaryError.message}). Retrying with legacy credentials"
        )

        val legacy = ApkUtils.KeyStoreDetails(
            primary.keyStore,
            primary.keyStorePassword,
            PatchEngine.Config.LEGACY_KEYSTORE_ALIAS,
            PatchEngine.Config.LEGACY_KEYSTORE_PASSWORD,
        )

        try {
            sign(legacy)
        } catch (legacyError: Exception) {
            primaryError.addSuppressed(legacyError)
            throw primaryError
        }
    }
}
