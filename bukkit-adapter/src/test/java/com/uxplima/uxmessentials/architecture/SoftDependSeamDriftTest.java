package com.uxplima.uxmessentials.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * The soft-depend seam guard (CLAUDE.md §3 "constructor injection / no duplicated wiring",
 * docs/01-architecture.md optional-integration section, docs/05-testing.md §16).
 *
 * <p><strong>The bug this freezes out.</strong> Every optional plugin is reached the same way: a present-guard,
 * then the SDK strictly past it. The guard is the load-bearing half, because a class that names an absent SDK
 * throws {@code NoClassDefFoundError} the moment it links. When each feature writes its own copy of that guard,
 * the same question gets answered several ways: three features asked for LuckPerms and two of them tested only
 * that the plugin was <em>installed</em>, never that it had <em>enabled</em>, so a LuckPerms that failed its own
 * startup read as live and the next call went straight into it. Four features had also each copied the reflective
 * walk down {@code WorldGuard.getInstance().getPlatform().getRegionContainer()}; a WorldGuard release that moves
 * one step of that chain breaks every copy silently and independently, and the copy somebody forgets to update is
 * the one that fails in production.
 *
 * <p><strong>The invariant.</strong> One optional plugin, one presence seam. Each soft-depend declared in
 * {@code paper-plugin.yml} may be probed from exactly one production source file, the class that owns the
 * integration ({@code PlaceholderApiSupport}, {@code LuckPermsAccess}, {@code WorldGuardReflection},
 * {@code LandsClaimProvider}, ...). Everything else asks that class.
 *
 * <p><strong>What it scans.</strong> A line-level source scan over every module's {@code src/main/java}. The
 * plugin names come from {@code paper-plugin.yml}'s {@code dependencies.server} block rather than a list kept
 * here, so a soft-depend added to the manifest is covered by this guard from the moment it is declared, with no
 * edit to this test. A file counts as probing plugin {@code P} when it names {@code P} in a plugin-manager
 * present-check ({@code getPlugin("P")} / {@code isPluginEnabled("P")}) or declares the plugin name as a string
 * constant, which is the shape a probe hides behind when it is one indirection deep. Comment and Javadoc lines
 * are skipped, so a class may freely <em>describe</em> the guard it sits behind.
 *
 * <p><strong>Honest limits.</strong> This is a source scan, not a call graph: it proves that only one file asks
 * the question, not that every caller of the SDK routes through the answer. It also cannot see a probe that
 * builds the plugin name at runtime, which no production class does today and which the self-test below does not
 * pretend to catch.
 *
 * <p><strong>The exception table.</strong> {@link #SHARED_SEAMS} names, per plugin, the exact files allowed to
 * span more than one, and each entry states why. Every current entry is a design rather than debt: two
 * unrelated Vault services, and a PlayerPoints back-end beside a PlayerPoints balance feed. Pinning the file
 * names means none of them can spread further: a third file probing Vault fails the build exactly like a second
 * file probing Jobs would. Floodgate and Geyser hold no entry, because no file here probes them any more:
 * uxmLib's uxmlib-bedrock module owns both guards, and this plugin only asks its two factories for the detector
 * and the screen.
 */
class SoftDependSeamDriftTest {

    /**
     * Plugins whose probe legitimately, or for now, spans more than one file, mapped to the exact file set
     * allowed. Anything outside these sets fails, so an entry is a pin, not a licence.
     */
    private static final Map<String, Set<String>> SHARED_SEAMS = Map.of(
            // Two distinct Vault services with nothing in common but the plugin name: the economy provider
            // bridge, and the two hooks SPI implementations for its economy and permission services.
            "Vault",
            Set.of("ForeignEconomyProviders.java", "VaultEconomyHook.java", "VaultPermissionHook.java"),
            // A currency back-end and a balance feed: separate ports, separate lifetimes, same plugin.
            "PlayerPoints",
            Set.of("PlayerPointsBalanceFeed.java", "PlayerPointsCurrencyBackend.java"));

    /** A plugin-manager present-check naming the plugin inline: the direct form of the guard. */
    private static final Pattern PRESENT_CHECK =
            Pattern.compile("(?:getPlugin|isPluginEnabled)\\s*\\(\\s*\"([A-Za-z0-9_-]+)\"");

    /** A plugin name held as a string constant: the form a probe takes when it is one indirection deep. */
    private static final Pattern NAME_CONSTANT = Pattern.compile("String\\s+\\w+\\s*=\\s*\"([A-Za-z0-9_-]+)\"\\s*;");

    /**
     * The WorldGuard singleton, the head of every reflective walk into it. Duplicating that walk is what this
     * round removed, so the class name may be spelled in exactly one place.
     */
    private static final String WORLD_GUARD_BOOTSTRAP = "\"com.sk89q.worldguard.WorldGuard\"";

    private static final String WORLD_GUARD_SEAM = "WorldGuardReflection.java";

    /**
     * WorldEdit's Bukkit-to-WorldEdit adapter, the head of the location/world conversion every region query
     * needs. Allowed in the WorldGuard seam and in the WorldEdit selection reader, which talks to WorldEdit
     * itself rather than to WorldGuard.
     */
    private static final String WORLD_EDIT_ADAPTER = "\"com.sk89q.worldedit.bukkit.BukkitAdapter\"";

    private static final Set<String> WORLD_EDIT_ADAPTER_SEAMS =
            Set.of(WORLD_GUARD_SEAM, "WorldEditRegionSelection.java");

    @Test
    void eachSoftDependIsProbedFromOnePlace() {
        Map<String, Set<String>> probes = probesByPlugin();
        List<String> violations = new ArrayList<>();
        probes.forEach((plugin, files) -> {
            Set<String> allowed = SHARED_SEAMS.get(plugin);
            if (allowed == null) {
                if (files.size() > 1) {
                    violations.add(plugin + " probed from " + files);
                }
                return;
            }
            Set<String> unexpected = new TreeSet<>(files);
            unexpected.removeAll(allowed);
            if (!unexpected.isEmpty()) {
                violations.add(plugin + " probed from " + unexpected + ", outside its pinned seams " + allowed);
            }
        });
        assertThat(violations)
                .as(
                        "one optional plugin, one presence seam: route the check through the class that owns the "
                                + "integration instead of copying the guard (a copy that tests only getPlugin != null "
                                + "treats a plugin that failed its own startup as live):%n%s",
                        String.join("\n", violations))
                .isEmpty();
    }

    @Test
    void theWorldGuardBootstrapChainIsSpelledOnce() {
        assertThat(filesNaming(WORLD_GUARD_BOOTSTRAP))
                .as("the WorldGuard singleton starts every reflective walk into it; a duplicated chain breaks "
                        + "silently and independently on the next WorldGuard release. Call WorldGuardReflection "
                        + "instead (instance / regionContainer / flagRegistry / createQuery / adapt / adaptWorld / "
                        + "applicableRegions).")
                .containsExactly(WORLD_GUARD_SEAM);
    }

    @Test
    void theWorldEditAdapterIsSpelledOnlyInItsTwoSeams() {
        assertThat(filesNaming(WORLD_EDIT_ADAPTER))
                .as("Bukkit-to-WorldEdit conversion belongs to WorldGuardReflection#adapt / #adaptWorld for region "
                        + "work, and to the WorldEdit selection reader for WorldEdit's own selection API")
                .containsExactlyInAnyOrderElementsOf(new TreeSet<>(WORLD_EDIT_ADAPTER_SEAMS));
    }

    @Test
    void softDependNamesComeFromTheManifest() {
        // The plugin-name source is the manifest, not a list in this test: a new soft-depend must be covered the
        // moment it is declared. Three of the long-standing ones anchor that the parse still finds the block.
        assertThat(softDependNames())
                .contains("LuckPerms", "WorldGuard", "PlaceholderAPI")
                .hasSizeGreaterThan(20);
    }

    @Test
    void probePatternSelfTest() {
        // Examples the scan MUST read as a probe.
        assertThat(probed("if (server.getPluginManager().isPluginEnabled(\"WorldGuard\")) {"))
                .isEqualTo("WorldGuard");
        assertThat(probed("Plugin p = server.getPluginManager().getPlugin(\"dynmap\");"))
                .isEqualTo("dynmap");
        assertThat(probed("private static final String PLUGIN = \"LuckPerms\";"))
                .isEqualTo("LuckPerms");
        // Examples it MUST NOT: a reflective SDK class name, a log argument, and an unrelated constant.
        assertThat(probed("Class.forName(\"com.sk89q.worldguard.WorldGuard\");"))
                .isNull();
        assertThat(probed("log.warn(\"event=failed integration={}\", \"WorldGuard\");"))
                .isNull();
        assertThat(probed("private static final String FLAG_NAME = \"set-pwarp\";"))
                .isEqualTo("set-pwarp");
    }

    /** The plugin name a single line probes, or null. Mirrors the scan so the self-test exercises the real thing. */
    private static @Nullable String probed(String line) {
        Matcher present = PRESENT_CHECK.matcher(line);
        if (present.find()) {
            return present.group(1);
        }
        Matcher constant = NAME_CONSTANT.matcher(line);
        return constant.find() ? constant.group(1) : null;
    }

    /** Every declared soft-depend, mapped to the production file names that probe for it. */
    private static Map<String, Set<String>> probesByPlugin() {
        Set<String> names = softDependNames();
        Map<String, Set<String>> found = new TreeMap<>();
        forEachProductionLine((path, line) -> {
            String plugin = probed(line);
            if (plugin != null && names.contains(plugin)) {
                found.computeIfAbsent(plugin, key -> new TreeSet<>())
                        .add(path.getFileName().toString());
            }
        });
        return found;
    }

    /** The production file names containing a literal, sorted, so an assertion reads as a set of owners. */
    private static List<String> filesNaming(String literal) {
        Set<String> files = new TreeSet<>();
        forEachProductionLine((path, line) -> {
            if (line.contains(literal)) {
                files.add(path.getFileName().toString());
            }
        });
        return List.copyOf(files);
    }

    /** Feed every non-comment production line to the visitor, across every module's main source root. */
    private static void forEachProductionLine(LineVisitor visitor) {
        for (Path root : sourceRoots()) {
            try (Stream<Path> files = Files.walk(root)) {
                files.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                    for (String line : readLines(path)) {
                        String stripped = line.stripLeading();
                        if (stripped.startsWith("//") || stripped.startsWith("*") || stripped.startsWith("/*")) {
                            continue;
                        }
                        visitor.accept(path, line);
                    }
                });
            } catch (IOException failure) {
                throw new UncheckedIOException("failed to walk " + root, failure);
            }
        }
    }

    /**
     * The soft-depend names declared in {@code paper-plugin.yml} under {@code dependencies.server}. Parsed by
     * indentation rather than with a YAML library: the block is a flat list of plugin names at one fixed depth,
     * and reading it directly keeps this guard free of a parser dependency the rest of the test tree does not use.
     */
    private static Set<String> softDependNames() {
        Path manifest = repoRoot().resolve("bukkit-adapter/src/main/resources/paper-plugin.yml");
        assertThat(Files.isRegularFile(manifest))
                .as("expected the plugin manifest at %s", manifest)
                .isTrue();
        Set<String> names = new HashSet<>();
        boolean inServerBlock = false;
        for (String line : readLines(manifest)) {
            if (line.startsWith("  server:")) {
                inServerBlock = true;
                continue;
            }
            if (inServerBlock && !line.isBlank() && !line.startsWith("   ") && !line.startsWith("  #")) {
                break; // left the dependencies block
            }
            Matcher entry = Pattern.compile("^ {4}([A-Za-z0-9_-]+):\\s*$").matcher(line);
            if (inServerBlock && entry.matches()) {
                names.add(entry.group(1));
            }
        }
        return Set.copyOf(names);
    }

    private static List<String> readLines(Path path) {
        try {
            return Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException("failed to read " + path, failure);
        }
    }

    /** Every Gradle module holding production Java, so an integration probe cannot hide in a non-Bukkit module. */
    private static final List<String> MODULES = List.of(
            "api",
            "core",
            "bukkit-adapter",
            "persistence-adapter",
            "velocity-adapter",
            "discord-adapter",
            "redis-adapter",
            "migration");

    /** Every module's production source root, so an integration probe cannot hide in a non-Bukkit module. */
    private static List<Path> sourceRoots() {
        Path root = repoRoot();
        Map<String, Path> roots = new LinkedHashMap<>();
        for (String module : MODULES) {
            Path src = root.resolve(module).resolve("src/main/java");
            if (Files.isDirectory(src)) {
                roots.put(module, src);
            }
        }
        assertThat(roots)
                .as("expected the module production source roots under %s", root)
                .isNotEmpty();
        return List.copyOf(roots.values());
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

    @FunctionalInterface
    private interface LineVisitor {
        void accept(Path path, String line);
    }
}
