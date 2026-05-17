/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.gui.util

import app.morphe.gui.data.constants.AppConstants
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * Simple file logger with rotation support.
 *
 * Log file location: `<MorpheData.root>/logs/morphe-gui.log` — JAR-adjacent
 * `morphe-data/logs/` for shipped jars, `~/morphe/logs/` for IDE/dev runs.
 * See [app.morphe.engine.MorpheData] for the full resolution + fallback rules.
 */
object Logger {

    private const val MAX_LOG_SIZE = 2 * 1024 * 1024 // 2 MB
    private const val MAX_LOG_FILES = 3
    private const val MAX_LINES_TO_KEEP = 5000 // Keep last 5000 lines on startup
    private const val LOG_FILE_NAME = "morphe-gui.log"

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private var logFile: File? = null
    private var initialized = false

    enum class Level {
        DEBUG, INFO, WARN, ERROR
    }

    /**
     * Initialize the logger. Call once at app startup.
     */
    fun init() {
        if (initialized) return

        try {
            val logsDir = FileUtils.getLogsDir()
            logFile = File(logsDir, LOG_FILE_NAME)

            // Trim log file if it's too large (keep only last N lines)
            trimLogFile()

            // Rotate if needed
            rotateIfNeeded()

            // Log startup info
            info("=".repeat(60))
            info("Morphe-GUI Started")
            info("Version: ${AppConstants.APP_VERSION}")
            info("OS: ${System.getProperty("os.name")} ${System.getProperty("os.version")} (${System.getProperty("os.arch")})")
            info("Java: ${System.getProperty("java.version")} (${System.getProperty("java.vendor")}) ${System.getProperty("sun.arch.data.model")}-bit")
            info("Memory: ${Runtime.getRuntime().maxMemory() / 1024 / 1024} MB max")
            info("User: ${System.getProperty("user.name")}")
            info("App Data: ${FileUtils.getAppDataDir().absolutePath}")
            info("Working Dir: ${System.getProperty("user.dir")}")
            info("=".repeat(60))

            initialized = true
        } catch (e: Exception) {
            System.err.println("Failed to initialize logger: ${e.message}")
        }
    }

    /**
     * Trim log file to keep only the last MAX_LINES_TO_KEEP lines.
     */
    private fun trimLogFile() {
        val file = logFile ?: return
        if (!file.exists()) return

        try {
            val lines = file.readLines()
            if (lines.size > MAX_LINES_TO_KEEP) {
                val trimmedLines = lines.takeLast(MAX_LINES_TO_KEEP)
                file.writeText(trimmedLines.joinToString("\n") + "\n")
            }
        } catch (e: Exception) {
            System.err.println("Failed to trim log file: ${e.message}")
        }
    }

    fun debug(message: String) = log(Level.DEBUG, message)
    fun info(message: String) = log(Level.INFO, message)
    fun warn(message: String) = log(Level.WARN, message)
    fun error(message: String) = log(Level.ERROR, message)

    fun error(message: String, throwable: Throwable) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        log(Level.ERROR, "$message\n$sw")
    }

    /**
     * Log a CLI command execution.
     */
    fun logCliCommand(command: List<String>) {
        info("CLI Command: ${command.joinToString(" ")}")
    }

    /**
     * Log CLI output.
     */
    fun logCliOutput(output: String) {
        if (output.isNotBlank()) {
            debug("CLI Output: $output")
        }
    }

    private fun log(level: Level, message: String) {
        val timestamp = dateFormat.format(Date())
        val logLine = "[$timestamp] [${level.name.padEnd(5)}] $message"

        // Print to console
        when (level) {
            Level.ERROR -> System.err.println(logLine)
            else -> println(logLine)
        }

        // Write to file
        try {
            logFile?.let { file ->
                rotateIfNeeded()
                file.appendText("$logLine\n")
            }
        } catch (e: Exception) {
            System.err.println("Failed to write to log file: ${e.message}")
        }
    }

    private fun rotateIfNeeded() {
        val file = logFile ?: return
        if (!file.exists()) return
        if (file.length() < MAX_LOG_SIZE) return

        try {
            // Shift existing log files
            for (i in MAX_LOG_FILES - 1 downTo 1) {
                val older = File(file.parent, "$LOG_FILE_NAME.$i")
                val newer = if (i == 1) file else File(file.parent, "$LOG_FILE_NAME.${i - 1}")
                if (newer.exists()) {
                    if (older.exists()) older.delete()
                    newer.renameTo(older)
                }
            }

            // Create fresh log file
            file.createNewFile()
        } catch (e: Exception) {
            System.err.println("Failed to rotate logs: ${e.message}")
        }
    }

    /**
     * Get the current log file for export.
     */
    fun getLogFile(): File? = logFile

    /**
     * Get all log files for export.
     */
    fun getAllLogFiles(): List<File> {
        val logsDir = FileUtils.getLogsDir()
        return logsDir.listFiles()
            ?.filter { it.name.startsWith(LOG_FILE_NAME) }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    /**
     * Export logs to a specified location.
     */
    fun exportLogs(destination: File): Boolean {
        return try {
            val logs = getAllLogFiles()
            if (logs.isEmpty()) return false

            if (logs.size == 1) {
                logs.first().copyTo(destination, overwrite = true)
            } else {
                // Combine all logs into one file
                destination.writeText("")
                logs.reversed().forEach { log ->
                    destination.appendText("=== ${log.name} ===\n")
                    destination.appendText(log.readText())
                    destination.appendText("\n")
                }
            }
            true
        } catch (e: Exception) {
            error("Failed to export logs", e)
            false
        }
    }

    /**
     * Clear all log files.
     */
    fun clearLogs(): Boolean {
        return try {
            val logsDir = FileUtils.getLogsDir()
            logsDir.listFiles()?.forEach { it.delete() }
            logFile?.createNewFile()
            info("Logs cleared")
            true
        } catch (e: Exception) {
            System.err.println("Failed to clear logs: ${e.message}")
            false
        }
    }

    /**
     * Get the total size of all log files.
     */
    fun getLogsSize(): Long {
        return try {
            FileUtils.getLogsDir().walkTopDown()
                .filter { it.isFile }
                .sumOf { it.length() }
        } catch (e: Exception) {
            0L
        }
    }
}
