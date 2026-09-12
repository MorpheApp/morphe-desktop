/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.data.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppConfigSortingTest {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Test
    fun `existing config without sort preferences remains compatible`() {
        val config = json.decodeFromString<AppConfig>("{}")

        assertTrue(config.homeAppSortPreferences.isEmpty())
    }

    @Test
    fun `sort preferences survive config serialization`() {
        val preferences = mapOf(
            "ALL" to AppSortPreference("PACKAGE", "DESCENDING"),
            "YOURS" to AppSortPreference("LAST_PATCHED", "ASCENDING"),
            "DEVICE" to AppSortPreference("INSTALLED_VERSION", "DESCENDING"),
        )

        val restored = json.decodeFromString<AppConfig>(
            json.encodeToString(AppConfig.serializer(), AppConfig(homeAppSortPreferences = preferences)),
        )

        assertEquals(preferences, restored.homeAppSortPreferences)
    }
}
