package com.uxplima.uxmessentials.shared.application.permission;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Keeps every node in the catalogue classes. A node written inline somewhere else is a second copy that a rename of
 * the catalogue entry silently misses, so the main sources of every module are read and any string literal in the
 * plugin's node space outside the catalogue fails the build.
 */
class PermissionLiteralsTest {

    private static final String SPACE = "uxmessentials.";

    private static final Path CATALOG_PACKAGE =
            Path.of("core/src/main/java/com/uxplima/uxmessentials/shared/application/permission");

    /**
     * The literals that may stay, each keyed by the file that holds it. The REST add-on compiles against the
     * published API alone and cannot see the catalogue, and the database file name only shares the prefix.
     */
    private static final Map<String, Set<String>> ALLOWED = Map.of(
            "rest-adapter/src/main/java/com/uxplima/uxmessentials/rest/TokenCommand.java",
            Set.of("uxmessentials.rest.admin"),
            "persistence-adapter/src/main/java/com/uxplima/uxmessentials/persistence/runtime/DatabaseSettings.java",
            Set.of("uxmessentials.db"));

    private static final List<Class<?>> CATALOGS = List.of(
            SharedPermissions.class,
            TeleportPermissions.class,
            DestinationPermissions.class,
            EconomyPermissions.class,
            EnforcementPermissions.class,
            SocialPermissions.class,
            PlayerstatePermissions.class,
            ItemworldPermissions.class,
            ContentPermissions.class);

    @Test
    void noMainSourceWritesANodeOutsideTheCatalogue() {
        Path root = repositoryRoot();
        List<String> found = new ArrayList<>();
        for (Path file : mainSources(root)) {
            String relative = root.relativize(file).toString().replace('\\', '/');
            if (isCatalog(root.relativize(file))) {
                continue;
            }
            Set<String> allowed = ALLOWED.getOrDefault(relative, Set.of());
            for (String literal : stringLiterals(read(file))) {
                if (literal.startsWith(SPACE) && !allowed.contains(literal)) {
                    found.add(relative + ": \"" + literal + "\"");
                }
            }
        }

        assertThat(found)
                .as("node literals belong in the *Permissions catalogue classes; use their constants instead")
                .isEmpty();
    }

    @Test
    void everyCatalogueConstantNamesANodeTheCatalogueDeclares() throws IllegalAccessException {
        List<String> undeclared = new ArrayList<>();
        for (Class<?> catalog : CATALOGS) {
            for (Field field : catalog.getDeclaredFields()) {
                int modifiers = field.getModifiers();
                if (!Modifier.isPublic(modifiers) || field.getType() != String.class) {
                    continue;
                }
                String node = (String) field.get(null);
                if (!node.equals(SharedPermissions.NAMESPACE)
                        && PermissionCatalog.find(node).isEmpty()) {
                    undeclared.add(catalog.getSimpleName() + "." + field.getName() + " = " + node);
                }
            }
        }

        assertThat(undeclared).isEmpty();
    }

    @Test
    void theScannerSkipsCommentsAndCharactersButSeesEveryCodeLiteral() {
        String source = """
                // "uxmessentials.in.a.line.comment"
                /* "uxmessentials.in.a.block.comment" */
                char quote = '"';
                String a = "uxmessentials.real" + "with \\" escaped";
                String b = \"""
                    "uxmessentials.in.a.text.block"
                    \""";
                String c = \"""
                    ends in a backslash \\\\\""";
                String d = "uxmessentials.after.a.backslash";
                """;

        assertThat(stringLiterals(source))
                .containsExactly("uxmessentials.real", "with \\\" escaped", "uxmessentials.after.a.backslash");
    }

    private static boolean isCatalog(Path relative) {
        return CATALOG_PACKAGE.equals(relative.getParent())
                && relative.getFileName().toString().endsWith("Permissions.java");
    }

    /** The repository root, handed over by the build so the scan reaches every module rather than only this one. */
    private static Path repositoryRoot() {
        String root = System.getProperty("uxm.repositoryRoot");
        assertThat(root)
                .as("the build passes uxm.repositoryRoot to the test JVM")
                .isNotNull();
        return Path.of(root).toAbsolutePath().normalize();
    }

    private static List<Path> mainSources(Path root) {
        try (Stream<Path> modules = Files.list(root)) {
            List<Path> files = new ArrayList<>();
            for (Path module : modules.toList()) {
                Path sources = module.resolve("src/main/java");
                if (!Files.isDirectory(sources)) {
                    continue;
                }
                try (Stream<Path> walk = Files.walk(sources)) {
                    walk.filter(path -> path.toString().endsWith(".java")).forEach(files::add);
                }
            }
            assertThat(files).as("main sources under " + root).isNotEmpty();
            return files;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * The contents of every ordinary string literal in {@code source}, left escaped. Comments, character literals
     * and text blocks are stepped over, because a node quoted in a comment is prose and a text block is a resource,
     * not a check.
     */
    static List<String> stringLiterals(String source) {
        List<String> literals = new ArrayList<>();
        int i = 0;
        int n = source.length();
        while (i < n) {
            if (source.startsWith("//", i)) {
                int end = source.indexOf('\n', i);
                i = end < 0 ? n : end;
            } else if (source.startsWith("/*", i)) {
                int end = source.indexOf("*/", i + 2);
                i = end < 0 ? n : end + 2;
            } else if (source.startsWith("\"\"\"", i)) {
                // Stepped over one escape at a time, so an escaped backslash right before the closing quotes ends
                // the block rather than being read as an escaped quote.
                int end = i + 3;
                while (end < n && !source.startsWith("\"\"\"", end)) {
                    end += source.charAt(end) == '\\' ? 2 : 1;
                }
                i = Math.min(end + 3, n);
            } else if (source.charAt(i) == '"' || source.charAt(i) == '\'') {
                char quote = source.charAt(i);
                int end = i + 1;
                while (end < n && source.charAt(end) != quote) {
                    end += source.charAt(end) == '\\' ? 2 : 1;
                }
                if (quote == '"') {
                    literals.add(source.substring(i + 1, Math.min(end, n)));
                }
                i = end + 1;
            } else {
                i++;
            }
        }
        return literals;
    }
}
