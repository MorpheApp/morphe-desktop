/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.ui.components.color

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import app.morphe.engine.MorpheData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

val MORPHE_SWATCHES = listOf(
    0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFFFF0033.toInt(), 0xFF00E5FF.toInt(),
    0xFF1DE9B6.toInt(), 0xFFFFC400.toInt(), 0xFF7C4DFF.toInt(), 0xFFFF6D00.toInt(),
)

object CustomSwatches {
    const val MAX = 12

    private val file by lazy { File(MorpheData.iconsDir, "swatches.json") }
    private val json = Json { ignoreUnknownKeys = true }
    private var isLoaded = false

    val colors: SnapshotStateList<Int> = mutableStateListOf<Int>().also {
        load()
    }

    fun load() {
        if (isLoaded) return
        isLoaded = true
        CoroutineScope(Dispatchers.IO).launch {
            val loaded = runCatching {
                if (file.exists()) {
                    json.decodeFromString<List<Int>>(file.readText())
                } else null
            }.getOrNull() ?: return@launch
            withContext(Dispatchers.Main) {
                colors.clear()
                colors.addAll(loaded)
            }
        }
    }

    val isFull: Boolean get() = colors.size >= MAX

    fun add(argb: Int) {
        if (argb !in colors && colors.size < MAX) { colors.add(argb); save() }
    }

    fun remove(argb: Int) {
        if (colors.remove(argb)) save()
    }

    private fun save() {
        val snapshot = colors.toList()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { file.writeText(json.encodeToString(snapshot)) }
        }
    }
}
