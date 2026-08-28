package com.uxplima.uxmessentials.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * The guard-inventory guard: it holds the canon's claims about enforcement to the enforcement that exists
 * (CLAUDE.md §3, docs/05-testing.md §16).
 *
 * <p><strong>The bug this freezes out.</strong> CLAUDE.md §3 lists every forbidden practice beside the guard that
 * fails CI when it is broken, and docs/05-testing.md §16 inventories those guards. Both were substantially
 * fiction. Of the guards the canon named, most did not exist: no {@code PrintStackTraceDriftTest}, no
 * {@code LegacyChatApiDriftTest}, no {@code PermissionsDocsDriftTest}, no {@code SuppressWarningsCommentDriftTest},
 * and several ArchUnit rules were cited under names no field ever carried. Meanwhile the suite had grown dozens of
 * real guards the inventory never mentioned. A named guard that does not exist is worse than an admitted gap: it
 * reads as "this rule is mechanically enforced, stop worrying about it", and the reviewer stops looking.
 *
 * <p><strong>The invariant.</strong> Both directions, for both kinds of guard. Every {@code *DriftTest} class in
 * the repository is named in the §16 inventory, so a new guard cannot be written and forgotten. Every
 * {@code *DriftTest} the canon names exists as a class, so the canon cannot promise enforcement it has not got.
 * The same holds for the ArchUnit rules in {@code ArchitectureTest}: the rule names in §16 and the
 * {@code @ArchTest} fields are the same set.
 *
 * <p><strong>Scope.</strong> The canon is CLAUDE.md plus the numbered docs and the ADRs. {@code docs/superpowers/}
 * is deliberately excluded: specs, plans and research are frozen records of what was intended at the time, and
 * holding a June plan to an August class list would only pressure somebody to rewrite history. {@code docs/} is
 * kept out of the published repository, so a checkout without it skips rather than fails.
 */
class DriftGuardInventoryDriftTest {

    private static final String INVENTORY = "docs/05-testing.md";

    private static final String ARCHITECTURE_TEST =
            "bukkit-adapter/src/test/java/com/uxplima/uxmessentials/architecture/ArchitectureTest.java";

    /** The canon: the files a contributor is told to trust. Frozen design records are not canon. */
    private static final List<String> CANON_ROOTS = List.of("CLAUDE.md", "docs");

    private static final Pattern GUARD_NAME = Pattern.compile("\\b([A-Z][A-Za-z0-9]*DriftTest)\\b");

    private static final Pattern ARCH_RULE_FIELD =
            Pattern.compile("static\\s+final\\s+ArchRule\\s+([A-Za-z][A-Za-z0-9]*)");

    /** An ArchUnit rule as the inventory lists it: the first column of the rule table. */
    private static final Pattern ARCH_RULE_ROW = Pattern.compile("(?m)^\\|\\s*`([a-z][A-Za-z0-9]*)`\\s*\\|");

    @Test
    void everyGuardThatExistsIsInTheInventory() {
        Set<String> inventoried = namesIn(read(repoRoot().resolve(INVENTORY)), GUARD_NAME);
        Set<String> shipped = shippedGuards();
        shipped.removeAll(inventoried);
        assertThat(shipped)
                .as(
                        "every drift guard in the repository belongs in the %s §16 inventory, so the next author"
                                + " can find the boundary already covered instead of writing a sibling for it",
                        INVENTORY)
                .isEmpty();
    }

    @Test
    void everyGuardTheCanonNamesExists() {
        Set<String> shipped = shippedGuards();
        List<String> phantoms = new ArrayList<>();
        for (Path doc : canonDocs()) {
            for (String named : namesIn(read(doc), GUARD_NAME)) {
                if (!shipped.contains(named)) {
                    phantoms.add(repoRoot().relativize(doc) + ": " + named);
                }
            }
        }
        assertThat(new TreeSet<>(phantoms))
                .as("the canon may only name guards that exist. A rule documented as mechanically enforced by a"
                        + " guard nobody wrote reads as settled and stops being reviewed; write the guard, or"
                        + " say plainly that the rule is review-time discipline.")
                .isEmpty();
    }

    @Test
    void theArchUnitRulesAndTheirInventoryAreTheSameSet() {
        Set<String> fields = namesIn(read(repoRoot().resolve(ARCHITECTURE_TEST)), ARCH_RULE_FIELD);
        assertThat(fields)
                .as("expected the ArchUnit rules in %s", ARCHITECTURE_TEST)
                .isNotEmpty();

        Set<String> listed = namesIn(archUnitTable(), ARCH_RULE_ROW);
        assertThat(listed)
                .as(
                        "the ArchUnit table in %s §16 and the @ArchTest fields in %s are one list kept in two"
                                + " places: a rule renamed on one side is renamed on the other in the same commit",
                        INVENTORY, ARCHITECTURE_TEST)
                .containsExactlyInAnyOrderElementsOf(fields);
    }

    /** The rule table in §16: everything from its introducing line to the end of the section. */
    private static String archUnitTable() {
        String inventory = read(repoRoot().resolve(INVENTORY));
        int start = inventory.indexOf("Plus the ArchUnit");
        assertThat(start)
                .as("expected the ArchUnit rule table to follow the guard inventory in %s §16", INVENTORY)
                .isNotNegative();
        int end = inventory.indexOf("\n## ", start);
        return end < 0 ? inventory.substring(start) : inventory.substring(start, end);
    }

    /** Every drift guard class in the repository, whichever module it lives in. */
    private static Set<String> shippedGuards() {
        Set<String> names = new TreeSet<>();
        try (Stream<Path> tree = Files.walk(repoRoot())) {
            tree.filter(path -> path.getFileName().toString().endsWith("DriftTest.java"))
                    .filter(path -> !path.toString().contains("/build/"))
                    .forEach(path -> {
                        String file = path.getFileName().toString();
                        names.add(file.substring(0, file.length() - ".java".length()));
                    });
        } catch (IOException e) {
            throw new UncheckedIOException("could not walk the repository", e);
        }
        assertThat(names).as("expected the drift guards to be found on disk").isNotEmpty();
        return names;
    }

    /** CLAUDE.md, the numbered docs and the ADRs. Frozen specs, plans and research are not canon. */
    private static List<Path> canonDocs() {
        Path root = repoRoot();
        List<Path> docs = new ArrayList<>();
        for (String entry : CANON_ROOTS) {
            Path path = root.resolve(entry);
            if (Files.isRegularFile(path)) {
                docs.add(path);
                continue;
            }
            if (!Files.isDirectory(path)) {
                continue;
            }
            try (Stream<Path> tree = Files.walk(path)) {
                tree.filter(file -> file.toString().endsWith(".md"))
                        .filter(file -> !file.toString().contains("/superpowers/"))
                        .sorted()
                        .forEach(docs::add);
            } catch (IOException e) {
                throw new UncheckedIOException("could not walk " + path, e);
            }
        }
        assumeTrue(!docs.isEmpty(), "the canon is not present in this checkout (docs/ stays out of the public repo)");
        return docs;
    }

    private static Set<String> namesIn(String text, Pattern pattern) {
        Set<String> names = new TreeSet<>();
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    private static String read(Path path) {
        assumeTrue(Files.exists(path), path + " is not present in this checkout");
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + path, e);
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
