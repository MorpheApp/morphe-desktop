/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.data.repository

import java.util.Locale

/**
 * Repository responsible for loading, formatting, and filtering supported desktop languages.
 */
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

    /**
     * Returns the full list of supported languages, with "System" pinned at index 0,
     * "English" at index 1, and the remaining options sorted alphabetically by display name.
     */
    fun getSupportedLanguages(appLanguageCode: String = SYSTEM_CODE): List<LanguageOption> = getOptionsForLocale(appLanguageCode)

    /**
     * Finds a [LanguageOption] by its language code, falling back to the System option if not found.
     */
    fun getLanguageByCode(code: String, appLanguageCode: String = SYSTEM_CODE): LanguageOption {
        val options = getOptionsForLocale(appLanguageCode)
        return options.firstOrNull { it.code.equals(code, ignoreCase = true) }
            ?: options.first()
    }

    /**
     * Filters supported languages in real time by language code, display name, or native name.
     */
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

        /**
         * Calculates a 2-letter country flag emoji dynamically using Unicode offsets (0x1F1E6).
         * Falls back to a globe emoji (🌐) for missing or invalid region codes.
         */
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

        /**
         * Resolves the appropriate flag emoji for a [Locale].
         */
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

        private fun buildLanguageOptions(targetLocale: Locale): List<LanguageOption> {
            val systemOption = LanguageOption(
                code = SYSTEM_CODE,
                displayName = "System",
                nativeName = "System",
                flag = "🌐"
            )

            val parsedOptions = SUPPORTED_LANGUAGE_TAGS.map { tag ->
                val locale = Locale.forLanguageTag(tag)
                val displayName = formatDisplayName(locale, targetLocale)

                val nativeName = formatDisplayName(locale, locale)
                val flag = flagForLocale(locale)
                LanguageOption(
                    code = tag,
                    displayName = displayName,
                    nativeName = nativeName,
                    flag = flag
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

/**
 * Represents a language option for UI display and selection.
 *
 * @property code BCP-47 / ISO language tag, or "system" for system default.
 * @property displayName Language name formatted in English / display language (e.g. "Portuguese (Brazil)").
 * @property nativeName Language name formatted in its native locale (e.g. "Português (Brasil)").
 * @property flag Country flag emoji computed dynamically or "🌐" fallback.
 */
data class LanguageOption(
    val code: String,
    val displayName: String,
    val nativeName: String,
    val flag: String
)

fun String.isRtlLanguage(): Boolean {
    // Extract the primary subtag (e.g., "ar" from "ar-SA")
    val primaryCode = this.substringBefore('-').lowercase()
    return primaryCode in setOf("ar", "fa", "he", "iw", "ur", "ckb")
}
