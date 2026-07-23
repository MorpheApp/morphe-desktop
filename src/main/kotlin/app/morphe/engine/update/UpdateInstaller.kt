/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.engine.update

import app.morphe.engine.MorpheData
import app.morphe.engine.isWindows
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.logging.Logger

/**
 * Coordinates handing a verified, staged update JAR off to a **separate
 * process** that performs the actual file replacement — this class itself
 * never overwrites the running JAR (a running process can't safely replace
 * its own backing file on every platform, and Windows will outright refuse
 * to on most JVMs since the file is memory-mapped/locked).
 *
 * ## Why the updater runs from a copy of the *current* JAR, not the downloaded one
 *
 * Earlier revisions of this class launched the **downloaded** JAR itself as
 * the updater process (`java -jar <stagedJar> utility self-update-apply
 * ...`), reasoning that shipping a second updater artifact was unnecessary
 * duplication. That reasoning about avoiding a second artifact still holds —
 * but launching the *downloaded* JAR specifically was wrong, for two
 * independent reasons:
 *
 *  1. **Bootstrap dependency.** It made every update silently depend on the
 *     *target* version already containing `self-update-apply`. The very
 *     first updater-enabled release has nothing upstream that satisfies
 *     that — the downloaded JAR exits with "Unknown command" and the update
 *     fails. More generally, it coupled "can I update?" to the internals of
 *     whatever happens to be the latest upstream release, which this class
 *     has no control over.
 *  2. **Self-lock on Windows.** Even ignoring (1), if the downloaded JAR
 *     *did* contain the updater and were launched in place, replacing
 *     [MorpheData.currentJarFile] would still need the *updater process
 *     itself* to not be the thing holding that file open — but the running
 *     app is the one being replaced, and Windows won't let anything delete
 *     or overwrite a JAR a live JVM loaded classes from.
 *
 * The fix addresses both at once: [launchUpdater] copies
 * [MorpheData.currentJarFile] — the version that's *actually running right
 * now*, which by definition already contains this exact updater code — to a
 * disposable temp file, and launches **that copy** with `utility
 * self-update-apply`. The downloaded JAR ([stagedJar]) is passed only as
 * `--staged`, a plain path the updater process copies bytes from; it is
 * never executed, so it doesn't matter whether it contains updater code,
 * an older format, or nothing update-related at all. And because the
 * updater process runs from a copy rather than from
 * [MorpheData.currentJarFile] directly, it holds no lock on the file it
 * needs to replace.
 *
 * The net result: **any version that has this fix can update to any other
 * version**, regardless of what the other version contains. Compare to how
 * IntelliJ/JetBrains Toolbox and VS Code updaters work — the *installed*
 * app always drives its own update using its own bundled updater, and the
 * downloaded payload is inert data until installed.
 *
 * ## Why still no second artifact
 *
 * The updater process is still just this same JAR (via the hidden
 * [app.morphe.desktop.command.utility.SelfUpdateApplyCommand] subcommand)
 * rather than a dedicated updater binary — morphe-desktop's release
 * pipeline ships exactly one asset (see `.releaserc`), and a second
 * artifact would need its own build/sign/version story for no real benefit
 * here. The only change is *which copy* of this JAR plays that role.
 *
 * Flow:
 * 1. [launchUpdater] copies [MorpheData.currentJarFile] to a throwaway file
 *    and launches it as `java -jar <copy> utility self-update-apply
 *    --target <currentJarFile> --staged <stagedJar> --pid <ourPid>`, detached.
 * 2. The caller (GUI ViewModel / CLI command) then exits the current
 *    process — the spawned process is waiting on our PID to disappear
 *    before it will touch [MorpheData.currentJarFile].
 * 3. That process backs up the target, replaces it with the bytes from
 *    `--staged`, verifies the replacement, and relaunches — restoring the
 *    backup and relaunching the *old* JAR instead if anything went wrong.
 *    See [app.morphe.cli.command.utility.SelfUpdateApplyCommand] for that
 *    half — it is unchanged by this fix, since it already only ever reads
 *    `--staged` as data and never executed it.
 */
class UpdateInstaller {

    private val logger = Logger.getLogger(UpdateInstaller::class.java.name)

