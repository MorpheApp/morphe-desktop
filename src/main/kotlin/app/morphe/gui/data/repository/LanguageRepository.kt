/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.data.repository

import java.util.Locale

/** Resolves, formats, and filters supported display languages. */
class LanguageRepository {

    private var lastTargetLocale: Locale? = null
    private var cachedOptions: List<LanguageOption>? = null

    private fun getOptionsForLocale(appLanguageCode: String): List<LanguageOption> {
        val targetLocale = if (appLanguageCode.isBlank() || appLanguageCode.equals(SYSTEM_CODE, ignoreCase = true)) {
            Locale.getDefault()
        } else {
            Locale.forLanguageTag(appLanguageCode)
        }

        if (lastTargetLocale != targetLocale || cachedOptions == null) {
            cachedOptions = buildLanguageOptions(targetLocale)
            lastTargetLocale = targetLocale
        }
        return cachedOptions!!
    }

    /** Returns cached language options for [appLanguageCode], or null if uncomputed. */
    fun getCachedLanguages(appLanguageCode: String = SYSTEM_CODE): List<LanguageOption>? {
        val targetLocale = if (appLanguageCode.isBlank() || appLanguageCode.equals(SYSTEM_CODE, ignoreCase = true)) {
            Locale.getDefault()
        } else {
            Locale.forLanguageTag(appLanguageCode)
        }
        return if (lastTargetLocale == targetLocale) cachedOptions else null
    }

    /** Returns [LanguageOption] for [code], falling back to System if not found. */
    fun getLanguageByCode(code: String, appLanguageCode: String = SYSTEM_CODE): LanguageOption {
        if (code.isBlank() || code.equals(SYSTEM_CODE, ignoreCase = true)) {
            return LanguageOption(
                code = SYSTEM_CODE,
                displayName = "System",
                nativeName = "System",
                flag = "🌐"
            )
        }
        val locale = Locale.forLanguageTag(code)
        val targetLocale = if (appLanguageCode.isBlank() || appLanguageCode.equals(SYSTEM_CODE, ignoreCase = true)) {
            Locale.getDefault()
        } else {
            Locale.forLanguageTag(appLanguageCode)
        }
        return LanguageOption(
            code = code,
            displayName = formatDisplayName(locale, targetLocale),
            nativeName = formatDisplayName(locale, locale),
            flag = flagForLocale(locale)
        )
    }

    /** Filters supported languages by code, display name, or native name. */
    fun filterLanguages(query: String, appLanguageCode: String = SYSTEM_CODE): List<LanguageOption> {
        val options = getOptionsForLocale(appLanguageCode)
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return options
        return options.filter { option ->
            option.code.contains(trimmed, ignoreCase = true) ||
                option.displayName.contains(trimmed, ignoreCase = true) ||
                option.nativeName.contains(trimmed, ignoreCase = true)
        }
    }

