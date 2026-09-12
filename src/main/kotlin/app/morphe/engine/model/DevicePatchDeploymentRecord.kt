/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.model

import kotlinx.serialization.Serializable

/**
 * Proof of the exact patched artifact Morphe successfully installed on one
 * device. The APK hash lets a later device refresh reject stale receipts after
 * an external reinstall, while [sourcesSnapshot] identifies the installed
 * patch-source versions independently from the Android app version.
 */
@Serializable
data class DevicePatchDeploymentRecord(
    val deviceSerial: String,
    /** Original package name used by the patched-app history. */
    val packageName: String,
    /** Actual post-patch package queried on the device. */
    val installedPackageName: String,
    val apkVersion: String,
    val outputApkSha256: String,
    val sourcesSnapshot: List<PatchedAppRecord.PatchedSourceSnapshot>,
    val installedAt: Long,
    /** Android package lastUpdateTime captured when this receipt was verified. */
    val packageLastUpdateTime: String? = null,
)
