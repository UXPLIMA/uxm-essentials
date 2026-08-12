package com.uxplima.uxmessentials.drift;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.PlaceholderContexts;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.PlaceholderResolver;
import com.uxplima.uxmessentials.shared.application.placeholder.PlaceholderCatalog;
import com.uxplima.uxmessentials.shared.application.placeholder.PlaceholderSpec;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/**
 * The placeholder catalogue and the resolver say the same thing, in both directions.
 *
 * <p>A key used to exist only as a branch inside the resolver, so the published list was written from memory and
 * fell behind. Checking one direction alone would not hold it: a key the resolver answers but the catalogue omits is
 * invisible to operators, while a key the catalogue promises and nothing answers renders as the raw token in
 * somebody's scoreboard. The forward check resolves every catalogued key for real against a resolver with no seams
 * wired, which is exactly the state a fresh server with every module disabled is in.
 */
class PlaceholderCatalogDriftTest {

    /** A key the resolver answers, named in a {@code case} label. */
    private static final Pattern CASE_LABEL = Pattern.compile("case ((?:\"[a-z0-9_]+\"(?:,\\s*)?)+)\\s*(?:->|:)");

    /** A key the resolver answers, named in a {@code startsWith("...")} branch. */
    private static final Pattern INLINE_PREFIX = Pattern.compile("startsWith\\(\"([a-z0-9_]+)\"\\)");

    private static final Pattern QUOTED = Pattern.compile("\"([a-z0-9_]+)\"");

    private static final Path RESOLVER =
            Path.of("src/main/java/com/uxplima/uxmessentials/shared/adapter/outbound/papi/PlaceholderResolver.java");

    /**
     * Words that appear in a {@code case} label without being a key of their own: the values a branch renders, and
     * the tails read by a helper that is reached through a family whose head is catalogued. Every line is a claim a
     * person made, not a pattern that happened to match.
     */
    private static final Set<String> NOT_KEYS = Set.of(
            // Rendered values rather than keys.
            "thunder",
            "rain",
            "clear",
            "free",
            "unlimited",
            // Vote periods, which are the open segment of votes_<period>.
            "daily",
            "weekly",
            "monthly",
            "alltime",
            // Fields read off a row or view, reached through a catalogued family.
            "name",
            "uuid",
            "amount",
            "formatted",
            "votes",
            "owner",
            "world",
            "visits",
            "cost",
            "x",
            "y",
            "z",
            "current",
            "best");

    @Test
    void everyCataloguedKeyIsAnsweredByTheResolver() {
        PlaceholderResolver resolver =
                new PlaceholderResolver(PlaceholderContexts.builder().build());
        PlayerRef who = new PlayerRef(UUID.randomUUID(), "Drift");

        PlayerRef other = new PlayerRef(UUID.randomUUID(), "Drifter");

        Set<String> unanswered = new TreeSet<>();
        for (PlaceholderSpec spec : PlaceholderCatalog.all()) {
            String key = spec.sampled("example");
            // A relational key is answered through the two-player form PlaceholderAPI routes under rel_.
            boolean answered = spec.relational()
                    ? resolver.resolveRelational(who, other, key).isPresent()
                    : resolver.resolve(who, true, key).isPresent();
            if (!answered) {
                unanswered.add(spec.key());
            }
        }

        assertThat(unanswered)
                .describedAs("the catalogue promises keys the resolver renders as the raw token")
                .isEmpty();
    }

    @Test
    void aKeyOutsideTheCatalogueIsNotAnswered() {
        PlaceholderResolver resolver =
                new PlaceholderResolver(PlaceholderContexts.builder().build());
        PlayerRef who = new PlayerRef(UUID.randomUUID(), "Drift");

        assertThat(resolver.resolve(who, true, "nothing_like_this")).isEmpty();
        assertThat(resolver.resolveRelational(who, who, "nothing_like_this")).isEmpty();
        assertThat(PlaceholderCatalog.find("nothing_like_this")).isEmpty();
    }

    @Test
    void everyKeyTheResolverNamesIsInTheCatalogue() {
        String source = read(Path.of("").toAbsolutePath().resolve(RESOLVER));

        Set<String> named = new LinkedHashSet<>();
        Matcher cases = CASE_LABEL.matcher(source);
        while (cases.find()) {
            Matcher quoted = QUOTED.matcher(cases.group(1));
            while (quoted.find()) {
                named.add(quoted.group(1));
            }
        }
        Matcher prefixes = INLINE_PREFIX.matcher(source);
        while (prefixes.find()) {
            named.add(prefixes.group(1));
        }

        Set<String> uncatalogued = new TreeSet<>();
        for (String key : named) {
            if (NOT_KEYS.contains(key) || catalogued(key)) {
                continue;
            }
            uncatalogued.add(key);
        }

        assertThat(uncatalogued)
                .describedAs("the resolver answers keys no operator can discover")
                .isEmpty();
    }

    @Test
    void noKeyIsDeclaredTwice() {
        List<String> keys =
                PlaceholderCatalog.all().stream().map(PlaceholderSpec::key).toList();

        assertThat(keys).doesNotHaveDuplicates();
    }

    @Test
    void everyAreaIsAModuleOrTheKernel() {
        Set<String> modules = new DefaultModuleRegistry()
                .all().stream().map(module -> module.id().value()).collect(Collectors.toCollection(TreeSet::new));

        assertThat(PlaceholderCatalog.areas())
                .allSatisfy(area -> assertThat(area.equals("shared") || modules.contains(area))
                        .describedAs("area '%s' is neither a registered module nor the kernel", area)
                        .isTrue());
    }

    /**
     * Whether the catalogue knows {@code key}: either as a key of its own, as the head of a family, or as a tail
     * a catalogued key ends with, which is how the sub-branch of a family reads in the source.
     */
    private static boolean catalogued(String key) {
        if (PlaceholderCatalog.find(key).isPresent()) {
            return true;
        }
        return PlaceholderCatalog.all().stream()
                .anyMatch(spec -> spec.key().startsWith(key)
                        || spec.key().endsWith(key)
                        || spec.key().contains("_" + key + "_")
                        || spec.head().endsWith(key));
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException failed) {
            throw new UncheckedIOException("cannot read " + path, failed);
        }
    }
}
