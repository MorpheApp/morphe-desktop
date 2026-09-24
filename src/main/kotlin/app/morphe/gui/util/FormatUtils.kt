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
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.chrono.IsoChronology
import java.time.format.DateTimeFormatterBuilder
import java.time.format.FormatStyle
import java.util.Date
import java.util.Locale

object FormatUtils {

    /**
     * Resolves the [Locale] for the given [languageCode].
     * Falls back to [Locale.getDefault()] if blank or "system".
     * Configured with Latin numbering ("nu-latn") to guarantee Western digits (0-9) across all locales.
     */
    fun resolveLocale(languageCode: String? = null): Locale {
        val baseLocale = if (languageCode.isNullOrBlank() || languageCode.equals(LanguageRepository.SYSTEM_CODE, ignoreCase = true)) {
            Locale.getDefault()
        } else {
            Locale.forLanguageTag(languageCode)
        }
        return toLatinDigitsLocale(baseLocale)
    }

    /**
     * Ensures the given [Locale] uses Western digits (0-9) via the Unicode "nu-latn" extension.
     */
    fun toLatinDigitsLocale(locale: Locale): Locale {
        return try {
            Locale.Builder().setLocale(locale).setUnicodeLocaleKeyword("nu", "latn").build()
        } catch (_: Exception) {
            locale
        }
    }

    /**
     * Formats a date in medium localized style (e.g. "Sep 4, 2026" or "04.09.2026").
     */
    fun formatDate(millis: Long, locale: Locale = Locale.getDefault()): String =
        formatDate(Date(millis), locale)

    /**
     * Formats a nullable [Date] in medium localized style. Returns empty string if [date] is null.
     */
    fun formatDate(date: Date?, locale: Locale = Locale.getDefault()): String {
        if (date == null) return ""
        return DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(date)
    }

    /**
     * Formats a time in short localized style (e.g. "1:30 AM" or "14:10").
     */
    fun formatTime(millis: Long, locale: Locale = Locale.getDefault()): String =
        formatTime(Date(millis), locale)

    /**
     * Formats a nullable [Date] time in short localized style. Returns empty string if [date] is null.
     */
    fun formatTime(date: Date?, locale: Locale = Locale.getDefault()): String {
        if (date == null) return ""
        return DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(date)
    }

    /**
     * Formats a timestamp into localized date and time separated by " · " (e.g. "Sep 4, 2026 · 1:30 AM").
     */
    fun formatDateTime(millis: Long, locale: Locale = Locale.getDefault()): String =
        formatDateTime(Date(millis), locale)

    /**
     * Formats a nullable [Date] into localized date and time separated by " · ". Returns empty string if [date] is null.
     */
    fun formatDateTime(date: Date?, locale: Locale = Locale.getDefault()): String {
        if (date == null) return ""
        val datePart = formatDate(date, locale)
        val timePart = formatTime(date, locale)
        return "$datePart · $timePart"
    }

    /**
     * Formats a short month and day without year (e.g. "Sep 4" in US).
     */
    fun formatShortDate(millis: Long, locale: Locale = Locale.getDefault()): String =
        formatShortDate(Date(millis), locale)

    /**
     * Formats a nullable [Date] into a short month and day without year. Returns empty string if [date] is null.
     */
    fun formatShortDate(date: Date?, locale: Locale = Locale.getDefault()): String {
        if (date == null) return ""
        val pattern = try {
            val shortPattern = DateTimeFormatterBuilder.getLocalizedDateTimePattern(
                FormatStyle.SHORT,
                null,
                IsoChronology.INSTANCE,
                locale
            )
            val dIdx = shortPattern.indexOf('d')
            val mIdx = shortPattern.indexOf('M')
            if (dIdx != -1 && mIdx != -1 && dIdx < mIdx) "d MMM" else "MMM d"
        } catch (_: Exception) {
            "MMM d"
        }
        return SimpleDateFormat(pattern, locale).format(date)
    }

    /**
     * Formats an ISO-8601 date string (e.g. "2024-01-15T10:30:00Z" or "2024-01-15") into
     * localized date and time, or localized date if time is not present.
     * Falls back to [isoDate] if parsing fails.
     */
    fun formatIsoDateTime(isoDate: String?, locale: Locale = Locale.getDefault()): String {
        if (isoDate.isNullOrBlank()) return ""
        return try {
            val date = try {
                Date.from(OffsetDateTime.parse(isoDate).toInstant())
            } catch (_: Exception) {
                try {
                    Date.from(Instant.parse(isoDate))
                } catch (_: Exception) {
                    val localDate = LocalDate.parse(isoDate)
                    Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant())
                }
            }
            if (isoDate.contains("T")) {
                formatDateTime(date, locale)
            } else {
                formatDate(date, locale)
            }
        } catch (_: Exception) {
            isoDate
        }
    }

    /**
     * Formats a byte count into a human-readable file size string (e.g. "194.3 MB" in US or "194,3 MB" in German/Polish)
     * according to the given [locale].
     */
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

    /**
     * Formats data transfer speed given in KB/s (e.g. "1.5 MB/s" in US or "1,5 MB/s" in German/Polish)
     * according to the given [locale].
     */
    fun formatTransferRate(kbPerSec: Int, locale: Locale = Locale.getDefault()): String {
        val latnLocale = toLatinDigitsLocale(locale)
        return if (kbPerSec >= 1024) {
            String.format(latnLocale, "%.1f MB/s", kbPerSec / 1024f)
        } else {
            "$kbPerSec KB/s"
        }
    }
}

/**
 * Composable helper that resolves the current GUI [Locale] from [LocalLanguageState].
 */
@Composable
fun currentLocale(): Locale {
    val langState = LocalLanguageState.current
    return FormatUtils.resolveLocale(langState.current)
}
