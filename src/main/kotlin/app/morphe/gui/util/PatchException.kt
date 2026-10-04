/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.gui.util

import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

open class PatchException(
    message: String,
    val stringRes: StringResource? = null,
    val formatArgs: List<Any> = emptyList(),
    cause: Throwable? = null,
) : Exception(message, cause) {
    suspend fun getUserMessage(): String =
        stringRes?.let { getString(it, *formatArgs.toTypedArray()) } ?: (message ?: "")
}
