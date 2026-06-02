/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.engine.util

import app.morphe.engine.PatchEngine
import app.morphe.patcher.apk.ApkUtils
import java.util.logging.Logger

/**
 * Signs an APK with [primary] credentials, falling back to the legacy ("Morphe Key" / empty password) entry.
 * The legacy retry only fires when [allowLegacyFallback] is true AND the keystore file already exists,
 * i.e. the user is on default credentials and we're reading a pre-existing keystore that might predate the current alias.
 * This preserves the exact condition both call sites (CLI + engine) used before.
 *
 * On double failure the PRIMARY exception is thrown (legacy attached as suppressed).
 * The primary error is the meaningful one: the user expects the current Morphe key,
 * so "no 'Morphe' entry" is more actionable than whatever the legacy retry hit.
 * The old behavior threw the *legacy* failure, which surfaced confusing errors.
 *
 * [sign] performs the actual signing; callers wrap this call with their own progress / step-result reporting.
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

        // Never silently swallow the real cause. Always log it before the back-compat path.
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
