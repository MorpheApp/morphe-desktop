/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.icon

import kotlinx.serialization.json.Json
import java.io.File

/** Persists and reloads editable [IconProject] state from disk. */
object IconProjectStore {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
        // Use prefixed discriminator to prevent collision with data class properties.
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
