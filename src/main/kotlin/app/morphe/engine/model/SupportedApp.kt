/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.model

import app.morphe.engine.util.DownloadUrlResolver
import app.morphe.engine.util.compareVersions
import app.morphe.patcher.patch.FormFactor
import app.morphe.patcher.patch.SupportedAbi

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
    val versionBuildCodes: Map<String, Set<Int>> = emptyMap(),
    /** The build code of each ABI per version, for the versions that declare them. */
    val versionAbiBuildCodes: Map<String, Map<SupportedAbi, Int>> = emptyMap(),
    /** Device form factor of the app. Null when the patches do not say, which is a phone app. */
    val formFactor: FormFactor? = null,
) {
    /**
     * The web search link of [version], with the build the patches need and the form factor of
     * the app. The link is for every architecture, so it names the arm64-v8a build, which nearly
     * every device uses, or another declared build if there is none.
     */
    fun downloadUrl(version: String?): String? {
        val codes = version?.let { versionAbiBuildCodes[it.removePrefix("v")] ?: versionAbiBuildCodes[it] }
        val buildCode = codes?.let { it[SupportedAbi.ARM64_V8A] ?: it.values.firstOrNull() }
        return getDownloadUrl(packageName, version, buildCode, formFactor)
    }

    fun buildCodeSupported(version: String, versionCode: Int?): Boolean {
        val codes = versionBuildCodes[version.removePrefix("v")]
            ?: versionBuildCodes[version]
            ?: return true
        return codes.isEmpty() || versionCode == null || versionCode in codes
    }

    fun requiredBuildCodes(version: String): Set<Int> {
        return versionBuildCodes[version.removePrefix("v")]
            ?: versionBuildCodes[version]
            ?: emptySet()
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

        fun getDownloadUrl(
            packageName: String,
            version: String?,
            buildCode: Int? = null,
            formFactor: FormFactor? = null,
        ): String? {
            if (version == null) return null
            return DownloadUrlResolver.getWebSearchDownloadLink(
                packageName = packageName,
                version = version,
                buildCode = buildCode,
                formFactor = formFactor,
            )
        }

        fun getRecommendedVersion(versions: List<String>): String? {
            if (versions.isEmpty()) return null

            return versions.sortedWith { v1, v2 ->
                compareVersions(v2, v1) // Descending order
            }.firstOrNull()
        }
    }
}
