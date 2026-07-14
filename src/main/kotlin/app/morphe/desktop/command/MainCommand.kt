/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.desktop.command

import app.morphe.desktop.command.utility.UtilityCommand
import app.morphe.library.logging.Logger
import org.jetbrains.annotations.VisibleForTesting
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.IVersionProvider
import java.util.Properties
import kotlin.system.exitProcess

fun desktopMain(args: Array<String>) {
    Logger.setDefault()
    val exitCode = CommandLine(MainCommand).execute(*args)
    exitProcess(exitCode)
}

private object CLIVersionProvider : IVersionProvider {
    override fun getVersion() =
        arrayOf(
            MainCommand::class.java.getResourceAsStream(
                "/app/morphe/cli/version.properties",
            )?.use { stream ->
                Properties().apply {
                    load(stream)
                }.let {
                    "Morphe Desktop v${it.getProperty("version")}"
                }
            } ?: "Morphe Desktop",
        )
}

@Command(
    name = "morphe-desktop",
    description = ["Command line application to use Morphe."],
    mixinStandardHelpOptions = true,
    versionProvider = CLIVersionProvider::class,
    subcommands = [
        PatchCommand::class,
        ListPatchesCommand::class,
        ListCompatibleVersions::class,
        OptionsCommand::class,
        UtilityCommand::class,
    ]
)
@VisibleForTesting
internal object MainCommand
