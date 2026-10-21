/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.util

import app.morphe.engine.model.Release

/** Strips leading 'v' and whitespace from a version string. */
fun String.normalizeVersion(): String = removePrefix("v").removePrefix("V").trim()

/** Compares two semantic version strings, ordering stable releases above pre-releases. */
fun compareVersions(v1: String?, v2: String?): Int {
    if (v1 == null && v2 == null) return 0
    if (v1 == null) return -1
    if (v2 == null) return 1

    val version1 = v1.normalizeVersion()
    val version2 = v2.normalizeVersion()
    if (version1 == version2) return 0

    data class VersionParts(val base: String, val preRelease: String?)

    fun extractParts(version: String): VersionParts {
        val preReleasePattern =
            """^([\d.]+)[-._]?(dev|beta|rc|alpha|preview)""".toRegex(RegexOption.IGNORE_CASE)
        val match = preReleasePattern.find(version)
        return if (match != null) {
            VersionParts(match.groupValues[1], match.groupValues[2])
        } else {
            val numMatch = """^([\d.]+)(.*)$""".toRegex().find(version)
            if (numMatch != null && numMatch.groupValues[2].isNotEmpty()) {
                VersionParts(numMatch.groupValues[1], numMatch.groupValues[2])
            } else {
                VersionParts(version, null)
            }
        }
    }

    val parts1 = extractParts(version1)
    val parts2 = extractParts(version2)

    val base1 = parts1.base.split(".").map { it.toIntOrNull() ?: 0 }
    val base2 = parts2.base.split(".").map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(base1.size, base2.size)) {
        val a = base1.getOrNull(i) ?: 0
        val b = base2.getOrNull(i) ?: 0
        when {
            a < b -> return -1
            a > b -> return 1
        }
    }

    return when {
        parts1.preRelease == null && parts2.preRelease == null -> 0
        parts1.preRelease == null && parts2.preRelease != null -> 1  // stable beats pre-release
        parts1.preRelease != null && parts2.preRelease == null -> -1
        else -> {
            val num1 = version1.substringAfterLast('.').toLongOrNull()
            val num2 = version2.substringAfterLast('.').toLongOrNull()
            if (num1 != null && num2 != null) num1.compareTo(num2) else version1.compareTo(version2)
        }
    }
}

fun isNewerVersion(current: String?, baseline: String?): Boolean {
    if (current.isNullOrBlank() || baseline.isNullOrBlank()) return false
    if (current.equals("unknown", true) || baseline.equals("unknown", true)) return false
    return compareVersions(current, baseline) > 0
}

fun String?.isDevTag(): Boolean {
    val tag = this ?: return false
    return tag.contains("dev", ignoreCase = true) ||
        tag.contains("alpha", ignoreCase = true) ||
        tag.contains("beta", ignoreCase = true)
}

fun newerRelease(dev: Release?, stable: Release?): Release? = when {
    dev == null -> stable
    stable == null -> dev
    compareVersions(dev.tagName, stable.tagName) >= 0 -> dev
    else -> stable
}
