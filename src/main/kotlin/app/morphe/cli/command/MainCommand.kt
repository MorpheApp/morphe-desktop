/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.cli.command

import app.morphe.cli.command.utility.UtilityCommand
import app.morphe.library.logging.Logger
import org.jetbrains.annotations.VisibleForTesting
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.IVersionProvider
import java.util.Properties
import kotlin.system.exitProcess

fun cliMain(args: Array<String>) {
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
                    "Morphe CLI v${it.getProperty("version")}"
                }
            } ?: "Morphe CLI",
        )
}

@Command(
    name = "morphe-cli",
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
