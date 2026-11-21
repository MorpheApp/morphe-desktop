/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.desktop.command.utility

import app.morphe.engine.CacheManager
import app.morphe.engine.util.Logger
import app.morphe.engine.util.formatFileSize
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

    @Option(
        names = ["--app-icons"],
        description = ["Target only cached app icons."],
    )
    private var appIconsOnly: Boolean = false

    override fun call(): Int {
        val categories = buildList {
            if (patchesOnly) add(CacheManager.Category.PATCHES)
            if (logsOnly) add(CacheManager.Category.LOGS)
            if (tempOnly) add(CacheManager.Category.TEMP)
            if (avatarsOnly) add(CacheManager.Category.AVATARS)
            if (appIconsOnly) add(CacheManager.Category.APP_ICONS)
        }.ifEmpty { CacheManager.Category.entries }

        if (info) {
            var totalBytes = 0L
            var totalFiles = 0

            Logger.info(
                buildString {
                    appendLine("Cache status:")
                    categories.forEach { category ->
                        val stats = CacheManager.getDirectoryStats(category.dir)
                        totalBytes += stats.bytes
                        totalFiles += stats.fileCount
                        appendLine(
                            "  ${category.label.padEnd(9)} ${formatFileSize(stats.bytes).padStart(9)}" +
                                "  (${stats.fileCount} file${if (stats.fileCount == 1) "" else "s"})"
                        )
                    }
                    append("  ${"Total".padEnd(9)} ${formatFileSize(totalBytes).padStart(9)}  ($totalFiles file${if (totalFiles == 1) "" else "s"})")
                }
            )
            return EXIT_CODE_SUCCESS
        }

        val result = CacheManager.ClearResult(categories.map { it.clear() })

        Logger.info("Cache cleared.")

        // Deletion failures always shown, even without --info.
        return if (result.success) {
            EXIT_CODE_SUCCESS
        } else {
            Logger.warn("${result.failedFiles} file(s) could not be deleted (may be locked)")
            EXIT_CODE_ERROR
        }
    }
}
