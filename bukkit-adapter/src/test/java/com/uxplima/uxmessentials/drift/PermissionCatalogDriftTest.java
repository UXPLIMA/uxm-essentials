package com.uxplima.uxmessentials.drift;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.permission.PermissionCatalog;
import com.uxplima.uxmessentials.shared.application.permission.PermissionSpec;
import org.junit.jupiter.api.Test;

/**
 * The permission catalogue and the running code say the same thing, in both directions.
 *
 * <p>A node used to live in three places nothing kept in step: a literal at the site that checks it, an entry in
 * {@code paper-plugin.yml} so the server knew it existed, and a row on the reference page. The catalogue collapsed
 * that to one, and this is what keeps it collapsed. Checking one direction alone would not: a node checked in code
 * and missing from the catalogue is invisible to operators and to their permission plugin, while an entry in the
 * catalogue that nothing reads is worse than nothing, because somebody grants it and waits for an effect that never
 * comes. The audit that produced the catalogue found thirty-nine of the first kind and six of the second.
 */
class PermissionCatalogDriftTest {

    /** A node written whole in Java, as {@code "uxmessentials.something"}. */
    private static final Pattern JAVA_LITERAL = Pattern.compile("\"(uxmessentials\\.[a-z0-9._-]+)\"");

    /** A node named by a shipped menu spec, as {@code perm:uxmessentials.something}. */
    private static final Pattern SPEC_REFERENCE = Pattern.compile("perm:(uxmessentials\\.[a-z0-9._-]+)");

    /**
     * Entries assembled somewhere the text scan cannot see them, with what assembles each. Kept short and kept
     * explicit: every line is a claim that something really does read the node, made by a person rather than by a
     * pattern that happened to match.
     */
    private static final Map<String, String> COMPOSED_ELSEWHERE = Map.of(
            // Composed by the info-page command from the page name an operator configured.
            "uxmessentials.communication.info.info", "the info-page prefix plus the page name",
            "uxmessentials.communication.info.motd", "the info-page prefix plus the page name",
            "uxmessentials.communication.info.rules", "the info-page prefix plus the page name",
            // Warmups.Feature builds "uxmessentials." + feature + ".warmup", then appends the tier or ".bypass".
            "uxmessentials.tp.warmup.<seconds>", "Warmups.Feature.warmupNode()",
            "uxmessentials.tp.warmup.bypass", "Warmups.Feature.bypassNode()");

    /** The per-module reload tier, composed by {@code ModuleId.permissionNode()} and checked below against the registry. */
    private static final String MODULE_TIER = "uxmessentials.module.";

    /** The catalogue's own package, left out of the scan so it cannot answer questions about itself. */
    private static final String CATALOGUE_PACKAGE =
            Path.of("shared", "application", "permission").toString();

    @Test
    void everyNodeTheCodeChecksIsInTheCatalogue() {
        Set<String> unknown = usedNodes().stream()
                .filter(node -> PermissionCatalog.find(node).isEmpty())
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(unknown)
                .describedAs("these nodes are checked in production but the catalogue does not declare them, so the "
                        + "server is never told they exist and no operator can find them")
                .isEmpty();
    }

    @Test
    void everyCatalogueEntryIsReachedFromProduction() {
        String sources = productionSources();

        Set<String> unread = PermissionCatalog.all().stream()
                .filter(spec -> !mentioned(spec, sources))
                .map(PermissionSpec::node)
                .collect(Collectors.toCollection(TreeSet::new));

        assertThat(unread)
                .describedAs("nothing in production reads these nodes, so granting one does nothing; either check it "
                        + "where it belongs or drop it from the catalogue")
                .isEmpty();
    }

    @Test
    void everyCommandIsGuardedByANodeTheCatalogueDeclares() {
        Set<String> unknown = new DefaultModuleRegistry()
                .all().stream()
                        .flatMap(module -> module.commands().stream())
                        .map(CommandSpec::permission)
                        .filter(node -> PermissionCatalog.find(node).isEmpty())
                        .collect(Collectors.toCollection(TreeSet::new));

        assertThat(unknown)
                .describedAs("a command is registered behind a node the catalogue has never heard of")
                .isEmpty();
    }

    /**
     * Every module has a reload tier and no tier names a module that is gone. This is the one place the module tier
     * is checked at all, because {@code ModuleId.permissionNode()} composes the node and no literal exists to find.
     */
    @Test
    void everyRegisteredModuleHasAReloadTierAndNoTierOutlivesItsModule() {
        Set<String> declared = PermissionCatalog.all().stream()
                .map(PermissionSpec::node)
                .filter(node -> node.startsWith(MODULE_TIER))
                .map(node -> node.substring(MODULE_TIER.length()))
                .collect(Collectors.toCollection(TreeSet::new));

        Set<String> modules = new DefaultModuleRegistry()
                .all().stream().map(module -> module.id().value()).collect(Collectors.toCollection(TreeSet::new));

        assertThat(declared)
                .describedAs("the uxmessentials.module.<id> tiers and the registered modules must be the same set")
                .isEqualTo(modules);
    }

    @Test
    void noNodeIsDeclaredTwice() {
        List<String> nodes =
                PermissionCatalog.all().stream().map(PermissionSpec::node).toList();

        assertThat(nodes).doesNotHaveDuplicates();
    }

