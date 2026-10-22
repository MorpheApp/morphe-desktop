/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.desktop.command

import app.morphe.engine.patches.RemotePatchSourceFactory
import java.io.File
import picocli.CommandLine
import picocli.CommandLine.Model.CommandSpec

internal fun checkFileExistsOrIsUrl(files: Set<File>, spec: CommandSpec): Set<File> {
    files.firstOrNull {
        !it.exists() && RemotePatchSourceFactory.parse(it.invariantSeparatorsPath) == null
    }?.let {
        throw CommandLine.ParameterException(spec.commandLine(), "${it.name} can not be found")
    }
    return files
}
