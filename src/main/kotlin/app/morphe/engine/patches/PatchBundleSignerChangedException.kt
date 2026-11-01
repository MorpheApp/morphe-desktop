/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.patches

import app.morphe.patcher.patch.PatchBundleSignature

/**
 * A bundle that is not signed by the key its source trusts. [signer] is the key it is signed
 * with, or null if it is not signed at all.
 */
class PatchBundleSignerChangedException(val signer: String?) :
    SecurityException("The patch bundle is not signed by the key its source trusts") {
    /** [signer] in the short form people compare, see [PatchBundleSignature.fingerprint]. */
    val signerFingerprint: String? get() = signer?.let(PatchBundleSignature::fingerprint)
}
