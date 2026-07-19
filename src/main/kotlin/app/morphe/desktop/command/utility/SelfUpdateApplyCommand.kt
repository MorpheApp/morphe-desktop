/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.desktop.command.utility

import app.morphe.engine.isWindows
import app.morphe.engine.update.UpdateVerifier
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.io.File
import java.util.logging.Logger
import kotlin.system.exitProcess

/**
 * The "external updater process" from the self-update architecture ([see
 * app.morphe.engine.update.UpdateInstaller][app.morphe.engine.update.UpdateInstaller]
 * for why this is a hidden subcommand of the same JAR rather than a
 * separate artifact, and for why it's launched from a throwaway *copy* of
 * the currently-running JAR rather than from `--staged` directly).
 *
 * Not intended for interactive use — `hidden = true` keeps it out of
 * `--help`. [app.morphe.engine.update.UpdateInstaller] is the only caller,
 * always with all three options set.
 *
 * This command treats `--staged` (the downloaded JAR) purely as bytes to
 * copy into place — it is never executed, here or anywhere else in this
 * flow, which is precisely what makes the update bootstrap-safe: this
 * command's own behavior is entirely determined by the version that is
 * *running* it (always the current, already-updater-aware release, per
 * [app.morphe.engine.update.UpdateInstaller]), never by whatever the
 * downloaded release happens to contain.
 *
 * Safety invariant this command exists to uphold: **the user is never left
 * without a working installation.** Every failure path restores the backup
 * and relaunches the previously-running (old) JAR rather than leaving
 * [target] missing, half-written, or pointed at a corrupt file.
 */
@Command(
    name = "self-update-apply",
    description = ["Internal: apply a downloaded self-update. Not for interactive use."],
    hidden = true,
)
internal object SelfUpdateApplyCommand : Runnable {

    private val logger = Logger.getLogger(SelfUpdateApplyCommand::class.java.name)
    private val verifier = UpdateVerifier()

    @Option(names = ["--target"], required = true, description = ["JAR to replace (the running installation)."])
    private lateinit var target: File

    @Option(names = ["--staged"], required = true, description = ["Already-downloaded, already-verified new JAR."])
    private lateinit var staged: File

    @Option(names = ["--pid"], required = true, description = ["PID of the process that spawned this updater; waited on before touching target."])
    private var pid: Long = -1

    override fun run() {
        logger.info("self-update-apply: target=${target.absolutePath} staged=${staged.absolutePath} waiting on pid=$pid")

        if (!waitForExit(pid)) {
            // The launching process never terminated within the timeout — it
            // may still be holding the target file open. Touching it now
            // risks corrupting a working install, so we abort without
            // relaunching (the original process is presumably still running
            // and doesn't need us to).
            logger.severe("self-update-apply: pid $pid did not exit in time — aborting without touching $target")
            exitProcess(1)
        }

        val backup = File(target.parentFile, "${target.name}.bak")

        try {
            if (!staged.exists()) error("staged file vanished: ${staged.absolutePath}")

            logger.info("self-update-apply: backing up ${target.name} -> ${backup.name}")
            if (backup.exists()) backup.delete()
            if (target.exists()) target.copyTo(backup, overwrite = true)

            logger.info("self-update-apply: replacing ${target.absolutePath}")
            replace(staged, target)

            if (!verifier.isReadableArchive(target)) {
                error("replacement at ${target.absolutePath} failed integrity check after copy")
            }

            logger.info("self-update-apply: replacement verified — cleaning up and relaunching new version")
            backup.delete()
            runCatching { staged.delete() }

            relaunch(target)
            logger.info("self-update-apply: done")
            exitProcess(0)
        } catch (e: Exception) {
            logger.severe("self-update-apply: FAILED (${e.message}) — rolling back")
            rollbackAndRelaunch(target, backup)
            exitProcess(1)
        }
    }

    /** Poll [ProcessHandle] for [pid] to disappear. True if it exited within the timeout. */
    private fun waitForExit(pid: Long, timeoutMs: Long = 60_000, pollMs: Long = 200): Boolean {
        val handle = ProcessHandle.of(pid).orElse(null) ?: return true // already gone
        val deadline = System.currentTimeMillis() + timeoutMs
        while (handle.isAlive) {
            if (System.currentTimeMillis() > deadline) return false
            Thread.sleep(pollMs)
        }
        return true
    }

    /**
     * Move [from] to [to], falling back to copy+delete when the two paths
     * aren't on the same filesystem (rename then fails atomically rather
     * than partially, per [File.renameTo]'s contract — so the fallback is
     * always safe to attempt).
     */
    private fun replace(from: File, to: File) {
        if (to.exists() && !to.delete()) {
            error("could not remove existing file at ${to.absolutePath} (permissions?)")
        }
        if (!from.renameTo(to)) {
            from.copyTo(to, overwrite = true)
            from.delete()
        }
    }

    /** Restore [backup] over [target] (best-effort) and relaunch whatever ends up there. */
    private fun rollbackAndRelaunch(target: File, backup: File) {
        if (backup.exists()) {
            runCatching {
                if (target.exists()) target.delete()
                backup.copyTo(target, overwrite = true)
                logger.info("self-update-apply: restored backup to ${target.absolutePath}")
            }.onFailure {
                logger.severe("self-update-apply: rollback copy failed: ${it.message} — $target may be unusable")
            }
        }
        if (target.exists()) {
            relaunch(target)
        } else {
            logger.severe("self-update-apply: no working JAR left at ${target.absolutePath} — user must reinstall")
        }
    }

    /** Spawn `java -jar <jar>` detached (no args → GUI mode), independent of this process's lifetime. */
    private fun relaunch(jar: File) {
        val javaBin = File(File(System.getProperty("java.home"), "bin"), if (isWindows()) "java.exe" else "java")
        try {
            ProcessBuilder(javaBin.absolutePath, "-jar", jar.absolutePath)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectErrorStream(true)
                .start()
        } catch (e: Exception) {
            logger.severe("self-update-apply: failed to relaunch ${jar.absolutePath}: ${e.message}")
        }
    }
}
