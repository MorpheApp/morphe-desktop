package app.morphe.tasks;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

@CacheableTask
@SuppressWarnings("unused")
public abstract class WriteMorpheComponentsTask extends DefaultTask {

    @Input
    public abstract Property<String> getLibraryVersion();

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @TaskAction
    public void generate() throws IOException {
        File file = getOutputFile().get().getAsFile();
        Files.createDirectories(file.getParentFile().toPath());
        String content = "# Generated from resolved runtimeClasspath - do not edit\n"
                + "libraryVersion=" + getLibraryVersion().get() + "\n";
        Files.writeString(file.toPath(), content);
    }
}
