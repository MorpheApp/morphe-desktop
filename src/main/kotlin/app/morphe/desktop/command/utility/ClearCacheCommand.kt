/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.desktop.command.utility

import app.morphe.engine.CacheManager
import app.morphe.engine.util.Logger
import java.util.concurrent.Callable
import picocli.CommandLine.Command
import picocli.CommandLine.Option

@Command(
    name = "clear-cache",
    description = ["Delete cached patch files, logs, and temporary files."],
)
internal object ClearCacheCommand : Callable<Int> {
    private const val EXIT_CODE_SUCCESS = 0
    private const val EXIT_CODE_ERROR = 1

    @Option(
        names = ["--info"],
        description = ["Show a per-category breakdown of what was cleared and how much space was freed."],
    )
    private var info: Boolean = false

    override fun call(): Int {
        val result = CacheManager.clearCaches()

        if (info) {
            Logger.info(
                buildString {
                    appendLine("Cache cleared:")
                    result.perDirectory.forEach { dir ->
                        appendLine(
                            "  ${dir.label.padEnd(8)} ${humanReadable(dir.bytesFreed).padStart(9)}" +
                                "  (${dir.filesDeleted} file${if (dir.filesDeleted == 1) "" else "s"})"
                        )
                    }
                    append("  ${"Total".padEnd(8)} ${humanReadable(result.bytesFreed).padStart(9)}")
                }
            )
        } else {
            Logger.info("Cache cleared.")
        }

        // Deletion failures always shown, even without --info.
        return if (result.success) {
            EXIT_CODE_SUCCESS
        } else {
            Logger.warn("${result.failedFiles} file(s) could not be deleted (may be locked)")
            EXIT_CODE_ERROR
        }
    }

    private fun humanReadable(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    }
}
