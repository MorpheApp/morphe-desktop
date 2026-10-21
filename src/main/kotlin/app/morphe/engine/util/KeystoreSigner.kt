/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.util

import app.morphe.engine.PatchEngine
import app.morphe.patcher.apk.ApkUtils

/** Signs an APK using [primary] credentials, retrying with legacy credentials on failure. */
fun signWithLegacyFallback(
    primary: ApkUtils.KeyStoreDetails,
    allowLegacyFallback: Boolean,
    sign: (ApkUtils.KeyStoreDetails) -> Unit,
) {
    try {
        sign(primary)
    } catch (primaryError: Exception) {
        if (!allowLegacyFallback || !primary.keyStore.exists()) throw primaryError

        Logger.info(
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
