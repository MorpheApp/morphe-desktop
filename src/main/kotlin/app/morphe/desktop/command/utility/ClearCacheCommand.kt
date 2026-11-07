/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.desktop.command.utility

import app.morphe.engine.CacheManager
import app.morphe.engine.MorpheData
import app.morphe.engine.util.Logger
import app.morphe.engine.workspace.WorkspaceManager
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
        description = ["Show current cache usage breakdown without clearing."],
    )
    private var info: Boolean = false

    @Option(
        names = ["--patches"],
        description = ["Target only cached patch bundles."],
    )
    private var patchesOnly: Boolean = false

    @Option(
        names = ["--logs"],
        description = ["Target only log files."],
    )
    private var logsOnly: Boolean = false

    @Option(
        names = ["--temp"],
        description = ["Target only temporary scratch files."],
    )
    private var tempOnly: Boolean = false

    @Option(
        names = ["--avatars"],
        description = ["Target only cached remote avatars."],
    )
    private var avatarsOnly: Boolean = false

    override fun call(): Int {
        val anyCategorySelected = patchesOnly || logsOnly || tempOnly || avatarsOnly

        if (info) {
            val categories = buildList {
                if (!anyCategorySelected || patchesOnly) add("Patches" to MorpheData.patchesDir)
                if (!anyCategorySelected || logsOnly) add("Logs" to MorpheData.logsDir)
                if (!anyCategorySelected || tempOnly) add("Temp" to WorkspaceManager.scratchDir)
                if (!anyCategorySelected || avatarsOnly) add("Avatars" to MorpheData.avatarsDir)
            }
            var totalBytes = 0L
            var totalFiles = 0

            Logger.info(
                buildString {
                    appendLine("Cache status:")
                    categories.forEach { (label, dir) ->
                        val stats = CacheManager.getDirectoryStats(dir)
                        totalBytes += stats.bytes
                        totalFiles += stats.fileCount
                        appendLine(
                            "  ${label.padEnd(8)} ${humanReadable(stats.bytes).padStart(9)}" +
                                "  (${stats.fileCount} file${if (stats.fileCount == 1) "" else "s"})"
                        )
                    }
                    append("  ${"Total".padEnd(8)} ${humanReadable(totalBytes).padStart(9)}  ($totalFiles file${if (totalFiles == 1) "" else "s"})")
                }
            )
            return EXIT_CODE_SUCCESS
        }

        val result = if (anyCategorySelected) {
            val results = buildList {
                if (patchesOnly) add(CacheManager.clearDirectoryContents("Patches", MorpheData.patchesDir))
                if (logsOnly) add(CacheManager.clearDirectoryContents("Logs", MorpheData.logsDir))
                if (tempOnly) add(WorkspaceManager.clearScratch())
                if (avatarsOnly) add(CacheManager.clearDirectoryContents("Avatars", MorpheData.avatarsDir))
            }
            CacheManager.ClearResult(results)
        } else {
            CacheManager.clearCaches()
        }

        Logger.info("Cache cleared.")

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
