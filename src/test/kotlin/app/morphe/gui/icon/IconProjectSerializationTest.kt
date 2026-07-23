package app.morphe.gui.icon

import kotlinx.serialization.json.Json
import kotlin.test.Test

class IconProjectSerializationTest {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true; encodeDefaults = true; classDiscriminator = "#kind" }

    @Test
    fun roundTrips() {
        val project = IconProject(
            background = IconProject.Background.Gradient(
                stops = listOf(IconProject.Background.Stop(0f, 0xFF00E5FF.toInt()), IconProject.Background.Stop(1f, 0xFF000000.toInt())),
                type = IconProject.GradientType.CONIC,
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
}
