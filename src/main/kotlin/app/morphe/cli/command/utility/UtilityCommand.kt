/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.cli.command.utility

import picocli.CommandLine

@CommandLine.Command(
    name = "utility",
    description = ["Commands for utility purposes."],
    subcommands = [InstallCommand::class, UninstallCommand::class],
)
internal object UtilityCommand
