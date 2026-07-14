/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe

import app.morphe.library.logging.Logger
import java.awt.GraphicsEnvironment

fun main(args: Array<String>) {
    if (args.isEmpty() && !GraphicsEnvironment.isHeadless()) {
        app.morphe.gui.launchGui(args)
    } else {
        Logger.setDefault()

        if (GraphicsEnvironment.isHeadless()){
            val logger = java.util.logging.Logger.getLogger("app.morphe.MorpheLauncher")
            logger.info("Running in Headless environment, falling back to CLI mode.")
        }

        picocli.CommandLine(app.morphe.desktop.command.MainCommand)
            .execute(*args)
            .let(System::exit)
    }
}
