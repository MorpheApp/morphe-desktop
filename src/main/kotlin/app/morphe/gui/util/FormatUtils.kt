/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import androidx.compose.runtime.Composable
import app.morphe.gui.LocalLanguageState
import app.morphe.gui.data.repository.LanguageRepository
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.util.Date
import java.util.Locale

object FormatUtils {

    /** Resolves the [Locale] for [languageCode], configured with Latin numbering. */
    fun resolveLocale(languageCode: String? = null): Locale {
        val baseLocale = if (languageCode.isNullOrBlank() || languageCode.equals(LanguageRepository.SYSTEM_CODE, ignoreCase = true)) {
            Locale.getDefault()
        } else {
            Locale.forLanguageTag(languageCode)
        }
        return toLatinDigitsLocale(baseLocale)
    }

    /** Ensures [locale] uses Western digits (0-9) via Unicode "nu-latn" extension. */
    fun toLatinDigitsLocale(locale: Locale): Locale {
        return try {
            Locale.Builder().setLocale(locale).setUnicodeLocaleKeyword("nu", "latn").build()
        } catch (_: Exception) {
            locale
        }
    }

    /** Formats a nullable [Date] in medium localized style. */
    fun formatDate(date: Date?, locale: Locale = Locale.getDefault()): String {
        if (date == null) return ""
        return DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(date)
    }

    /** Formats a timestamp into localized date and time. */
    fun formatDateTime(millis: Long, locale: Locale = Locale.getDefault()): String =
        formatDateTime(Date(millis), locale)

    /** Formats a nullable [Date] into localized date and time. */
    fun formatDateTime(date: Date?, locale: Locale = Locale.getDefault()): String {
        if (date == null) return ""
        val datePart = formatDate(date, locale)
        val timePart = DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(date)
        return "$datePart · $timePart"
    }

    /** Formats a timestamp into short month and day without year. */
    fun formatShortDate(millis: Long, locale: Locale = Locale.getDefault()): String =
        formatShortDate(Date(millis), locale)

    /** Formats a nullable [Date] into short month and day without year. */
    fun formatShortDate(date: Date?, locale: Locale = Locale.getDefault()): String {
        if (date == null) return ""
        val pattern = if (isDayBeforeMonth(locale)) "d MMM" else "MMM d"
        return SimpleDateFormat(pattern, locale).format(date)
    }

    /** Formats a timestamp with full month name, adding the year when it differs from [nowMillis]'s year. */
    fun formatFullMonthOrLongDate(
        millis: Long,
        locale: Locale = Locale.getDefault(),
        nowMillis: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        val dateYear = Instant.ofEpochMilli(millis).atZone(zoneId).year
        val currentYear = Instant.ofEpochMilli(nowMillis).atZone(zoneId).year
        val date = Date(millis)
        return if (dateYear == currentYear) {
            val pattern = if (isDayBeforeMonth(locale)) "d MMMM" else "MMMM d"
            SimpleDateFormat(pattern, locale).format(date)
        } else {
            DateFormat.getDateInstance(DateFormat.LONG, locale).format(date)
        }
    }

    private fun isDayBeforeMonth(locale: Locale): Boolean = try {
        val shortPattern = DateTimeFormatterBuilder.getLocalizedDateTimePattern(
            FormatStyle.SHORT,
            null,
            IsoChronology.INSTANCE,
            locale
        )
        val dIdx = shortPattern.indexOf('d')
        val mIdx = shortPattern.indexOf('M')
        dIdx != -1 && mIdx != -1 && dIdx < mIdx
    } catch (_: Exception) {
        false
    }

    /** Formats a signed integer delta with an explicit leading `+` when positive. */
    fun formatSignedDelta(delta: Int): String = if (delta > 0) "+$delta" else delta.toString()

    /** Formats a count as the reader writes numbers, with the grouping of their language. */
    fun formatCount(count: Long, locale: Locale = Locale.getDefault()): String =
        String.format(toLatinDigitsLocale(locale), "%,d", count)

    /** Formats a byte count into a localized human-readable file size string. */
    fun formatFileSize(bytes: Long, locale: Locale = Locale.getDefault()): String {
        val latnLocale = toLatinDigitsLocale(locale)
        return when {
            bytes <= 0 -> "0 B"
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(latnLocale, "%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format(latnLocale, "%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format(latnLocale, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }

    /** Formats data transfer speed in KB/s into a localized string. */
    fun formatTransferRate(kbPerSec: Int, locale: Locale = Locale.getDefault()): String {
        val latnLocale = toLatinDigitsLocale(locale)
        return if (kbPerSec >= 1024) {
            String.format(latnLocale, "%.1f MB/s", kbPerSec / 1024f)
        } else {
            "$kbPerSec KB/s"
        }
    }

    /** Formats an application version with its targeted build code (if any), showing at most one build code. */
    fun formatVersionWithBuild(version: String, buildCodes: Set<Int>): String {
        if (buildCodes.isEmpty()) return version
        val primaryCode = buildCodes.first()
        return "$version ($primaryCode)"
    }
}

/** Resolves current GUI [Locale] from [LocalLanguageState]. */
@Composable
fun currentLocale(): Locale {
    val langState = LocalLanguageState.current
    return FormatUtils.resolveLocale(langState.current)
}
