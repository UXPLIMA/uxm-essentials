package com.uxplima.uxmessentials.architecture;

import static org.assertj.core.api.Assertions.assertThat;

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
 * The guard on the guards: every guard this repository ships can actually fail.
 *
 * <p><strong>What this replaces, and why.</strong> This test used to hold CLAUDE.md and the numbered docs to
 * the guards that exist, in both directions. Neither CLAUDE.md nor {@code docs/} has ever been in this
 * repository, so all three tests called {@code Assumptions.assumeTrue} and aborted. JUnit records an abort as
 * a skip, so the suite reported green while the guard on the guards was itself doing nothing, which is the
 * exact shape of failure it was written to catch. CONTRACT.md section 15 says there is no legitimate skip
 * here, so it is replaced by the same question asked of files that are here.
 *
 * <p><strong>The invariant now.</strong> A guard that cannot fail is not a guard, and there are three ways to
 * write one by accident. A {@code *DriftTest} class with no test method in it is a file that looks like
 * enforcement and runs nothing. An {@code ArchRule} field without {@code @ArchTest} is never handed to
 * ArchUnit, so it is compiled, reviewed, and never evaluated. A rule with {@code allowEmptyShould(true)}
 * passes when nothing matches it, which is right for a rule guarding against a thing that does not exist yet
 * and wrong for one whose subject moved out from under it: those are named here, so a new one is a deliberate
 * edit rather than a quiet opt-out.
 *
 * <p><strong>The trade.</strong> The canon can no longer be held to the guards, because the canon is not
 * here. The list of rules allowed to pass vacuously now lives in this file rather than in a document a person
 * reads. That is worse for a reader and better for the build, and the build is the half that was broken.
 */
class GuardIntegrityDriftTest {

    private static final String ARCHITECTURE_TEST =
            "bukkit-adapter/src/test/java/com/uxplima/uxmessentials/architecture/ArchitectureTest.java";

    /**
     * The ArchUnit rules that may pass when nothing matches them. Each guards against a practice this codebase
     * has never had: no domain class imports Bukkit, no published API class reaches an internal type, and so
     * on. An empty result is the answer they are meant to give. Every other rule has a live subject, so an
     * empty result there means the subject moved and the rule is now aimed at nothing.
     */
    private static final Set<String> MAY_PASS_ON_AN_EMPTY_SET = Set.of(
            "apiModulesDoNotDependOnInternals",
            "applicationHasNoBukkit",
            "bootstrapIsTheOnlyPlaceWithJavaPlugin",
            "domainAndApplicationHaveNoInfrastructure",
            "domainFactsAndProposalsAreRecords",
            "domainHasNoBukkit",
            "economyDomainHasNoProviderSdk",
            "menuPureCoreDoesNotUseHooks",
            "noClassDependsOnBukkitScheduler");

    /** An {@code ArchRule} field declaration, capturing the field name. */
    private static final Pattern ARCH_RULE_FIELD =
            Pattern.compile("static\\s+final\\s+ArchRule\\s+([A-Za-z][A-Za-z0-9]*)");

    /** Anything JUnit or ArchUnit will actually run. */
    private static final Pattern RUNNABLE_TEST =
            Pattern.compile("@(Test|ParameterizedTest|RepeatedTest|TestFactory|TestTemplate|ArchTest)\\b");

    @Test
    void everyDriftGuardDeclaresATest() {
        List<String> empty = new ArrayList<>();
        for (Path guard : driftGuards()) {
            if (!RUNNABLE_TEST.matcher(read(guard)).find()) {
                empty.add(repoRoot().relativize(guard).toString());
            }
        }
        assertThat(empty)
                .as("a class named *DriftTest reads as enforcement. One with no test method in it is a file"
                        + " somebody stopped writing, and it is worse than no file at all, because the next"
                        + " reviewer sees the name and stops looking.")
                .isEmpty();
    }

    @Test
    void everyArchUnitRuleIsAnnotatedArchTest() {
        String source = read(repoRoot().resolve(ARCHITECTURE_TEST));
        Set<String> declared = namesIn(source, ARCH_RULE_FIELD);
        assertThat(declared)
                .as("expected the ArchUnit rules in %s", ARCHITECTURE_TEST)
                .isNotEmpty();

        long annotated = source.split("@ArchTest", -1).length - 1L;
        assertThat(annotated)
                .as(
                        "ArchUnit evaluates a rule only when its field carries @ArchTest. A field without one"
                                + " compiles, reads as a fence in review, and is never run. %s declares %d rules.",
                        ARCHITECTURE_TEST, declared.size())
                .isEqualTo(declared.size());
    }

    @Test
    void onlyTheNamedArchUnitRulesMayPassOnAnEmptySet() {
        Set<String> vacuous = new TreeSet<>();
        String source = read(repoRoot().resolve(ARCHITECTURE_TEST));
        Matcher matcher = ARCH_RULE_FIELD.matcher(source);
        List<Integer> starts = new ArrayList<>();
        List<String> names = new ArrayList<>();
        while (matcher.find()) {
            starts.add(matcher.start());
            names.add(matcher.group(1));
        }
        for (int i = 0; i < names.size(); i++) {
            int end = i + 1 < starts.size() ? starts.get(i + 1) : source.length();
            if (source.substring(starts.get(i), end).contains("allowEmptyShould(true)")) {
                vacuous.add(names.get(i));
            }
        }
        assertThat(vacuous)
                .as("a rule with allowEmptyShould(true) passes when nothing matches it. That is the right"
                        + " answer for a rule guarding a practice we have never had, and the wrong one for a"
                        + " rule whose subject was renamed out from under it: the second kind goes green"
                        + " forever and nobody is told. Add a rule here only with the reason it can be empty.")
                .containsExactlyInAnyOrderElementsOf(MAY_PASS_ON_AN_EMPTY_SET);
    }

    /** Every drift guard class in the repository, whichever module it lives in. */
    private static List<Path> driftGuards() {
        List<Path> guards = new ArrayList<>();
        try (Stream<Path> tree = Files.walk(repoRoot())) {
            tree.filter(path -> path.getFileName().toString().endsWith("DriftTest.java"))
                    .filter(path -> !path.toString().contains("/build/"))
                    .sorted()
                    .forEach(guards::add);
        } catch (IOException e) {
            throw new UncheckedIOException("could not walk the repository", e);
        }
        assertThat(guards).as("expected the drift guards to be found on disk").isNotEmpty();
        return guards;
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