    companion object {
        const val SYSTEM_CODE = "system"

        private val DISAMBIGUATED_LANGUAGES = setOf("pt", "zh", "sr")

        private val SUPPORTED_LANGUAGE_TAGS = listOf(
            "en", "af-ZA", "am-ET", "ar-SA", "as-IN", "az-AZ", "be-BY", "bg-BG",
            "bn-BD", "bs-BA", "ca-ES", "ckb-IR", "cs-CZ", "da-DK", "de-DE", "el-GR",
            "es-ES", "et-EE", "eu-ES", "fa-IR", "fi-FI", "fil-PH", "fr-FR", "ga-IE",
            "gl-ES", "gu-IN", "hi-IN", "hr-HR", "hu-HU", "hy-AM", "id-ID", "is-IS",
            "it-IT", "he-IL", "ja-JP", "ka-GE", "kk-KZ", "km-KH", "kmr-TR", "kn-IN",
            "ko-KR", "ky-KG", "lo-LA", "lt-LT", "lv-LV", "mai-IN", "mk-MK", "mn-MN",
            "ms-MY", "mr-IN", "my-MM", "nb-NO", "ne-IN", "nl-NL", "or-IN", "pa-IN",
            "pl-PL", "pt-BR", "pt-PT", "ro-RO", "ru-RU", "si-LK", "sk-SK", "sl-SI",
            "sr-CS", "sr-SP", "sv-SE", "sw-KE", "ta-IN", "te-IN", "th-TH", "tr-TR",
            "uk-UA", "ur-IN", "uz-UZ", "vi-VN", "zh-CN", "zh-TW", "zu-ZA"
        )

        /** Converts a 2-letter country code into its corresponding flag emoji, or globe fallback. */
        fun countryCodeToFlagEmoji(countryCode: String?): String {
            if (countryCode.isNullOrBlank() || countryCode.length != 2) {
                return "🌐"
            }
            val upper = countryCode.uppercase(Locale.ROOT)
            if (upper[0] !in 'A'..'Z' || upper[1] !in 'A'..'Z') {
                return "🌐"
            }
            val firstCodePoint = 0x1F1E6 + (upper[0] - 'A')
            val secondCodePoint = 0x1F1E6 + (upper[1] - 'A')
            return String(Character.toChars(firstCodePoint)) + String(Character.toChars(secondCodePoint))
        }

        /** Resolves flag emoji for [locale]. */
        fun flagForLocale(locale: Locale): String {
            val country = if (locale.country.isNotBlank()) {
                locale.country
            } else if (locale.language.equals("en", ignoreCase = true)) {
                "US"
            } else {
                ""
            }
            return countryCodeToFlagEmoji(country)
        }

        private fun formatDisplayName(locale: Locale, targetLocale: Locale): String {
            val rawLang = locale.getDisplayLanguage(targetLocale)
            var name = rawLang.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(targetLocale) else it.toString()
            }
            if (name.isBlank()) {
                name = locale.language
            }

            val langCode = locale.language.lowercase(Locale.ROOT)
            val country = locale.getDisplayCountry(targetLocale)
            val script = locale.getDisplayScript(targetLocale)

            if (langCode in DISAMBIGUATED_LANGUAGES) {
                if (country.isNotBlank()) {
                    return "$name ($country)"
                } else if (script.isNotBlank()) {
                    return "$name ($script)"
                }
            }
            return name
        }

        private data class InvariantLanguageEntry(
            val tag: String,
            val locale: Locale,
            val nativeName: String,
            val flag: String,
        )

        private val INVARIANT_LANGUAGE_ENTRIES: List<InvariantLanguageEntry> by lazy {
            SUPPORTED_LANGUAGE_TAGS.map { tag ->
                val locale = Locale.forLanguageTag(tag)
                val nativeName = formatDisplayName(locale, locale)
                val flag = flagForLocale(locale)
                InvariantLanguageEntry(tag, locale, nativeName, flag)
            }
        }

        private fun buildLanguageOptions(targetLocale: Locale): List<LanguageOption> {
            val systemOption = LanguageOption(
                code = SYSTEM_CODE,
                displayName = "System",
                nativeName = "System",
                flag = "🌐"
            )

            val parsedOptions = INVARIANT_LANGUAGE_ENTRIES.map { entry ->
                val displayName = formatDisplayName(entry.locale, targetLocale)
                LanguageOption(
                    code = entry.tag,
                    displayName = displayName,
                    nativeName = entry.nativeName,
                    flag = entry.flag
                )
            }

            val englishOption = parsedOptions.first { it.code == "en" }
            val otherOptions = parsedOptions
                .filter { it.code != "en" }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName })

            return listOf(systemOption, englishOption) + otherOptions
        }
    }
}

/** Language metadata and display names for UI selection. */
data class LanguageOption(
    val code: String,
    val displayName: String,
    val nativeName: String,
    val flag: String
)

fun String.isRtlLanguage(): Boolean {
    val primaryCode = this.substringBefore('-').lowercase()
    return primaryCode in setOf("ar", "fa", "he", "iw", "ur", "ckb")
}
