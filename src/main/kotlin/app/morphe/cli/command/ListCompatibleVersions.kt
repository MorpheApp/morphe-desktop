/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 */

package app.morphe.cli.command

import app.morphe.engine.CompatibleVersionsMap
import app.morphe.engine.VersionMap
import app.morphe.engine.mostCommonCompatibleVersions
import app.morphe.patcher.patch.loadPatchesFromJar
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Help.Visibility.ALWAYS
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Option
import picocli.CommandLine.Spec
import java.io.File
import java.util.logging.Logger

@Command(
    name = "list-versions",
    description = [
        "List the most common compatible versions of apps that are compatible " +
                "with the patches from MPP files.",
    ],
)
internal class ListCompatibleVersions : Runnable {
    private val logger = Logger.getLogger(this::class.java.name)

    @Option(
        names = ["--patches"],
        description = ["Path to a MPP file or a GitHub repo url such as https://github.com/MorpheApp/morphe-patches"],
        arity = "1..*",
        required = true
    )
    @Suppress("unused")
    private fun setPatchesFile(patchesFiles: Set<File>) {
        this.patchesFiles = checkFileExistsOrIsUrl(patchesFiles, spec)
    }
    private var patchesFiles = emptySet<File>()

    @Option(
        names = ["--prerelease"],
        description = ["Fetch the latest dev pre-release instead of the stable main release from the repo provided in --patches."],
        showDefaultValue = ALWAYS,
    )
    private var prerelease: Boolean = false

    @Option(
        names = ["-f", "--filter-package-names"],
        description = ["Filter patches by package name."],
    )
    private var packageNames: Set<String>? = null

    @Option(
        names = ["-u", "--count-unused-patches"],
        description = ["Count patches that are not used by default."],
        showDefaultValue = ALWAYS,
    )
    private var countUnusedPatches: Boolean = false

    @Option(
        names = ["-x", "--include-experimental"],
        description = ["Include experimental app versions in the output."],
        showDefaultValue = ALWAYS,
    )
    private var includeExperimental: Boolean = false

    @Spec
    private lateinit var spec: CommandSpec

    override fun run() {
        fun VersionMap.buildVersionsString(): String {
            if (isEmpty()) return "Any"

            fun buildPatchesCountString(count: Int) = if (count == 1) "1 patch" else "$count patches"

            return entries.joinToString("\n") { (version, count) ->
                "$version (${buildPatchesCountString(count)})"
            }
        }

        fun buildString(entry: Map.Entry<String, VersionMap>) =
            buildString {
                val (name, versions) = entry
                appendLine("Package name: $name")
                appendLine("Most common compatible versions:")
                appendLine(versions.buildVersionsString().prependIndent("\t"))
            }

        try {
            patchesFiles = PatchFileResolver.resolve(
                patchesFiles,
                prerelease,
                CliHttpClient.instance
            )
        } catch (e: IllegalArgumentException) {
            throw CommandLine.ParameterException(
                spec.commandLine(),
                e.message ?: "Failed to resolve patch URL"
            )
        }

        val patches = loadPatchesFromJar(patchesFiles)

        patches.mostCommonCompatibleVersions(
            packageNames,
            countUnusedPatches,
            includeExperimental,
        ).entries.joinToString("\n", transform = ::buildString).let(logger::info)
    }
}
