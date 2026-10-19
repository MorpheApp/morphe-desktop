package app.morphe.tasks;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

@CacheableTask
@SuppressWarnings("unused")
public abstract class GenerateBootstrapConstantsTask extends DefaultTask {

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getArtifactFiles();

    @Input
    public abstract Property<String> getSkikoVersion();

    @Input
    public abstract Property<String> getJnaVersion();

    @Input
    public abstract Property<String> getOshiVersion();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDir();

    @TaskAction
    public void generate() throws IOException, NoSuchAlgorithmException {
        String skikoVer = getSkikoVersion().get();
        String jnaVer = getJnaVersion().get();
        String oshiVer = getOshiVersion().get();
        Set<File> files = getArtifactFiles().getFiles();

        String jnaHash = sha256(files, "jna", jnaVer);
        String jnaPlatformHash = sha256(files, "jna-platform", jnaVer);
        String skikoAwtHash = sha256(files, "skiko-awt", skikoVer);
        String oshiCoreHash = sha256(files, "oshi-core", oshiVer);
        String oshiCommonHash = sha256(files, "oshi-common", oshiVer);

        String macosX64 = sha256(files, "skiko-awt-runtime-macos-x64", skikoVer);
        String macosArm64 = sha256(files, "skiko-awt-runtime-macos-arm64", skikoVer);
        String linuxX64 = sha256(files, "skiko-awt-runtime-linux-x64", skikoVer);
        String linuxArm64 = sha256(files, "skiko-awt-runtime-linux-arm64", skikoVer);
        String windowsX64 = sha256(files, "skiko-awt-runtime-windows-x64", skikoVer);

        File outFile = getOutputDir().get().file("BootstrapConstants.kt").getAsFile();
        Files.createDirectories(outFile.getParentFile().toPath());

        String content = """
            package app.morphe.engine

            internal object BootstrapConstants {
                const val SKIKO_VERSION = "%s"
                const val JNA_VERSION = "%s"
                const val OSHI_VERSION = "%s"

                const val JNA_HASH = "%s"
                const val JNA_PLATFORM_HASH = "%s"
                const val SKIKO_AWT_HASH = "%s"
                const val OSHI_CORE_HASH = "%s"
                const val OSHI_COMMON_HASH = "%s"

                val SKIKO_HASHES = mapOf(
                    "macos-x64" to "%s",
                    "macos-arm64" to "%s",
                    "linux-x64" to "%s",
                    "linux-arm64" to "%s",
                    "windows-x64" to "%s"
                )
            }
            """.formatted(
                skikoVer, jnaVer, oshiVer,
                jnaHash, jnaPlatformHash, skikoAwtHash, oshiCoreHash, oshiCommonHash,
                macosX64, macosArm64, linuxX64, linuxArm64, windowsX64
            );

        Files.writeString(outFile.toPath(), content);
    }

    private static String sha256(Set<File> files, String name, String version) throws IOException, NoSuchAlgorithmException {
        String jarName = name + "-" + version + ".jar";
        File file = files.stream()
                .filter(f -> f.getName().equals(jarName))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Artifact matching " + jarName + " not found in " + files));
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(Files.readAllBytes(file.toPath()));
        return HexFormat.of().formatHex(digest);
    }
}
