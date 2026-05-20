package app.morphe.cli.command

import app.morphe.cli.command.model.PatchBundle
import app.morphe.engine.MorpheData
import app.morphe.engine.patches.LoadedBundle
import app.morphe.engine.patches.PatchBundleLoader
import app.morphe.cli.command.model.findMatchingBundle
import app.morphe.cli.command.model.mergeWithBundle
import app.morphe.cli.command.model.withUpdatedBundle
import kotlinx.serialization.json.Json
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Help.Visibility.ALWAYS
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Spec
import java.io.File
import java.util.concurrent.Callable
import java.util.logging.Logger

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
        names = ["-t", "--temporary-files-path"],
        description = ["Path to store temporary files."],
    )
    private var temporaryFilesPath: File? = null

    @Option(
        names = ["-f", "--filter-package-name"],
        description = ["Filter patches by compatible package name."],
    )
    private var packageName: String? = null

    private val json = Json { prettyPrint = true }

    override fun call(): Int {
        val temporaryFilesPath = temporaryFilesPath ?: MorpheData.tmpDir

        try {
            // Since we could have many URLs, we resolve each of them separately
            patchesFiles = patchesFiles.map { file ->
                val resolved = PatchFileResolver.resolve(
                    setOf(file),
                    prerelease,
                    temporaryFilesPath,
                    CliHttpClient.instance
                )
                resolved.single()
            }.toSet()
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
                    Json.decodeFromString<List<PatchBundle>>(outputFile.readText())
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
            loadedBundles.forEach { lb ->
                val filtered = packageName?.let { pkg ->
                    lb.patches.filter { patch ->
                        patch.compatiblePackages?.any { (name, _) -> name == pkg } ?: true
                    }.toSet()
                } ?: lb.patches

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

            outputFile.absoluteFile.parentFile?.mkdirs()
            outputFile.writeText(json.encodeToString(updatedBundles))

            logger.info("Options file saved to ${outputFile.path}")

            EXIT_CODE_SUCCESS
        } catch (e: Exception) {
            logger.severe("Failed to export options: ${e.message}")
            EXIT_CODE_ERROR
        }
    }
}