    /**
     * Launch the detached updater process for [stagedJar]. Does **not**
     * exit the current process — callers must do that themselves right
     * after a [LaunchResult.Success] (see class doc for why the ordering
     * matters).
     *
     * Runs on [Dispatchers.IO]: preparing the updater copy copies the
     * entire running JAR (tens of MB), which would otherwise block the
     * caller's dispatcher.
     */
    suspend fun launchUpdater(stagedJar: File): LaunchResult = withContext(Dispatchers.IO) {
        val targetJar = MorpheData.currentJarFile
            ?: return@withContext LaunchResult.Unsupported(
                "Not running from a packaged JAR (IDE/dev run) — self-update can't replace anything."
            )
        if (!stagedJar.exists()) {
            return@withContext LaunchResult.Failure("Staged update file is missing: ${stagedJar.absolutePath}")
        }

        val javaBin = resolveJavaBinary()
            ?: return@withContext LaunchResult.Failure("Could not locate the Java runtime (java.home unset).")

        // Best-effort: clear out copies left behind by a previous attempt
        // (e.g. one that crashed before relaunching) before adding a new
        // one. Never fails the current update if this can't fully clean up —
        // a few stray MB in the temp dir is harmless.
        sweepOrphanedUpdaterCopies()

        val updaterCopy = try {
            makeUpdaterCopy(targetJar)
        } catch (e: Exception) {
            logger.severe("UpdateInstaller: failed to prepare updater copy: ${e.message}")
            return@withContext LaunchResult.Failure("Could not prepare the updater: ${e.message}")
        }

        val ourPid = ProcessHandle.current().pid()
        val logFile = File(MorpheData.logsDir, "self-update.log")

        // Note --target and the running JAR (`-jar updaterCopy`) are
        // deliberately different files — see class doc, "Self-lock on
        // Windows". `--staged` (the downloaded JAR) is passed as inert data
        // and is never executed by anything in this flow.
        val command = listOf(
            javaBin.absolutePath,
            "-jar", updaterCopy.absolutePath,
            "utility", "self-update-apply",
            "--target", targetJar.absolutePath,
            "--staged", stagedJar.absolutePath,
            "--pid", ourPid.toString(),
        )

        return@withContext try {
            logger.info("UpdateInstaller: launching updater process: ${command.joinToString(" ")}")
            ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))
                .redirectErrorStream(true)
                // No inheritIO / no waitFor: this must survive and keep
                // running after the current (parent) process exits.
                .start()
            LaunchResult.Success
        } catch (e: Exception) {
            logger.severe("UpdateInstaller: failed to launch updater process: ${e.message}")
            LaunchResult.Failure("Could not start the updater process: ${e.message}")
        }
    }

    /**
     * Copy [targetJar] (the running installation) to a fresh, uniquely
     * named file in [updaterCopyDir]. This copy — not [targetJar] itself —
     * is what actually gets launched as the updater process; see class doc.
     */
    private fun makeUpdaterCopy(targetJar: File): File {
        val dir = updaterCopyDir().also { it.mkdirs() }
        val copy = File(dir, "updater-${System.currentTimeMillis()}-${ProcessHandle.current().pid()}.jar")
        targetJar.copyTo(copy, overwrite = true)
        return copy
    }

    /**
     * Delete any updater copies left over from earlier update attempts.
     * Safe to call any time: a copy is only ever needed for the few moments
     * between being spawned and relaunching the new version, so anything
     * still present here by the time a *new* update starts is orphaned —
     * most commonly because the process that made it crashed, or the
     * machine lost power, before it could relaunch and exit cleanly. This
     * process (not the orphaned one) still holds no lock on these files, so
     * deleting them here is always safe.
     */
    private fun sweepOrphanedUpdaterCopies() {
        runCatching { updaterCopyDir().listFiles()?.forEach { it.delete() } }
    }

    private fun updaterCopyDir(): File = File(MorpheData.tmpDir, "self-update/updater-copies")

    /**
     * `<java.home>/bin/java[.exe]` — the exact JVM currently running us, so
     * the spawned process needs no PATH lookup and matches our toolchain.
     */
    private fun resolveJavaBinary(): File? {
        val javaHome = System.getProperty("java.home") ?: return null
        val binName = if (isWindows()) "java.exe" else "java"
        val bin = File(File(javaHome, "bin"), binName)
        return bin.takeIf { it.exists() }
    }

    sealed class LaunchResult {
        object Success : LaunchResult()
        data class Unsupported(val reason: String) : LaunchResult()
        data class Failure(val reason: String) : LaunchResult()
    }
}
