/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.data.model

import kotlinx.serialization.json.Json
import kotlin.test.Test

class AppConfigCardFillTest {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "#kind"
    }

    @Test
    fun gradientSurvivesItsOwnTypeProperty() {
        val config = AppConfig(
            cardFills = mapOf(
                "com.google.android.youtube" to MorpheFill.Gradient(
                    stops = listOf(MorpheFill.Stop(0f, 0xFF0E3F6E.toInt()), MorpheFill.Stop(1f, 0xFF00AFAE.toInt())),
                    type = GradientType.RADIAL,
                    angleDeg = 120f,
                ),
                "com.reddit.frontpage" to MorpheFill.Solid(0xFFFF4500.toInt()),
            ),
        )

        val text = json.encodeToString(AppConfig.serializer(), config)
        check("\"#kind\"" in text) { "discriminator missing, sealed fill was not tagged" }
        check("\"type\": \"RADIAL\"" in text) { "gradient type was clobbered by the discriminator:\n$text" }

        val back = json.decodeFromString(AppConfig.serializer(), text)
        check(back.cardFills == config.cardFills) { "card fills did not round trip: ${back.cardFills}" }

        val gradient = back.cardFills.getValue("com.google.android.youtube") as MorpheFill.Gradient
        check(gradient.type == GradientType.RADIAL) { "gradient type lost: ${gradient.type}" }
        check(gradient.angleDeg == 120f) { "angle lost: ${gradient.angleDeg}" }
    }

    @Test
    fun configWrittenBeforeCardFillsExistedStillLoads() {
        val legacy = """
            {
                "themePreference": "DARK",
                "useSimplifiedMode": false
            }
        """
        val decoded = json.decodeFromString(AppConfig.serializer(), legacy)
        check(decoded.cardFills.isEmpty()) { "expected no card fills, got ${decoded.cardFills}" }
        check(decoded.themePreference == "DARK") { "unrelated settings were disturbed" }
    }

    @Test
    fun uncustomisedAppsStayAbsentFromTheMap() {
        val withFill = AppConfig(cardFills = mapOf("a" to MorpheFill.Solid(1), "b" to MorpheFill.Solid(2)))
        val cleared = withFill.copy(cardFills = withFill.cardFills - "a")
        check("a" !in cleared.cardFills) { "cleared app is still present" }
        check(cleared.cardFills.keys == setOf("b")) { "wrong keys: ${cleared.cardFills.keys}" }
    }
}
