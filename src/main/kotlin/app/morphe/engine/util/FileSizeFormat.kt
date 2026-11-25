/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.util

import java.util.Locale

/** Ensures [locale] uses Western digits (0-9) via Unicode "nu-latn" extension. */
fun toLatinDigitsLocale(locale: Locale): Locale {
    return try {
        Locale.Builder().setLocale(locale).setUnicodeLocaleKeyword("nu", "latn").build()
    } catch (_: Exception) {
        locale
    }
}

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
