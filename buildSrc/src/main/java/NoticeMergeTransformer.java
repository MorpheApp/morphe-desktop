import com.github.jengelman.gradle.plugins.shadow.transformers.CacheableTransformer;
import com.github.jengelman.gradle.plugins.shadow.transformers.ResourceTransformer;
import com.github.jengelman.gradle.plugins.shadow.transformers.TransformerContext;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import javax.inject.Inject;
import org.apache.tools.zip.ZipEntry;
import org.apache.tools.zip.ZipOutputStream;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.jetbrains.annotations.NotNull;

/**
 * Merges dependency NOTICE files verbatim into a single META-INF/NOTICE entry
 * without adding Apache Software Foundation boilerplate.
 */
@CacheableTransformer
@SuppressWarnings("unused")
public abstract class NoticeMergeTransformer implements ResourceTransformer {

    private static final String DEFAULT_SEPARATOR =
            "\n\n----------------------------------------------------------------\n\n";
    private static final List<String> DEFAULT_PATHS = Arrays.asList(
            "META-INF/NOTICE",
            "META-INF/NOTICE.txt",
            "META-INF/NOTICE.md"
    );

    private final ObjectFactory objectFactory;
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private int sectionCount = 0;

    @Inject
    public NoticeMergeTransformer(ObjectFactory objectFactory) {
        this.objectFactory = objectFactory;
        getOutputPath().convention("META-INF/NOTICE");
        getMatchedPaths().convention(DEFAULT_PATHS);
        getSeparator().convention(DEFAULT_SEPARATOR);
    }

    @Input
    public abstract Property<String> getOutputPath();

    @Input
    public abstract ListProperty<String> getMatchedPaths();

    @Input
    public abstract Property<String> getSeparator();

    @Internal
    @NotNull
    @Override
    public ObjectFactory getObjectFactory() {
        return objectFactory;
    }

    @Override
    public boolean canTransformResource(@NotNull FileTreeElement element) {
        String path = element.getRelativePath().getPathString();
        for (String matched : getMatchedPaths().get()) {
            if (matched.equalsIgnoreCase(path)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void transform(@NotNull TransformerContext context) throws IOException {
        if (sectionCount > 0) {
            buffer.write(getSeparator().get().getBytes(StandardCharsets.UTF_8));
        }
        context.getInputStream().transferTo(buffer);
        sectionCount++;
    }

    @Override
    public boolean hasTransformedResource() {
        return sectionCount > 0;
    }

    @Override
    public void modifyOutputStream(@NotNull ZipOutputStream os, boolean preserveFileTimestamps) throws IOException {
        ZipEntry entry = new ZipEntry(getOutputPath().get());
        if (!preserveFileTimestamps) {
            entry.setTime(0);
        }
        os.putNextEntry(entry);
        os.write(buffer.toByteArray());
        os.closeEntry();
        buffer.reset();
        sectionCount = 0;
    }
}
