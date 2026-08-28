package com.uxplima.uxmessentials.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

/**
 * The ubiquitous-language guard (CLAUDE.md §2, docs/08-glossary.md, docs/05-testing.md §16).
 *
 * <p><strong>The bug this freezes out.</strong> DDD only pays for itself while the team, the code and the player
 * use the same word for the same thing, which means every bounded context has to say somewhere what its terms
 * mean. The canon used to ask for a {@code GLOSSARY.md} per context and claimed a drift guard held the set
 * complete. Neither existed: not one {@code GLOSSARY.md} was ever written, the guard was never implemented, and
 * the docs accumulated forty-odd links into files that were not there. What the codebase grew instead was the
 * better version of the same idea, {@code package-info.java} next to the code it describes, where the compiler
 * sees it, review sees it, and it cannot drift away from the package it documents without somebody noticing.
 *
 * <p><strong>The invariant.</strong> Every layer package of every bounded context carries a
 * {@code package-info.java}. That file is the context's glossary for that layer: what the package owns, which
 * invariants it enforces, and what may not appear in it. The file is also where the JSpecify {@code @NullMarked}
 * annotation lives, so a package without one is invisible to NullAway as well: a missing glossary is a missing
 * null-safety fence too, which is why this is enforced rather than merely encouraged.
 *
 * <p><strong>Scope.</strong> The {@code core} module only, which is where the domain and application layers of
 * every context live. Adapter packages are covered by their own module's conventions.
 */
class UbiquitousLanguageDriftTest {

    private static final String CORE = "core/src/main/java/com/uxplima/uxmessentials";

    /** The plugin-loader scaffolding is not a bounded context and owns no domain language. */
    private static final String NOT_A_CONTEXT = "loader";

    @Test
    void everyPackageInCoreDocumentsItsOwnLanguage() {
        List<String> undocumented = new ArrayList<>();
        for (Path pkg : packagesWithSources()) {
            if (!Files.exists(pkg.resolve("package-info.java"))) {
                undocumented.add(repoRoot().relativize(pkg).toString());
            }
        }
        assertThat(new TreeSet<>(undocumented))
                .as(
                        "every package under %s states its own ubiquitous language in a package-info.java"
                                + " (docs/08-glossary.md). It is also where @NullMarked lives, so a package without"
                                + " one is skipped by NullAway as well.",
                        CORE)
                .isEmpty();
    }

    @Test
    void everyBoundedContextDocumentsItsApplicationLayer() {
        List<String> missing = new ArrayList<>();
        for (Path context : contexts()) {
            Path application = context.resolve("application");
            if (Files.isDirectory(application) && !Files.exists(application.resolve("package-info.java"))) {
                missing.add(context.getFileName().toString());
            }
        }
        assertThat(missing)
                .as("every bounded context describes what its use cases orchestrate, in application/package-info.java")
                .isEmpty();
    }

    @Test
    void everyBoundedContextWithADomainDocumentsIt() {
        List<String> missing = new ArrayList<>();
        for (Path context : contexts()) {
            Path domain = context.resolve("domain");
            if (Files.isDirectory(domain) && !Files.exists(domain.resolve("package-info.java"))) {
                missing.add(context.getFileName().toString());
            }
        }
        assertThat(missing)
                .as("a context's domain/package-info.java is its glossary: the aggregates it owns, the"
                        + " invariants it alone enforces, and the SDKs fenced out of it")
                .isEmpty();
    }

    /** The bounded contexts: every top-level package under {@code core}, minus the loader scaffolding. */
    private static List<Path> contexts() {
        Path root = repoRoot().resolve(CORE);
        assertThat(root).as("expected the core source tree at %s", CORE).isDirectory();
        List<Path> contexts = new ArrayList<>();
        try (var entries = Files.list(root)) {
            entries.filter(Files::isDirectory)
                    .filter(dir -> !NOT_A_CONTEXT.equals(dir.getFileName().toString()))
                    .sorted()
                    .forEach(contexts::add);
        } catch (IOException e) {
            throw new UncheckedIOException("could not list " + root, e);
        }
        assertThat(contexts).as("expected the bounded contexts under %s", CORE).isNotEmpty();
        return contexts;
    }

    /** Every package under {@code core} that actually holds a source file, so an empty grouping folder is exempt. */
    private static List<Path> packagesWithSources() {
        Path root = repoRoot().resolve(CORE);
        List<Path> packages = new ArrayList<>();
        try (var tree = Files.walk(root)) {
            tree.filter(Files::isDirectory)
                    .filter(dir -> !dir.startsWith(root.resolve(NOT_A_CONTEXT)))
                    .filter(UbiquitousLanguageDriftTest::holdsAJavaSource)
                    .sorted()
                    .forEach(packages::add);
        } catch (IOException e) {
            throw new UncheckedIOException("could not walk " + root, e);
        }
        return packages;
    }

    private static boolean holdsAJavaSource(Path dir) {
        try (var entries = Files.list(dir)) {
            return entries.anyMatch(file -> {
                String name = file.getFileName().toString();
                return name.endsWith(".java") && !name.equals("package-info.java");
            });
        } catch (IOException e) {
            throw new UncheckedIOException("could not list " + dir, e);
        }
    }

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.exists(dir.resolve("settings.gradle.kts"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException(
                "could not locate the repository root from " + Path.of("").toAbsolutePath());
    }
}
