/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import app.morphe.engine.util.isNewerVersion
import app.morphe.engine.util.normalizeVersion

data class ChangelogEntry(
    val version: String,
    val date: String?,
    val content: String,
    val scopedBullets: Map<String, List<String>> = emptyMap(),
)

val ChangelogEntry.isPrerelease: Boolean get() = version.contains('-')

data class ChangelogSection(
    val kind: Kind,
    val title: String?,
    val items: List<ChangelogItem>,
) {
    enum class Kind { FEATURES, FIXES, IMPROVEMENTS, PERFORMANCE, APP_SUPPORT, OTHER }
}

data class ChangelogItem(
    val scope: String?,
    val text: String,
    val isBullet: Boolean = true,
)

enum class ChangelogBadge { INSTALLED, DOWNLOADED, PRERELEASE, LATEST }

val ChangelogSection.changeCount: Int get() = items.count { it.isBullet }

fun List<ChangelogSection>.countOf(kind: ChangelogSection.Kind): Int =
    sumOf { section -> if (section.kind == kind) section.changeCount else 0 }

object ChangelogParser {

    private val VERSION_HEADING = Regex(
        """^#{1,3}\s+(?:\S+\s+)?(?:\[([^]]+)]\([^)]*\)|([^\s\[(]+))\s+\((\d{4}-\d{2}-\d{2})\)""",
        RegexOption.IGNORE_CASE,
    )

    private val BULLET_SCOPE_RE = Regex("""^\* \*\*(.+?):\*\*""")

    private val EXPERIMENTAL_VERSION_ADDITION_RE = Regex(
        """^Add(?:ed)?\s+experimental\s+support\s+for\b""",
        RegexOption.IGNORE_CASE,
    )

    private val COMMIT_LINK_REGEX = Regex("""\s*\(\[([0-9a-f]{7,})]\([^)]+/commit/[^)]+\)\)""")
    private val SECTION_HEADING_RE = Regex("""^\s*#{1,6}\s+(.+?)\s*$""")
    private val HEADING_DECORATION_RE = Regex("""^[^\p{L}\p{N}]+""")
    private val LIST_ITEM_RE = Regex("""^\s*(?:[*+-]|\d+[.)])\s+(.+?)\s*$""")
    private val SCOPED_BODY_RE = Regex("""^\*\*(.+?):\*\*\s*(.*)$""")
    private val NOISE_LINE_RE = Regex(
        """^\s*(?:[-=_*~]{3,}|[<=>]{7}.*|```.*|<[^>]+>|(?:signed-off-by|co-authored-by):.*)\s*$""",
        RegexOption.IGNORE_CASE,
    )
    private val QUOTE_PREFIX_RE = Regex("""^\s*>\s?""")
    private val IMAGE_RE = Regex("""!\[[^]]*]\([^)]*\)""")

    private fun String.sanitizeContent(): String = replace(COMMIT_LINK_REGEX, "").trimEnd()

    private fun resolveScopedBullets(content: String): Map<String, List<String>> {
        val scoped = mutableMapOf<String, MutableList<String>>()
        for (rawLine in content.lines()) {
            val line = rawLine.trim()
            val match = BULLET_SCOPE_RE.find(line) ?: continue
            val scope = match.groupValues[1]
            val body = line.substring(match.value.length).trim()
            scoped.getOrPut(scope) { mutableListOf() }.add(body)
        }
        return scoped
    }

    fun parse(markdown: String): List<ChangelogEntry> {
        val entries = mutableListOf<ChangelogEntry>()

        var currentVersion: String? = null
        var currentDate: String? = null
        val currentContent = StringBuilder()

        fun flush() {
            val v = currentVersion ?: return
            val raw = currentContent.toString()
            entries += ChangelogEntry(
                version = v,
                date = currentDate,
                content = raw.sanitizeContent(),
                scopedBullets = resolveScopedBullets(raw),
            )
        }

        for (line in markdown.lines()) {
            val match = VERSION_HEADING.find(line)
            if (match != null) {
                flush()
                currentVersion = match.groupValues[1].ifEmpty { match.groupValues[2] }.trim()
                currentDate = match.groupValues[3]
                currentContent.clear()
            } else if (currentVersion != null) {
                currentContent.appendLine(line)
            }
        }
        flush()

        return entries
    }

    fun sections(content: String): List<ChangelogSection> {
        val sections = mutableListOf<ChangelogSection>()
        var title: String? = null
        var items = mutableListOf<ChangelogItem>()

        fun flush() {
            if (items.isEmpty()) return
            sections += ChangelogSection(sectionKindOf(title), title, items)
            items = mutableListOf()
        }

        val cleaned = content.replace(COMMIT_LINK_REGEX, "").replace(IMAGE_RE, "")
        for (rawLine in cleaned.lines()) {
            val line = rawLine.replace(QUOTE_PREFIX_RE, "")
            if (line.isBlank() || NOISE_LINE_RE.matches(line)) continue

            val heading = SECTION_HEADING_RE.matchEntire(line)
            if (heading != null) {
                flush()
                title = heading.groupValues[1].replace(HEADING_DECORATION_RE, "").ifEmpty { heading.groupValues[1] }.trim()
                continue
            }

            val body = LIST_ITEM_RE.matchEntire(line)?.groupValues?.get(1)
            if (body == null) {
                items += ChangelogItem(scope = null, text = line.trim(), isBullet = false)
                continue
            }
            val scoped = SCOPED_BODY_RE.matchEntire(body)
            items += if (scoped != null) {
                ChangelogItem(scope = scoped.groupValues[1], text = scoped.groupValues[2])
            } else {
                ChangelogItem(scope = null, text = body)
            }
        }
        flush()

        return sections.sortedBy { it.kind.ordinal }
    }

    private fun sectionKindOf(title: String?): ChangelogSection.Kind = when (title?.trim()?.lowercase()) {
        "features", "new features" -> ChangelogSection.Kind.FEATURES
        "bug fixes", "fixes" -> ChangelogSection.Kind.FIXES
        "improvements" -> ChangelogSection.Kind.IMPROVEMENTS
        "performance", "performance improvements" -> ChangelogSection.Kind.PERFORMANCE
        "updated app support", "app support" -> ChangelogSection.Kind.APP_SUPPORT
        else -> ChangelogSection.Kind.OTHER
    }

    fun entriesNewerThan(
        entries: List<ChangelogEntry>,
        installedVersion: String?,
    ): List<ChangelogEntry> {
        if (installedVersion == null) return entries
        val installedDate = findVersion(entries, installedVersion)?.date
        return entries.filter { entry ->
            isNewerVersion(entry.version, installedVersion) &&
                (installedDate == null || entry.date == null || entry.date >= installedDate)
        }
    }

    fun hasChangesFor(
        entries: List<ChangelogEntry>,
        installedVersion: String?,
        appNames: Collection<String>,
    ): Boolean {
        if (appNames.isEmpty()) return false
        val newerEntries = entriesNewerThan(entries, installedVersion)
        return newerEntries.any { entry ->
            entry.scopedBullets.any { (scope, bullets) ->
                val scopeMatches = appNames.any { name ->
                    scope.equals(name, ignoreCase = true) ||
                        scope.startsWith("$name - ", ignoreCase = true)
                }
                scopeMatches && bullets.any { !EXPERIMENTAL_VERSION_ADDITION_RE.containsMatchIn(it) }
            }
        }
    }

    fun findVersion(entries: List<ChangelogEntry>, version: String): ChangelogEntry? {
        val normalized = version.normalizeVersion()
        return entries.firstOrNull { it.version.normalizeVersion() == normalized }
    }
}
