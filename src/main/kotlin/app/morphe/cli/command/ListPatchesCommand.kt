/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-cli
 *
 * Original hard forked code:
 * https://github.com/ReVanced/revanced-cli/tree/731865e167ee449be15fff3dde7a476faea0c2de
 */

package app.morphe.cli.command

import app.morphe.engine.MorpheData
import app.morphe.patcher.patch.Package
import app.morphe.patcher.patch.Patch
import app.morphe.patcher.patch.loadPatchesFromJar
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Spec
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Help.Visibility.ALWAYS
import java.io.File
import java.util.logging.Logger
import app.morphe.patcher.patch.Option as PatchOption

@Command(
    name = "list-patches",
    description = ["List patches from supplied MPP files."],
)
internal object ListPatchesCommand : Runnable {
    private val logger = Logger.getLogger(this::class.java.name)

    // Patches is now flag based rather than position based
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
        names = ["--out"],
        description = ["Path to the output text file. Writes patch list to this file instead of stdout."],
    )
    private var outputFile: File? = null

    @Option(
        names = ["-d", "--with-descriptions"],
        description = ["List their descriptions."],
        showDefaultValue = ALWAYS,
    )
    private var withDescriptions: Boolean = true

    @Option(
        names = ["-t", "--temporary-files-path"],
        description = ["Path to store temporary files."],
    )
    private var temporaryFilesPath: File? = null

    @Option(
        names = ["-p", "--with-packages"],
        description = ["List the packages the patches are compatible with."],
        showDefaultValue = ALWAYS,
    )
    private var withPackages: Boolean = false

    @Option(
        names = ["-v", "--with-versions"],
        description = ["List the versions of the apps the patches are compatible with."],
        showDefaultValue = ALWAYS,
    )
    private var withVersions: Boolean = false

    @Option(
        names = ["-o", "--with-options"],
        description = ["List the options of the patches."],
        showDefaultValue = ALWAYS,
    )
    private var withOptions: Boolean = false

    @Option(
        names = ["-u", "--with-universal-patches"],
        description = ["List patches which are compatible with any app."],
        showDefaultValue = ALWAYS,
    )
    private var withUniversalPatches: Boolean = true

    @Option(
        names = ["-i", "--index"],
        description = ["List the index of each patch in relation to the supplied MPP files."],
        showDefaultValue = ALWAYS,
    )
    private var withIndex: Boolean = true

    @Option(
        names = ["-f", "--filter-package-name"],
        description = ["Filter patches by package name."],
    )
    private var packageName: String? = null

    @Spec
    private lateinit var spec: CommandSpec

    override fun run() {
        fun Package.buildString(): String {
            val (name, versions) = this

            return buildString {
                if (withVersions && versions != null) {
                    appendLine("Package name: $name")
                    appendLine("Compatible versions:")
                    append(versions.joinToString("\n") { version -> version }.prependIndent("\t"))
                } else {
                    append("Package name: $name")
                }
            }
        }

        fun PatchOption<*>.buildString() =
            buildString {
                appendLine("Title: $title")
                description?.let { appendLine("Description: $it") }
                appendLine("Required: $required")
                default?.let {
                    appendLine("Key: $key")
                    append("Default: $it")
                } ?: append("Key: $key")

                values?.let { values ->
                    appendLine("\nPossible values:")
                    append(values.map { "${it.value} (${it.key})" }.joinToString("\n").prependIndent("\t"))
                }

                append("\nType: $type")
            }

        fun IndexedValue<Patch<*>>.buildString() =
            let { (index, patch) ->
                buildString {
                    if (withIndex) appendLine("Index: $index")

                    append("Name: ${patch.name}")

                    if (withDescriptions) append("\nDescription: ${patch.description}")

                    append("\nEnabled: ${patch.use}")

                    if (withOptions && patch.options.isNotEmpty()) {
                        appendLine("\nOptions:")
                        append(
                            patch.options.values.joinToString("\n\n") { option ->
                                option.buildString()
                            }.prependIndent("\t"),
                        )
                    }

                    if (withPackages && patch.compatiblePackages != null) {
                        appendLine("\nCompatible packages:")
                        append(
                            patch.compatiblePackages!!.joinToString("\n") {
                                it.buildString()
                            }.prependIndent("\t"),
                        )
                    }
                }
            }

        fun Patch<*>.filterCompatiblePackages(name: String) =
            compatiblePackages?.any { (compatiblePackageName, _) ->
                compatiblePackageName == name
            } ?: withUniversalPatches


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

        val patches = loadPatchesFromJar(patchesFiles).withIndex().toList()

        val filtered = packageName?.let {
            patches.filter { (_, patch) ->
                patch.filterCompatiblePackages(
                    it
                )
            }
        } ?: patches

        // Extracted the final output that we get into this variable. Now we just call this based
        // on what the user wants. In the console or as an external text file.
        val finalOutput = filtered.joinToString("\n\n") {it.buildString()}

        if (filtered.isEmpty()) {
            logger.warning("No compatible patches found in: $patchesFiles")
        } else {
            if (outputFile == null) {
                logger.info(finalOutput)
            } else {
                logger.info("Created new output file at ${outputFile!!.path}")
                outputFile!!.writeText(finalOutput)
            }
        }
    }
}
