/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-desktop
 *
 * Original hard forked code:
 * https://github.com/ReVanced/revanced-cli/tree/731865e167ee449be15fff3dde7a476faea0c2de
 */

package app.morphe.desktop.command

import app.morphe.engine.options.toPatchOption
import app.morphe.engine.patches.compatibleVersionsForDisplay
import app.morphe.engine.patches.isCompatibleWith
import app.morphe.engine.patches.versionCodesFor
import app.morphe.engine.patches.PatchResolver
import app.morphe.engine.patches.PatchBundleLoader
import app.morphe.patcher.patch.Option as PatchOption
import app.morphe.patcher.patch.Patch
import java.io.File
import java.util.logging.Logger
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Spec
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Help.Visibility.ALWAYS

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

    @Option(
        names = ["-x", "--include-experimental"],
        description = ["Include experimental app versions in the output."],
        showDefaultValue = ALWAYS,
    )
    private var includeExperimental: Boolean = false

    @Spec
    private lateinit var spec: CommandSpec

    override fun run() {
        fun PatchOption<*>.buildString() =
            buildString {
                appendLine("Title: $name")
                description?.let { appendLine("Description: $it") }
                appendLine("Required: $required")
                default?.let {
                    appendLine("Key: $name")
                    append("Default: $it")
                } ?: append("Key: $name")

                values?.let { values ->
                    appendLine("\nPossible values:")
                    append(values.map { "${it.value} (${it.key})" }.joinToString("\n").prependIndent("\t"))
                }

                append("\nType: ${toPatchOption().type.displayName}")
            }

        fun getVersionCodesString(patch: Patch<*>, pkgName: String, versionName: String): String {
            return patch.versionCodesFor(pkgName, versionName)?.let { codes ->
                codes.entries.joinToString(", ") { "${it.key.name}=${it.value}" }
            } ?: ""
        }

        fun IndexedValue<Patch<*>>.buildString() =
            let { (index, patch) ->
                buildString {
                    if (withIndex) appendLine("Index: $index")

                    append("Name: ${patch.name}")

                    if (withDescriptions) append("\nDescription: ${patch.description}")

                    append("\nEnabled: ${patch.default}")

                    if (withOptions && patch.options.isNotEmpty()) {
                        appendLine("\nOptions:")
                        append(
                            patch.options.values.joinToString("\n\n") { option ->
                                option.buildString()
                            }.prependIndent("\t"),
                        )
                    }

                    if (withPackages) {
                        val packages = patch.compatibleVersionsForDisplay(includeExperimental)
                        if (packages.isNotEmpty()) {
                            appendLine("\nCompatible packages:")
                            append(
                                packages.joinToString("\n") { (name, versions) ->
                                    buildString {
                                        val displayName = name ?: "(universal)"
                                        if (withVersions && versions.isNotEmpty()) {
                                            appendLine("\tPackage name: $displayName")
                                            appendLine("\tCompatible versions:")
                                            append(versions.joinToString("\n").prependIndent("\t\t"))

                                            val codesList = versions.mapNotNull { v ->
                                                val codes = getVersionCodesString(patch, displayName, v)
                                                if (codes.isNotEmpty()) "$v: $codes" else null
                                            }

                                            if (codesList.isNotEmpty()) {
                                                append("\n\tVersion codes:\n")
                                                append(codesList.joinToString("\n").prependIndent("\t\t"))
                                            }
                                        } else {
                                            append("\tPackage name: $displayName")
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }

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

        val patches = PatchBundleLoader.loadFlat(patchesFiles).withIndex().toList()

        val filtered = packageName?.let { name ->
            patches.filter { (_, patch) ->
                patch.isCompatibleWith(
                    packageName = name,
                    includeExperimental = includeExperimental,
                    includeUniversalPatches = withUniversalPatches,
                )
            }
        } ?: patches

        // Extracted the final output that we get into this variable. Now we just call this based
        // on what the user wants. In the console or as an external text file.
        val finalOutput = filtered.joinToString("\n\n") {it.buildString()}

        if (filtered.isEmpty()) {
            logger.warning("No compatible patches found in: ${patchesFiles.joinToString(", ") { it.path }}")
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
