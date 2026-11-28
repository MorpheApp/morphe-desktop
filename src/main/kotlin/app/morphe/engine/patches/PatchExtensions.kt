/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.patches

import app.morphe.patcher.apk.ApkFormFactor
import app.morphe.patcher.patch.Compatibility
import app.morphe.patcher.patch.FormFactor
import app.morphe.patcher.patch.Patch
import app.morphe.patcher.patch.SupportedAbi
import java.io.File

data class CompatiblePackageDisplay(
    val packageName: String?,
    val formFactor: FormFactor?,
    val versions: List<String>,
)

fun FormFactor?.displayLabel(): String? = when (this) {
    FormFactor.TV -> "TV"
    FormFactor.WEARABLE -> "Wear OS"
    FormFactor.AUTOMOTIVE -> "Automotive"
    FormFactor.MOBILE, null -> null
}

fun Patch.versionCodesFor(
    packageName: String?,
    versionName: String,
): Map<SupportedAbi, Int>? {
    val compat = compatibility ?: return null
    return compat
        .filter { packageName == null || it.packageName == null || it.packageName == packageName }
        .flatMap { it.targets }
        .find { it.version == versionName && !it.versionCodes.isNullOrEmpty() }
        ?.versionCodes
}

fun Iterable<Patch>.versionCodesFor(
    packageName: String?,
    versionName: String,
): Map<SupportedAbi, Int>? =
    firstNotNullOfOrNull { it.versionCodesFor(packageName, versionName) }

fun Patch.isCompatibleWith(
    packageName: String,
    includeExperimental: Boolean,
    includeUniversalPatches: Boolean,
): Boolean {
    val compat = compatibility ?: return includeUniversalPatches

    return compat.any { entry ->
        when {
            entry.packageName == null -> includeUniversalPatches
            entry.packageName != packageName -> false
            else -> {
                entry.targets.any { target ->
                    includeExperimental || !target.isExperimental
                }
            }
        }
    }
}

/**
 * The form factors the patches tell builds of [packageName] apart by. Every declaration that names
 * none is [FormFactor.MOBILE], so only a package with more than one is narrowed to the build in hand.
 */
fun Iterable<Patch>.formFactorsOf(packageName: String): Set<FormFactor> =
    flatMap { it.compatibility.orEmpty() }
        .filter { it.packageName == packageName }
        .mapNotNullTo(mutableSetOf()) { it.formFactor }

/**
 * The build in [apk] patches are narrowed to, given the form factors they [declared] for its
 * package, or null when they do not tell builds apart. The APK is read only for a package with
 * several, and one that cannot be read narrows nothing.
 */
fun narrowingFormFactor(declared: Set<FormFactor>, apk: File): FormFactor? {
    if (declared.size <= 1) return null
    return runCatching { ApkFormFactor.of(apk) }.getOrNull()
}

/** [narrowingFormFactor] of the build of [packageName] in [apk], as these patches declare it. */
fun Iterable<Patch>.narrowingFormFactor(packageName: String, apk: File): FormFactor? =
    narrowingFormFactor(formFactorsOf(packageName), apk)

/** Whether the declaration applies to the build of [packageName] narrowed to [formFactor], see [narrowingFormFactor]. */
private fun Compatibility.coversBuild(packageName: String, formFactor: FormFactor?) =
    this.packageName != packageName || formFactor == null || supports(formFactor)

fun Patch.supportedVersionsFor(packageName: String, formFactor: FormFactor? = null): List<String>? {
    val compat = compatibility?.filter { it.coversBuild(packageName, formFactor) } ?: return null

    val matching = compat.filter { it.packageName == packageName }
    if (matching.isEmpty()) {
        val hasUniversalEntry = compat.any { it.packageName == null }
        return if (hasUniversalEntry) null else emptyList()
    }
    val versions = matching.flatMap { entry -> entry.targets.mapNotNull { it.version } }
    return versions.ifEmpty { null }
}

fun Patch.compatibleVersionsForDisplay(
    includeExperimental: Boolean,
): List<CompatiblePackageDisplay> {
    val compat = compatibility ?: return emptyList()

    return compat.map { entry ->
        val versions = entry.targets
            .filter { includeExperimental || !it.isExperimental }
            .mapNotNull { it.version }
        CompatiblePackageDisplay(
            packageName = entry.packageName,
            formFactor = entry.formFactor,
            versions = versions,
        )
    }
}
