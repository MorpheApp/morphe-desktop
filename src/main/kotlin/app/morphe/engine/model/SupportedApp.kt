/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.model

import app.morphe.engine.util.DownloadUrlResolver
import app.morphe.engine.util.compareVersions

/** App target aggregated from patch bundle compatibility metadata. */
data class SupportedApp(
    val packageName: String,
    val displayName: String,
    val supportedVersions: List<String>,
    val experimentalVersions: List<String> = emptyList(),
    val recommendedVersion: String?,
    val apkDownloadUrl: String? = null,
    val experimentalDownloadUrl: String? = null,
    val appIconColor: String? = null,
    val versionBuildCodes: Map<String, Set<Int>> = emptyMap()
) {
    fun buildCodeSupported(version: String, versionCode: Int?): Boolean {
        val codes = versionBuildCodes[version.removePrefix("v")]
            ?: versionBuildCodes[version]
            ?: return true
        return codes.isEmpty() || versionCode == null || versionCode in codes
    }

    companion object {
        fun resolveDisplayName(packageName: String, providedName: String?): String {
            return providedName?.takeIf { it.isNotBlank() } ?: getDisplayName(packageName)
        }

        /** Formats a package name into a readable display name when no override exists. */
        fun getDisplayName(packageName: String): String {
            val knownNames = mapOf(
                "com.google.android.youtube" to "YouTube",
                "com.google.android.apps.youtube.music" to "YouTube Music",
                "com.reddit.frontpage" to "Reddit",
            )
            knownNames[packageName]?.let { return it }

            // Smart fallback: use the most meaningful part of the package name
            val parts = packageName.split(".")
            // Skip common prefixes: com, org, net, android, app, etc.
            val skipParts = setOf("com", "org", "net", "io", "me", "app", "android", "apps", "free")
            val meaningful = parts.filter { it.lowercase() !in skipParts && it.length > 1 }
            // Use the last meaningful part, or the full last segment
            val name = meaningful.lastOrNull() ?: parts.last()
            // Split camelCase and underscores, capitalize
            return name
                .replace("_", " ")
                .replace(Regex("([a-z])([A-Z])")) { "${it.groupValues[1]} ${it.groupValues[2]}" }
                .split(" ")
                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        }

        fun getDownloadUrl(packageName: String, version: String?): String? {
            if (version == null) return null
            return DownloadUrlResolver.getWebSearchDownloadLink(packageName, version)
        }

        fun getRecommendedVersion(versions: List<String>): String? {
            if (versions.isEmpty()) return null

            return versions.sortedWith { v1, v2 ->
                compareVersions(v2, v1) // Descending order
            }.firstOrNull()
        }
    }
}
