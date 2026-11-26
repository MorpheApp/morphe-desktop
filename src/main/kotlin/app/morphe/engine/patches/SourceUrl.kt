/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.patches

import io.ktor.http.Url
import java.net.URI
import java.util.Locale

const val BRANCH_STABLE = "main"
const val BRANCH_DEV = "dev"
const val DEFAULT_BUNDLE_FILE = "patches-bundle.json"
const val JSON_EXTENSION = "json"

/** Supported Git hosting providers and their URL/path conventions. */
enum class CodeHost(
    val key: String,
    val host: String,
    val rawHost: String = host,
) {
    GITHUB("github", "github.com", rawHost = "raw.githubusercontent.com") {
        override val issuesPath = "issues"
        override fun releasePath(tag: String) = "releases/tag/$tag"
        override fun releaseTag(segments: List<String>): String? = releaseTagByKeyword(segments)
        override fun rawFileUrl(owner: String, repo: String, branch: String, path: String) =
            "https://$rawHost/$owner/$repo/$branch/$path"
        override fun avatarUrl(owner: String) = "https://$host/$owner.png"
        override fun isRawFile(host: String, segments: List<String>) = host == rawHost

        // raw.githubusercontent.com/owner/repo/BRANCH/path, unless pinned through refs/heads,
        // or github.com/owner/repo/tree|blob/BRANCH/path
        override fun branchIndex(host: String, segments: List<String>): Int? =
            if (host == rawHost) 2.takeIf { segments.size > 2 && segments[2] != "refs" }
            else 3.takeIf { segments.size > 3 && segments[2] in BRANCH_VIEWS }
    },

    GITLAB("gitlab", "gitlab.com") {
        override val issuesPath = "-/issues"
        override fun releasePath(tag: String) = "-/releases/$tag"
        override fun releaseTag(segments: List<String>): String? = when {
            segments.getOrNull(2) == "-" && segments.getOrNull(3) == "releases" -> segments.getOrNull(4)
            segments.getOrNull(2) == "releases" -> segments.getOrNull(3)
            else -> null
        }
        override fun rawFileUrl(owner: String, repo: String, branch: String, path: String) =
            "https://$host/$owner/$repo/-/raw/$branch/$path"
        // GitLab serves no avatar by username
        override fun avatarUrl(owner: String) = "https://unavatar.io/gitlab/$owner"
        override fun isRawFile(host: String, segments: List<String>) = viewIndex(segments, RAW_VIEW) != null

        // gitlab.com/owner/repo/-/raw|tree|blob/BRANCH/path
        override fun branchIndex(host: String, segments: List<String>): Int? =
            GITLAB_VIEWS.firstNotNullOfOrNull { viewIndex(segments, it) }
                ?.plus(1)?.takeIf { it < segments.size }

        private fun viewIndex(segments: List<String>, view: String) =
            segments.indexOf(view).takeIf { it >= 3 && segments[it - 1] == "-" }
    },

    CODEBERG("codeberg", "codeberg.org") {
        override val issuesPath = "issues"
        override fun releasePath(tag: String) = "releases/tag/$tag"
        override fun releaseTag(segments: List<String>): String? = releaseTagByKeyword(segments)
        override fun rawFileUrl(owner: String, repo: String, branch: String, path: String) =
            "https://$host/$owner/$repo/raw/branch/$branch/$path"
        override fun avatarUrl(owner: String) = "https://$host/$owner.png"
        override fun isRawFile(host: String, segments: List<String>) = segments.getOrNull(2) == RAW_VIEW

        // codeberg.org/owner/repo/raw|src/branch/BRANCH/path
        override fun branchIndex(host: String, segments: List<String>): Int? =
            4.takeIf { segments.size > 4 && segments[2] in CODEBERG_VIEWS && segments[3] == "branch" }
    };

    /** Path of the issues page under the repository page. */
    abstract val issuesPath: String

    /** Path of the page of the release tagged [tag] under the repository page. */
    abstract fun releasePath(tag: String): String

    /** Release tag named by path [segments] on this host, or null when the link names none. */
    abstract fun releaseTag(segments: List<String>): String?

    /** The file at [path] on [branch] of the repository, as the host serves it raw. */
    abstract fun rawFileUrl(owner: String, repo: String, branch: String, path: String): String

    /** Picture of the user or organization [owner]. */
    abstract fun avatarUrl(owner: String): String

    /** Whether a link on [host] with path [segments] already points at a raw file. */
    abstract fun isRawFile(host: String, segments: List<String>): Boolean

    /** Where in [segments] the branch a link on [host] points at sits, or null when it names none. */
    abstract fun branchIndex(host: String, segments: List<String>): Int?

    companion object {
        private const val RAW_VIEW = "raw"
        private val RELEASE_VIEWS = setOf("releases", "release")
        private val BRANCH_VIEWS = setOf("tree", "blob")
        private val GITLAB_VIEWS = BRANCH_VIEWS + RAW_VIEW
        private val CODEBERG_VIEWS = setOf(RAW_VIEW, "src")

        private fun releaseTagByKeyword(segments: List<String>): String? =
            segments.getOrNull(4)?.takeIf { segments.getOrNull(2) in RELEASE_VIEWS && segments.getOrNull(3) == "tag" }

        /** The code host serving pages or raw files on [host], or null for any other. */
        fun of(host: String): CodeHost? {
            val lower = host.lowercase(Locale.US)
            val withoutWww = lower.removePrefix("www.")
            return entries.firstOrNull { withoutWww == it.host || lower == it.rawHost }
        }
    }
}

