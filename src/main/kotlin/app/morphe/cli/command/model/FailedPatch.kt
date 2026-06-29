/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.cli.command.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@ExperimentalSerializationApi
@Serializable
data class FailedPatch(
    val patch: SerializablePatch,
    val reason: String
)
