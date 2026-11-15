package com.uxplima.uxmessentials.shared.application.permission;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns.CooldownKind;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns.CooldownStartPhase;
import com.uxplima.uxmessentials.shared.application.port.Warmups.WarmupKind;
import com.uxplima.uxmessentials.teleport.application.TeleportSettings;
import org.junit.jupiter.api.Test;

/**
 * Every exemption an operator can grant lives under one head, {@code uxmessentials.bypass.}, so a permission plugin
 * lists them together and the shared conventions hold. A node added later with {@code bypass} or {@code exempt}
 * somewhere in its middle fails here before an operator learns its name.
 */
class BypassNodesTest {

    private static final Pattern EXEMPTION_WORD = Pattern.compile("bypass|(^|[.-])exempt([.-]|$)");

    /** A warmup or cooldown kind built in the code, with its first argument: the feature, written or named. */
    private static final Pattern KIND =
            Pattern.compile("(?:new\\s+(?:[\\w.]+\\.)?(WarmupKind)|new\\s+(?:[\\w.]+\\.)?(CooldownKind)"
                    + "|(?:[\\w.]+\\.)?CooldownKind\\.(?:scoped|onTeleport)|(?<![\\w.])(scoped|onTeleport))"
                    + "\\(\\s*(\"[^\"]*\"|[A-Za-z_][A-Za-z0-9_]*)");

    /** A file that may call the factories bare, because it imports them statically. */
    private static final Pattern STATIC_FACTORY =
            Pattern.compile("import\\s+static\\s+[\\w.]*CooldownKind\\.(?:scoped|onTeleport|\\*)\\s*;");

    /** The ports declare the kinds and pass the feature through; every other file names a real feature. */
    private static final Set<String> PORTS = Set.of("Cooldowns.java", "Warmups.java");

    @Test
    void everyExemptionInTheCatalogueStartsWithTheBypassHead() {
        List<String> misplaced = PermissionCatalog.all().stream()
                .map(PermissionSpec::node)
                .filter(node -> EXEMPTION_WORD.matcher(node).find())
                .filter(node -> !node.startsWith(SharedPermissions.BYPASS))
                .toList();

        assertThat(misplaced).isEmpty();
    }

    @Test
    void theCooldownAndWarmupExemptionsAreBuiltUnderTheBypassHead() {
        assertThat(SharedPermissions.cooldownBypass("customcommand.example"))
                .isEqualTo("uxmessentials.bypass.cooldown.label.customcommand.example");
        assertThat(new CooldownKind("rtp", 30, CooldownStartPhase.TELEPORT).bypassNode())
                .isEqualTo("uxmessentials.bypass.cooldown.rtp");
        assertThat(CooldownKind.scoped("kit", "kit.starter", 60, CooldownStartPhase.TELEPORT)
                        .bypassNode())
                .isEqualTo(ContentPermissions.KIT_COOLDOWN_BYPASS);
        assertThat(new WarmupKind("tp", 3).bypassNode()).isEqualTo(TeleportPermissions.TP_WARMUP_BYPASS);
    }

    @Test
    void theNodesTheFactoriesBuildAreOnesTheCatalogueDeclares() {
        assertThat(PermissionCatalog.find(SharedPermissions.featureCooldownBypass("skin")))
                .map(PermissionSpec::node)
                .contains(SharedPermissions.featureCooldownBypass("<feature>"));
        assertThat(PermissionCatalog.find(SharedPermissions.cooldownBypass("kit")))
                .map(PermissionSpec::node)
                .contains(SharedPermissions.cooldownBypass("<label>"));
        assertThat(PermissionCatalog.find(SharedPermissions.featureWarmupBypass("customcommand")))
                .map(PermissionSpec::node)
                .contains(SharedPermissions.featureWarmupBypass("<feature>"));
        assertThat(PermissionCatalog.find(SharedPermissions.featureWarmupBypass("tp")))
                .map(PermissionSpec::node)
                .contains("uxmessentials.bypass.warmup.tp");
    }

    @Test
    void everyEarlierSpellingPointsAtADeclaredExemption() {
        for (Map.Entry<String, String> renamed : RenamedPermissions.all().entrySet()) {
            Optional<PermissionSpec> declared = PermissionCatalog.find(renamed.getValue());

            assertThat(renamed.getValue()).as(renamed.getKey()).startsWith(SharedPermissions.BYPASS);
            assertThat(declared)
                    .as(renamed.getKey() + " -> " + renamed.getValue())
                    .isPresent();
            assertThat(RenamedPermissions.current(renamed.getKey())).isEqualTo(renamed.getValue());
        }
    }

