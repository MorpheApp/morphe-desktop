/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.desktop.command.utility

import picocli.CommandLine

@CommandLine.Command(
    name = "utility",
    description = ["Commands for utility purposes."],
    subcommands = [
        InstallCommand::class,
        UninstallCommand::class,
        ClearCacheCommand::class,
        // Internal: spawned by UpdateInstaller as the detached updater
        // process, never invoked directly by a user. Hidden from --help.
        SelfUpdateApplyCommand::class,
    ],
)
internal object UtilityCommand
