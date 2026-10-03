/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.desktop.command

import app.morphe.engine.isCompatibleWith
import app.morphe.engine.options.PatchBundle
import app.morphe.engine.options.findMatchingBundle
import app.morphe.engine.options.mergeWithBundle
import app.morphe.engine.options.readPatchBundles
import app.morphe.engine.options.withUpdatedBundle
import app.morphe.engine.options.writePatchBundles
import app.morphe.engine.patches.LoadedBundle
import app.morphe.engine.patches.PatchBundleLoader
import app.morphe.engine.patches.PatchResolver
import java.io.File
import java.util.concurrent.Callable
import java.util.logging.Logger
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Help.Visibility.ALWAYS
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Spec

@Command(
    name = "options-create",
    description = ["Create an options JSON file for the patches and options."],
)
internal object OptionsCommand : Callable<Int> {

    private const val EXIT_CODE_SUCCESS = 0
    private const val EXIT_CODE_ERROR = 1

    private val logger = Logger.getLogger(this::class.java.name)

    @Spec
    private lateinit var spec: CommandSpec

    @Option(
        names = ["-p", "--patches"],
        description = ["Path to a MPP file or a GitHub repo url such as https://github.com/MorpheApp/morphe-patches"],
        required = true,
    )
    @Suppress("unused")
    private fun setPatchesFile(patchesFiles: Set<File>) {
        this.patchesFiles = checkFileExistsOrIsUrl(patchesFiles, spec)
    }

    private var patchesFiles = emptySet<File>()

    @Option(
        names = ["-o", "--out"],
        description = ["Path to the output JSON file."],
        required = true,
    )
    private lateinit var outputFile: File

    @Option(
        names = ["--prerelease"],
        description = ["Fetch the latest dev pre-release instead of the stable main release from the repo provided in --patches."],
        showDefaultValue = ALWAYS,
    )
    private var prerelease: Boolean = false

    @Option(
        names = ["-f", "--filter-package-name"],
        description = ["Filter patches by compatible package name."],
    )
    private var packageName: String? = null

    override fun call(): Int {
        try {
            patchesFiles = PatchResolver.resolveCliFiles(
                patchesFiles,
                prerelease,
            )
        } catch (e: IllegalArgumentException) {
            throw CommandLine.ParameterException(
                spec.commandLine(),
                e.message ?: "Failed to resolve patch URL"
            )
        }

        return try {
            logger.info("Loading patches...")

            // Load each bundle separately so we produce one JSON entry per .mpp
            // matches the shape PatchCommand expects when reading --options-file.
            val loadedBundles: List<LoadedBundle> = PatchBundleLoader.loadEach(patchesFiles)

            // Read existing bundles list if the file already exists.
            val existingBundles: List<PatchBundle> = if (outputFile.exists())
            {
                try {
                    readPatchBundles(outputFile)
                } catch (e: Exception) {
                    logger.warning(
                        "Could not parse existing file, creating fresh: ${e.message}"
                    )
                    emptyList()
                }
            } else emptyList()

            // For each bundle: apply optional package filter, find its matching JSON
            // entry (by source filename), merge, splice updated entry back into the running list.
            var updatedBundles = existingBundles
            val pkg = packageName
            loadedBundles.forEach { lb ->
                val filtered = lb.patches.filter { patch ->
                    pkg == null || patch.isCompatibleWith(
                        packageName = pkg,
                        includeExperimental = true,
                        includeUniversalPatches = true,
                    )
                }.toSet()

                val existingBundle = updatedBundles.findMatchingBundle(setOf(lb.sourceFile))
                val updatedBundle = filtered.mergeWithBundle(
                    existing = existingBundle,
                    sourceFiles = setOf(lb.sourceFile),
                )
                updatedBundles = updatedBundles.withUpdatedBundle(updatedBundle)

                // Per-bundle log line so users can see what changed for each .mpp
                if (existingBundle != null) {
                    val existingNames = existingBundle.patches.keys.map { it.lowercase() }.toSet()
                    val newNames = updatedBundle.patches.keys.map { it.lowercase() }.toSet()
                    val added = newNames - existingNames
                    val removed = existingNames - newNames
                    val kept = newNames.intersect(existingNames)

                    logger.info(
                        "Updated bundle for ${lb.sourceFile.name}: ${kept.size} preserved, ${added.size} added, ${removed.size} removed"
                    )
                } else {
                    logger.info(
                        "Created new bundle for ${lb.sourceFile.name} with ${updatedBundle.patches.size} patches"
                    )
                }
            }

            writePatchBundles(outputFile, updatedBundles)

            logger.info("Options file saved to ${outputFile.path}")

            EXIT_CODE_SUCCESS
        } catch (e: Exception) {
            logger.severe("Failed to export options: ${e.message}")
            EXIT_CODE_ERROR
        }
    }
}
