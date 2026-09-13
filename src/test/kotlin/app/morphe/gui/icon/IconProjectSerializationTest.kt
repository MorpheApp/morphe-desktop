package app.morphe.gui.icon

import app.morphe.gui.data.model.GradientType
import app.morphe.gui.data.model.MorpheFill
import kotlinx.serialization.json.Json
import kotlin.test.Test

class IconProjectSerializationTest {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true; classDiscriminator = "#kind" }

    @Test
    fun roundTrips() {
        val project = IconProject(
            background = MorpheFill.Gradient(
                stops = listOf(MorpheFill.Stop(0f, 0xFF00E5FF.toInt()), MorpheFill.Stop(1f, 0xFF000000.toInt())),
                type = GradientType.CONIC,
            ),
            layers = listOf(
                IconProject.Layer("1", IconProject.LayerContent.Text("Hi"), name = "Title"),
                IconProject.Layer("2", IconProject.LayerContent.Shape(IconProject.ShapeKind.STAR)),
                IconProject.Layer("3", IconProject.LayerContent.Image("/tmp/x.png"), shadow = IconProject.Shadow()),
            ),
        )
        val text = json.encodeToString(IconProject.serializer(), project)
        println("SERIALIZED_OK:\n$text")
        val back = json.decodeFromString(IconProject.serializer(), text)
        check(back == project) { "round trip mismatch" }
        println("ROUNDTRIP_OK")
    }

    @Test
    fun decodesProjectsWrittenBeforeTheFillModelMoved() {
        val legacy = """
            {
                "background": {
                    "#kind": "app.morphe.gui.icon.IconProject.Background.Gradient",
                    "stops": [
                        { "position": 0.0, "argb": -16718337 },
                        { "position": 1.0, "argb": -16777216 }
                    ],
                    "type": "CONIC",
                    "angleDeg": 45.0
                },
                "layers": []
            }
        """
        val decoded = json.decodeFromString(IconProject.serializer(), legacy)
        val bg = decoded.background as MorpheFill.Gradient
        check(bg.type == GradientType.CONIC) { "gradient type lost: ${'$'}{bg.type}" }
        check(bg.stops.size == 2) { "stops lost: ${'$'}{bg.stops}" }

        val reEncoded = json.encodeToString(IconProject.serializer(), decoded)
        check("app.morphe.gui.icon.IconProject.Background.Gradient" in reEncoded) {
            "serial name drifted, saved projects would stop loading"
        }
    }

    @Test
    fun decodesLegacySolidBackground() {
        val legacy = """
            {
                "background": {
                    "#kind": "app.morphe.gui.icon.IconProject.Background.Solid",
                    "argb": -1
                },
                "layers": []
            }
        """
        val decoded = json.decodeFromString(IconProject.serializer(), legacy)
        check(decoded.background == MorpheFill.Solid(-1)) { "solid lost: ${'$'}{decoded.background}" }
    }
}