/** Parsed HTTP(S) source URL with helpers for repository metadata and host-specific links. */
class SourceUrl private constructor(
    val host: String,
    val segments: List<String>,
) {
    /** The code host whose layout the link follows, or null for any other host. */
    val codeHost: CodeHost? = CodeHost.of(host)

    /** Owner of the repository on a known code host. */
    val owner: String? get() = segments.firstOrNull()?.takeIf { codeHost != null }

    /** Name of the repository on a known code host. */
    val repo: String?
        get() = segments.getOrNull(1)?.removeSuffix(".git")?.takeIf { codeHost != null && it.isNotEmpty() }

    /** Repository path as `owner/repo` on a known code host. */
    val repoPath: String? get() = repo?.let { "$owner/$it" }

    /** Page of the repository, whatever file, branch or pull request the link points at. */
    val repositoryUrl: String? get() = repoPath?.let { "https://${codeHost!!.host}/$it" }

    /** The repository as `<host>=owner/repo`, used in Morphe API headers. */
    val repositoryKey: String? get() = repoPath?.let { "${codeHost!!.key}=$it" }

    /** Issues page of the repository, or null for unknown hosts. */
    val issuesUrl: String? get() = repositoryUrl?.let { "$it/${codeHost!!.issuesPath}" }

    /** Picture of the repository owner, see [CodeHost.avatarUrl]. */
    val ownerAvatarUrl: String? get() = owner?.let { codeHost!!.avatarUrl(it) }

    /** Where in [segments] the branch the link points at sits, see [CodeHost.branchIndex]. */
    private val branchIndex: Int?
        get() = repo?.let { codeHost!!.branchIndex(host, segments) }

    /** The branch the link points at, see [branchIndex]. */
    val branch: String? get() = branchIndex?.let(segments::get)

    /** Path of the file the link points at within its branch, or null when it names none. */
    val filePath: String?
        get() = branchIndex?.let { segments.drop(it + 1).joinToString("/") }?.takeIf { it.isNotEmpty() }

    /**
     * The raw file at [path] on [branch] of the repository, or null for a link to no repository.
     * [path] defaults to the file the link itself points at.
     */
    fun rawFileUrl(branch: String, path: String? = filePath): String? {
        val repoName = repo ?: return null
        val file = path?.takeIf { it.isNotEmpty() } ?: return null
        return codeHost!!.rawFileUrl(owner!!, repoName, branch, file)
    }

    /** Release tag targeted by a release link on a known code host, or null. */
    val pinnedTag: String? get() = repo?.let { codeHost!!.releaseTag(segments) }

    /** Release page URL for [tag] on this repository, or null for unknown hosts. */
    fun releaseUrl(tag: String): String? =
        repositoryUrl?.let { "$it/${codeHost!!.releasePath(tag)}" }

    companion object {
        private val WINDOWS_DRIVE_REGEX = Regex("""^[A-Za-z]:[/\\]""")
        private val IPV4_HOST_REGEX = Regex("""^\d{1,3}(?:\.\d{1,3}){3}$""")
        private val DOMAIN_TLD_REGEX = Regex("""^[a-zA-Z]{2,}$""")
        private val DOMAIN_CHARS_REGEX = Regex("""^[a-zA-Z0-9.-]+$""")

        /** Takes [url] apart, or returns null when it is not an HTTP(S) URL with a host. */
        fun parse(url: String): SourceUrl? = try {
            val uri = URI(url.trim())
            val scheme = uri.scheme?.lowercase(Locale.US) ?: return null
            if (scheme != "http" && scheme != "https") return null
            val host = uri.host?.takeIf { it.isNotBlank() }?.lowercase(Locale.US) ?: return null
            val segments = uri.path.orEmpty().trim('/').split('/').filter { it.isNotBlank() }
            SourceUrl(
                host = host,
                segments = segments,
            )
        } catch (_: Exception) {
            null
        }

        /** Whether [endpoint] targets the `main` or `dev` branch of a [CodeHost] repository. */
        fun supportsPrerelease(endpoint: String): Boolean =
            parse(endpoint)?.branch.let { it == BRANCH_STABLE || it == BRANCH_DEV }

        /** Switches [endpoint] to [targetBranch] when it names a [CodeHost] branch and file. */
        fun switchBranch(endpoint: String, targetBranch: String): String =
            parse(endpoint)?.takeIf { it.branch != null }?.rawFileUrl(targetBranch) ?: endpoint

        /**
         * The PNG named after the bundle JSON at [endpoint], next to it.
         * Returns null if [endpoint] does not point to a `.json` file.
         */
        fun bundleAvatarUrl(endpoint: String): String? {
            val path = endpoint.substringBefore('?').substringBefore('#')
            val extension = ".$JSON_EXTENSION"
            if (!path.endsWith(extension, ignoreCase = true)) return null
            return path.dropLast(extension.length) + ".png" + endpoint.substring(path.length)
        }

        /**
         * Normalizes [input] into a raw `.json` bundle URL (rewriting [CodeHost] branch links to raw
         * URLs and appending [DEFAULT_BUNDLE_FILE] when no `.json` file is named), or returns null if invalid.
         */
        fun normalizeJsonUrl(input: String): String? {
            val normalized = RemotePatchSourceFactory.normalizeInput(input)
            if (normalized.isBlank()) return null
            if (normalized.startsWith("/") || normalized.startsWith("./") ||
                normalized.startsWith("../") || normalized.startsWith("~") ||
                WINDOWS_DRIVE_REGEX.containsMatchIn(normalized)
            ) {
                return null
            }

            val explicitHttp: Boolean
            val candidate: String
            if ("://" in normalized) {
                val rawScheme = normalized.substringBefore("://")
                if (!rawScheme.equals("http", ignoreCase = true) &&
                    !rawScheme.equals("https", ignoreCase = true)
                ) {
                    return null
                }
                explicitHttp = rawScheme.equals("http", ignoreCase = true)
                candidate = normalized
            } else {
                if ('/' !in normalized) return null
                val hostPort = normalized.substringBefore('/')
                if (!looksLikeHostPort(hostPort)) return null
                explicitHttp = false
                candidate = "https://$normalized"
            }

            val parsed = runCatching { Url(candidate) }.getOrNull() ?: return null
            val rawHost = parsed.host.takeIf { it.isNotBlank() }?.lowercase(Locale.US) ?: return null
            val segments = parsed.encodedPath.trim('/').split('/').filter { it.isNotBlank() }
            val path = "/" + segments.joinToString("/")
            val query = parsed.encodedQuery.takeIf { it.isNotEmpty() }?.let { "?$it" }.orEmpty()

            val isLoopback = rawHost == "localhost" || rawHost == "127.0.0.1" ||
                rawHost == "::1" || rawHost == "[::1]"
            val scheme = if (explicitHttp && isLoopback) "http" else "https"
            val targetDefaultPort = if (scheme == "http") 80 else 443

            val codeHost = CodeHost.of(rawHost)
            if (codeHost == null) {
                if (segments.isEmpty() || !path.endsWith(".$JSON_EXTENSION", ignoreCase = true)) {
                    return null
                }
                val portSuffix = parsed.specifiedPort
                    .takeIf { it != 0 && it != targetDefaultPort }
                    ?.let { ":$it" }
                    .orEmpty()
                return "$scheme://$rawHost$portSuffix$path$query"
            }

            if (codeHost.isRawFile(rawHost, segments)) {
                if (segments.size < 3 || !path.endsWith(".$JSON_EXTENSION", ignoreCase = true)) {
                    return null
                }
                val canonicalHost = if (rawHost == codeHost.rawHost) rawHost else codeHost.host
                return "https://$canonicalHost$path$query"
            }

            if (segments.size < 2) return null
            if (codeHost == CodeHost.GITHUB && segments.getOrNull(2) == "pull") return null

            val branchIndex = codeHost.branchIndex(rawHost, segments) ?: return null
            val branch = segments[branchIndex]
            val repo = segments[1].removeSuffix(".git").takeIf { it.isNotEmpty() } ?: return null
            val file = segments.drop(branchIndex + 1).joinToString("/")
            val bundleFile = when {
                file.endsWith(".$JSON_EXTENSION", ignoreCase = true) -> file
                file.isEmpty() -> DEFAULT_BUNDLE_FILE
                else -> "$file/$DEFAULT_BUNDLE_FILE"
            }
            return codeHost.rawFileUrl(segments[0], repo, branch, bundleFile)
        }

        private fun looksLikeHostPort(hostPort: String): Boolean {
            if (hostPort.startsWith("[")) {
                val close = hostPort.indexOf(']')
                if (close <= 1) return false
                val inside = hostPort.substring(1, close)
                val rest = hostPort.substring(close + 1)
                if (':' !in inside) return false
                return rest.isEmpty() || isValidPortSuffix(rest)
            }
            val colonIdx = hostPort.indexOf(':')
            val host = if (colonIdx >= 0) {
                if (!isValidPortSuffix(hostPort.substring(colonIdx))) return false
                hostPort.substring(0, colonIdx)
            } else {
                hostPort
            }
            if (host.equals("localhost", ignoreCase = true)) return true
            if ('.' !in host || host.startsWith('.') || host.endsWith('.')) return false
            if (!DOMAIN_CHARS_REGEX.matches(host)) return false
            if (IPV4_HOST_REGEX.matches(host)) return true
            return DOMAIN_TLD_REGEX.matches(host.substringAfterLast('.'))
        }

        private fun isValidPortSuffix(suffix: String): Boolean {
            if (!suffix.startsWith(':') || suffix.length < 2) return false
            val port = suffix.substring(1).toIntOrNull() ?: return false
            return port in 1..65535
        }
    }
}
