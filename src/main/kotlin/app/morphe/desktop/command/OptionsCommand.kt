/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 */

package app.morphe.desktop.command

import app.morphe.engine.options.updateOptionsFileFromBundles
import app.morphe.engine.patches.PatchBundleLoader
import app.morphe.engine.patches.PatchResolver
import app.morphe.engine.util.Logger
import java.io.File
import java.util.concurrent.Callable
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

    @Spec
    private lateinit var spec: CommandSpec

    @Option(
        names = ["-p", "--patches"],
        description = ["Path to a .mpp file, directory, GitHub/GitLab/Codeberg repo URL, or .json bundle URL such as https://github.com/MorpheApp/morphe-patches"],
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
            Logger.info("Loading patches...")

            // Load each bundle separately so we produce one JSON entry per .mpp
            // matches the shape PatchCommand expects when reading --options-file.
            updateOptionsFileFromBundles(
                file = outputFile,
                bundles = PatchBundleLoader.loadEach(patchesFiles),
                packageName = packageName,
            )

            Logger.info("Options file saved to ${outputFile.path}")

            EXIT_CODE_SUCCESS
        } catch (e: Exception) {
            Logger.error("Failed to export options: ${e.message}")
            EXIT_CODE_ERROR
        }
    }
}