    @Test
    void aMenuLabelNamedLikeAFeatureNeverLiftsThatFeature() {
        assertThat(SharedPermissions.cooldownBypass("kit")).isNotEqualTo(ContentPermissions.KIT_COOLDOWN_BYPASS);
        assertThat(SharedPermissions.cooldownBypass("tp")).isNotEqualTo(SharedPermissions.featureCooldownBypass("tp"));
    }

    @Test
    void everyFeatureTheCodeGatesHasADeclaredExemptionAndAnUpgradeEntry() {
        List<String> problems = new ArrayList<>();
        Set<String> features = new TreeSet<>();
        for (Path file : mainSources()) {
            String source = read(file);
            Matcher kind = KIND.matcher(source);
            boolean staticFactories = STATIC_FACTORY.matcher(source).find();
            while (kind.find()) {
                if (kind.group(3) != null && !staticFactories) {
                    continue;
                }
                boolean warmup = kind.group(1) != null;
                Optional<String> feature = featureOf(source, kind.group(4));
                if (feature.isEmpty()) {
                    if (!PORTS.contains(file.getFileName().toString())) {
                        problems.add(file.getFileName() + ": cannot tell which feature " + kind.group(4) + " names");
                    }
                    continue;
                }
                String name = feature.get();
                String node = warmup
                        ? SharedPermissions.featureWarmupBypass(name)
                        : SharedPermissions.featureCooldownBypass(name);
                String earlier = SharedPermissions.NAMESPACE + name + (warmup ? ".warmup" : ".cooldown") + ".bypass";
                features.add((warmup ? "warmup " : "cooldown ") + name);
                if (PermissionCatalog.find(node).isEmpty()) {
                    problems.add(node + " is checked but not declared");
                }
                if (!node.equals(RenamedPermissions.all().get(earlier))) {
                    problems.add(earlier + " has no upgrade entry pointing at " + node);
                }
            }
        }

        assertThat(features).contains("cooldown tp", "cooldown kit", "warmup tp", "warmup customcommand");
        assertThat(problems).isEmpty();
    }

    @Test
    void aConfiguredNodeIsReadUnderItsCurrentSpelling() {
        assertThat(RenamedPermissions.current(" uxmessentials.economy.tax.bypass "))
                .isEqualTo(EconomyPermissions.ECONOMY_TAX_BYPASS);
        assertThat(RenamedPermissions.current("uxmessentials.cooldown.bypass.daily-reward"))
                .isEqualTo("uxmessentials.bypass.cooldown.label.daily-reward");
        assertThat(RenamedPermissions.current("myserver.vip.nospawn")).isEqualTo("myserver.vip.nospawn");
    }

    @Test
    void aKeptTeleportFileStillExemptsThroughTheCurrentNode() {
        TeleportSettings kept =
                new TeleportSettings(new OneString("spawn.exempt-permission", "uxmessentials.spawn.join.exempt"));
        TeleportSettings fresh = new TeleportSettings(new OneString("unrelated", ""));

        assertThat(kept.joinSpawnExemptPermission()).isEqualTo(TeleportPermissions.SPAWN_JOIN_EXEMPT);
        assertThat(fresh.joinSpawnExemptPermission()).isEqualTo("uxmessentials.bypass.join-spawn");
    }

    /** The feature a kind's first argument names: a literal, or a string constant declared in the same file. */
    private static Optional<String> featureOf(String source, String argument) {
        if (argument.startsWith("\"")) {
            return Optional.of(argument.substring(1, argument.length() - 1));
        }
        Matcher constant = Pattern.compile("String\\s+" + Pattern.quote(argument) + "\\s*=\\s*\"([^\"]+)\"")
                .matcher(source);
        return constant.find() ? Optional.of(constant.group(1)) : Optional.empty();
    }

    private static List<Path> mainSources() {
        String root = System.getProperty("uxm.repositoryRoot");
        assertThat(root)
                .as("the build passes uxm.repositoryRoot to the test JVM")
                .isNotNull();
        try (Stream<Path> modules = Files.list(Path.of(root))) {
            List<Path> files = new ArrayList<>();
            for (Path module : modules.toList()) {
                Path sources = module.resolve("src/main/java");
                if (Files.isDirectory(sources)) {
                    try (Stream<Path> walk = Files.walk(sources)) {
                        walk.filter(path -> path.toString().endsWith(".java")).forEach(files::add);
                    }
                }
            }
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

    /** A config holding one string; every other read answers its fallback. */
    private record OneString(String path, String value) implements ConfigStore {

        @Override
        public boolean getBoolean(String key, boolean fallback) {
            return fallback;
        }

        @Override
        public String getString(String key, String fallback) {
            return key.equals(path) ? value : fallback;
        }

        @Override
        public int getInt(String key, int fallback) {
            return fallback;
        }
    }
}
