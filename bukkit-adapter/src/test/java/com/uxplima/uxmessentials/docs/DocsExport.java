package com.uxplima.uxmessentials.docs;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes the documentation model to disk for {@code tools/docs/generate.py}. Run by the {@code docsExport}
 * Gradle task; it needs the module registry and the shipped resources, which is why it lives beside the tests
 * rather than in the jar.
 */
public final class DocsExport {

    private DocsExport() {}

    public static void main(String[] args) {
        Path out = Path.of(args.length > 0 ? args[0] : "build/docs/docs-data.json");
        try {
            Path parent = out.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(out, DocsJson.render(DocsModelBuilder.build()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write " + out, e);
        }
        System.out.println("wrote " + out.toAbsolutePath());
    }
}
