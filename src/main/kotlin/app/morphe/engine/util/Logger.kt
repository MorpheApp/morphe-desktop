/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.util

import app.morphe.engine.MorpheComponents
import app.morphe.engine.MorpheData
import app.morphe.engine.UpdateChecker
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.MessageFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.logging.Handler
import java.util.logging.Level as JulLevel
import java.util.logging.LogRecord
import java.util.logging.Logger as JulLogger

/**
 * Unified logger for both CLI and GUI.
 *
 * Log file location: `<MorpheData.root>/logs/morphe-<timestamp>.log` — JAR-adjacent
 * `morphe-data/logs/` for shipped jars, `~/morphe/logs/` for IDE/dev runs.
 * See [app.morphe.engine.MorpheData] for the full resolution + fallback rules.
 */
object Logger {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val fileTimestampFormat = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
    private var logFile: File? = null
    private var initialized = false

    enum class Level {
        DEBUG, INFO, WARN, ERROR
    }

    /**
     * Initialize the GUI logger. Call once at GUI application startup.
     */
    fun init() {
        if (initialized) return

        try {
            // Install JUL bridge early so logs during startup (e.g. MorpheData) are unified
            installJulBridge()

            val logsDir = MorpheData.logsDir
            val timestamp = fileTimestampFormat.format(Date())
            logFile = File(logsDir, "morphe-$timestamp.log")

            // Write startup diagnostic banner to console and log file (without log markers)
            val banner = buildString {
                appendLine("=".repeat(60))
                appendLine("Morphe-GUI Started")
                appendLine("Version: ${UpdateChecker.currentVersion() ?: "dev"}")
                appendLine("morphe-patcher: ${MorpheComponents.patcherVersion ?: "unknown"}")
                appendLine("morphe-library: ${MorpheComponents.libraryVersion ?: "unknown"}")
                appendLine("OS: ${System.getProperty("os.name")} ${System.getProperty("os.version")} (${System.getProperty("os.arch")})")
                appendLine("Java: ${System.getProperty("java.version")} (${System.getProperty("java.vendor")}) ${System.getProperty("sun.arch.data.model")}-bit")
                appendLine("Memory: ${Runtime.getRuntime().maxMemory() / 1024 / 1024} MB max")
                appendLine("User: ${System.getProperty("user.name")}")
                appendLine("App Data: ${MorpheData.root.absolutePath}")
                appendLine("Working Dir: ${System.getProperty("user.dir")}")
                appendLine("=".repeat(60))
            }
            print(banner)
            logFile?.appendText(banner)

            initialized = true
        } catch (e: Exception) {
            System.err.println("Failed to initialize logger: ${e.message}")
        }
    }

    /**
     * Bridges java.util.logging for `app.morphe` into this Logger.
     * Sets useParentHandlers = false on "app.morphe" to isolate it from the root ConsoleHandler.
     */
    private fun installJulBridge() {
        val morpheLogger = JulLogger.getLogger("app.morphe")
        morpheLogger.useParentHandlers = false

        for (handler in morpheLogger.handlers.toList()) {
            if (handler is JulBridgeHandler) {
                morpheLogger.removeHandler(handler)
            }
        }

        morpheLogger.addHandler(JulBridgeHandler())
    }

    private class JulBridgeHandler : Handler() {
        override fun publish(record: LogRecord) {
            val rawMessage = record.message ?: return
            if (rawMessage.isBlank()) return

            val message = runCatching {
                val params = record.parameters
                if (params != null && params.isNotEmpty()) {
                    MessageFormat.format(rawMessage, *params)
                } else rawMessage
            }.getOrDefault(rawMessage)

            when {
                record.level.intValue() >= JulLevel.SEVERE.intValue() -> {
                    if (record.thrown != null) {
                        error(message, record.thrown)
                    } else {
                        error(message)
                    }
                }
                record.level.intValue() >= JulLevel.WARNING.intValue() -> {
                    warn(message)
                }
                record.level.intValue() >= JulLevel.INFO.intValue() -> {
                    info(message)
                }
                else -> {
                    debug(message)
                }
            }
        }

        override fun flush() {}
        override fun close() {}
    }

    fun debug(message: String) = log(Level.DEBUG, message)
    fun info(message: String) = log(Level.INFO, message)
    fun warn(message: String) = log(Level.WARN, message)
    fun error(message: String) = log(Level.ERROR, message)

    fun warn(message: String, throwable: Throwable) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        log(Level.WARN, "$message\n$sw")
    }

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
        val logLine = if (logFile != null) {
            val timestamp = dateFormat.format(Date())
            "[$timestamp] [${level.name}] $message"
        } else {
            "${level.name}: $message"
        }

        // Print to console
        when (level) {
            Level.ERROR -> System.err.println(logLine)
            else -> println(logLine)
        }

        // Write to file
        try {
            logFile?.appendText("$logLine\n")
        } catch (e: Exception) {
            System.err.println("Failed to write to log file: ${e.message}")
        }
    }

    /**
     * Get the current session's log file for export or viewing.
     */
    fun getLogFile(): File? = logFile

    /**
     * Get all log files in the logs directory.
     */
    fun getAllLogFiles(): List<File> {
        val logsDir = MorpheData.logsDir
        return logsDir.listFiles()
            ?.filter { it.name.startsWith("morphe-") && it.name.endsWith(".log") }
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
}
