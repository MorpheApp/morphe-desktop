/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.icon

import kotlinx.serialization.json.Json
import java.io.File

/**
 * Persists an [IconProject] to `morphe-data/icons/<packageName>/project.json` so a
 * custom icon can be reopened and edited later instead of rebuilt from scratch.
 * The generated mipmap folder is derived output; this is the editable source.
 */
object IconProjectStore {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
        // Use a discriminator that can't collide with a data-class property (e.g.
        // Gradient.type) — a "#"-prefixed key is not a valid Kotlin identifier.
        classDiscriminator = "#kind"
    }

    private fun file(packageName: String) = File(IconExporter.projectDir(packageName), "project.json")

    fun save(project: IconProject, packageName: String) {
        runCatching { file(packageName).writeText(json.encodeToString(IconProject.serializer(), project)) }
            .onFailure { System.err.println("IconProjectStore.save failed: ${it.message}"); it.printStackTrace() }
    }

    fun load(packageName: String): IconProject? {
        val f = file(packageName)
        if (!f.exists()) return null
        return runCatching { json.decodeFromString(IconProject.serializer(), f.readText()) }
            .onFailure { System.err.println("IconProjectStore.load failed: ${it.message}") }
            .getOrNull()
    }
}
