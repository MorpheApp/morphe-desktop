/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.desktop.command.utility

import picocli.CommandLine

@CommandLine.Command(
    name = "utility",
    description = ["Commands for utility purposes."],
    subcommands = [InstallCommand::class, UninstallCommand::class, ClearCacheCommand::class],
)
internal object UtilityCommand