    @Test
    void everyAreaIsAModuleOrTheKernel() {
        Set<String> modules = new DefaultModuleRegistry()
                .all().stream().map(module -> module.id().value()).collect(Collectors.toCollection(TreeSet::new));

        assertThat(PermissionCatalog.areas())
                .allSatisfy(area -> assertThat(area.equals("shared") || modules.contains(area))
                        .describedAs("area '%s' is neither a registered module nor the kernel", area)
                        .isTrue());
    }

    /**
     * Whether production reads this entry. A fixed node has to appear whole; a family only has to appear as its head,
     * because the rest of it is assembled at runtime from a number or a name.
     */
    private static boolean mentioned(PermissionSpec spec, String sources) {
        if (COMPOSED_ELSEWHERE.containsKey(spec.node()) || spec.node().startsWith(MODULE_TIER)) {
            return true;
        }
        if (spec.registrable()) {
            return sources.contains("\"" + spec.node() + "\"")
                    || sources.contains("perm:" + spec.node())
                    || builtFromABase(spec.node(), sources);
        }
        return headIsRead(spec.prefix(), sources);
    }

    /**
     * Whether a family's head is read, allowing for a head that is itself assembled. {@code /enchant} gates on
     * {@code uxmessentials.itemworld.enchant.<enchantment>} but writes {@code "uxmessentials.itemworld."} and appends
     * the verb and the type, so the family is proven by the longest prefix of its head that appears whole. The walk
     * stops before the bare {@code uxmessentials.} root, which would prove anything.
     */
    private static boolean headIsRead(String head, String sources) {
        String prefix = head;
        while (prefix.chars().filter(character -> character == '.').count() >= 2) {
            String bare = prefix.endsWith(".") ? prefix.substring(0, prefix.length() - 1) : prefix;
            if (sources.contains("\"" + prefix + "\"") || sources.contains("\"" + bare + "\"")) {
                return true;
            }
            int cut = bare.lastIndexOf('.');
            prefix = bare.substring(0, cut + 1);
        }
        return false;
    }

    /**
     * Whether a node is assembled from a base and a suffix rather than written whole, which is how a command with
     * many verbs guards each one: {@code BASE + "." + verb}. Deliberately loose, because the question being asked is
     * whether anything reads the node at all, not which line does.
     */
    private static boolean builtFromABase(String node, String sources) {
        int cut = node.lastIndexOf('.');
        if (cut < 0) {
            return false;
        }
        String base = node.substring(0, cut);
        String suffix = node.substring(cut + 1);
        return sources.contains("\"" + base + "\"")
                && (sources.contains("\"." + suffix + "\"") || sources.contains("\"" + suffix + "\""));
    }

    /**
     * Every node the production sources name, from Java literals and from shipped menu specs alike. Each pattern is
     * run only against the kind of file it belongs to: a quoted string in a config file is as likely to be a
     * database file name as a node, and only the {@code perm:} form there means a permission.
     */
    private static Set<String> usedNodes() {
        Set<String> found = new LinkedHashSet<>();
        collect(JAVA_LITERAL, sourcesEnding(".java"), found);
        collect(SPEC_REFERENCE, sourcesEnding(".conf"), found);
        // A literal ending in a dot is the head of a composed node, not a node; the family entry covers it.
        found.removeIf(node -> node.endsWith(".") || node.equals("uxmessentials"));
        assertThat(found).describedAs("expected to find node literals to check").hasSizeGreaterThan(300);
        return found;
    }

    private static void collect(Pattern pattern, String sources, Set<String> into) {
        Matcher matcher = pattern.matcher(sources);
        while (matcher.find()) {
            into.add(matcher.group(1));
        }
    }

    /** The production Java and the shipped menu specs, read as one body of text. */
    private static String productionSources() {
        return sourcesEnding(".java") + sourcesEnding(".conf");
    }

    /**
     * Every production file with the given extension, read as one body of text, with the catalogue itself left out.
     * Reading it back would make both directions of this test vacuous: every entry would be "mentioned" by its own
     * row, and every node the catalogue names would be "used".
     */
    private static String sourcesEnding(String extension) {
        StringBuilder all = new StringBuilder();
        for (Path root : sourceRoots()) {
            try (Stream<Path> files = Files.walk(root)) {
                List<Path> readable = files.filter(Files::isRegularFile)
                        .filter(path -> path.toString().endsWith(extension))
                        .filter(path -> !path.toString().contains(CATALOGUE_PACKAGE))
                        .toList();
                for (Path file : readable) {
                    all.append(Files.readString(file, StandardCharsets.UTF_8)).append('\n');
                }
            } catch (IOException unreadable) {
                throw new UncheckedIOException("failed to read the production sources under " + root, unreadable);
            }
        }
        return all.toString();
    }

    private static List<Path> sourceRoots() {
        Path repoRoot = repoRoot();
        List<Path> roots = new ArrayList<>();
        for (String path :
                List.of("core/src/main/java", "bukkit-adapter/src/main/java", "bukkit-adapter/src/main/resources")) {
            Path root = repoRoot.resolve(path);
            if (Files.isDirectory(root)) {
                roots.add(root);
            }
        }
        assertThat(roots)
                .describedAs("expected production source roots under %s", repoRoot)
                .isNotEmpty();
        return roots;
    }

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.exists(dir.resolve("settings.gradle.kts"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("could not locate the repo root (settings.gradle.kts)");
    }
}
