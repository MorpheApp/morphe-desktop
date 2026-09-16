/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.icon

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import app.morphe.engine.MorpheData
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * User-saved colours, shared across every colour control in the Icon Studio and
 * persisted to `morphe-data/icons/swatches.json`. Backed by a snapshot list so the
 * UI recomposes when a colour is added/removed. Capped at [MAX] slots.
 */
object CustomSwatches {
    const val MAX = 12

    private val file by lazy { File(MorpheData.iconsDir, "swatches.json") }
    private val json = Json { ignoreUnknownKeys = true }

    val colors: SnapshotStateList<Int> = mutableStateListOf<Int>().also { list ->
        runCatching { if (file.exists()) list.addAll(json.decodeFromString<List<Int>>(file.readText())) }
    }

    val isFull: Boolean get() = colors.size >= MAX

    fun add(argb: Int) {
        if (argb !in colors && colors.size < MAX) { colors.add(argb); save() }
    }

    fun remove(argb: Int) {
        if (colors.remove(argb)) save()
    }

    private fun save() {
        runCatching { file.writeText(json.encodeToString(colors.toList())) }
    }
}
