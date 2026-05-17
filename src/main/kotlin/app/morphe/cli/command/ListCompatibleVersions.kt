package app.morphe.cli.command

import app.morphe.engine.MorpheData
import app.morphe.patcher.patch.PackageName
import app.morphe.patcher.patch.VersionMap
import app.morphe.patcher.patch.loadPatchesFromJar
import app.morphe.patcher.patch.mostCommonCompatibleVersions
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
        names = ["-t", "--temporary-files-path"],
        description = ["Path to store temporary files."],
    )
    private var temporaryFilesPath: File? = null

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

        fun buildString(entry: Map.Entry<PackageName, VersionMap>) =
            buildString {
                val (name, versions) = entry
                appendLine("Package name: $name")
                appendLine("Most common compatible versions:")
                appendLine(versions.buildVersionsString().prependIndent("\t"))
            }

        val temporaryFilesPath = temporaryFilesPath ?: MorpheData.tmpDir

        try {
            patchesFiles = PatchFileResolver.resolve(
                patchesFiles,
                prerelease,
                temporaryFilesPath,
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
        ).entries.joinToString("\n", transform = ::buildString).let(logger::info)
    }
}
