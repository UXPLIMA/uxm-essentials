package com.uxplima.uxmessentials.shared.application.permission;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

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
        for (MainSources.Kind kind : MainSources.kinds()) {
            if (kind.feature().isEmpty()) {
                problems.add(kind.file() + ": cannot tell which feature " + kind.argument() + " names");
                continue;
            }
            boolean warmup = kind.warmup();
            String name = kind.feature().get();
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
