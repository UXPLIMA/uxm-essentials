package com.uxplima.uxmessentials.survival.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.junit.jupiter.api.Test;

/**
 * Pins {@link SurvivalConfig}'s resolution from the module's scoped config: an empty store falls back to the shipped
 * defaults (both mechanics on, the common ore set for veinminer), and explicit keys override each knob.
 */
class SurvivalConfigTest {

    @Test
    void anEmptyStoreYieldsTheShippedDefaults() {
        SurvivalConfig config = SurvivalConfig.from(new FixedConfig(Map.of()));

        assertThat(config.enabled()).isTrue();

        SurvivalConfig.TreeFeller tree = config.treeFeller();
        assertThat(tree.enabled()).isTrue();
        assertThat(tree.requireAxe()).isTrue();
        assertThat(tree.durabilityDrain()).isTrue();
        assertThat(tree.durabilityMultiplier()).isEqualTo(1);
        assertThat(tree.maxBlocks()).isEqualTo(64);
        assertThat(tree.hungerCost()).isZero();
        assertThat(tree.sneakRequired()).isFalse();
        assertThat(tree.replantSaplings()).isTrue();

        SurvivalConfig.Veinminer vein = config.veinminer();
        assertThat(vein.enabled()).isTrue();
        assertThat(vein.maxBlocks()).isEqualTo(64);
        assertThat(vein.toolDurability()).isTrue();
        assertThat(vein.hungerCost()).isZero();
        assertThat(vein.sneakRequired()).isTrue();
        assertThat(vein.blocks()).contains("COAL_ORE", "DIAMOND_ORE", "ANCIENT_DEBRIS");

        SurvivalConfig.LeafDecay leaf = config.leafDecay();
        assertThat(leaf.enabled()).isTrue();
        assertThat(leaf.radius()).isEqualTo(4);
        assertThat(leaf.delayTicks()).isEqualTo(4);

        assertThat(config.farmProtect().enabled()).isTrue();

        SurvivalConfig.FarmAssist farm = config.farmAssist();
        assertThat(farm.enabled()).isTrue();
        assertThat(farm.crops()).contains("WHEAT", "CARROTS", "POTATOES", "BEETROOTS", "NETHER_WART");
    }

    @Test
    void explicitKeysOverrideEachKnob() {
        SurvivalConfig config = SurvivalConfig.from(new FixedConfig(Map.ofEntries(
                Map.entry("enabled", false),
                Map.entry("tree-feller.require-axe", false),
                Map.entry("tree-feller.max-blocks", 12),
                Map.entry("tree-feller.sneak-required", true),
                Map.entry("veinminer.enabled", false),
                Map.entry("veinminer.max-blocks", 32),
                Map.entry("veinminer.blocks", List.of("COAL_ORE", "IRON_ORE")),
                Map.entry("leaf-decay.radius", 6),
                Map.entry("leaf-decay.delay-ticks", 10),
                Map.entry("farmprotect.enabled", false),
                Map.entry("farmassist.crops", List.of("WHEAT", "CARROTS")))));

        assertThat(config.enabled()).isFalse();
        assertThat(config.treeFeller().requireAxe()).isFalse();
        assertThat(config.treeFeller().maxBlocks()).isEqualTo(12);
        assertThat(config.treeFeller().sneakRequired()).isTrue();
        assertThat(config.veinminer().enabled()).isFalse();
        assertThat(config.veinminer().maxBlocks()).isEqualTo(32);
        assertThat(config.veinminer().blocks()).containsExactly("COAL_ORE", "IRON_ORE");
        assertThat(config.leafDecay().radius()).isEqualTo(6);
        assertThat(config.leafDecay().delayTicks()).isEqualTo(10);
        assertThat(config.farmProtect().enabled()).isFalse();
        assertThat(config.farmAssist().crops()).containsExactly("WHEAT", "CARROTS");
    }

    /** A map-backed {@link ConfigStore} that honours the boolean/int/double/list getters the config reads. */
    private record FixedConfig(Map<String, Object> values) implements ConfigStore {
        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return values.get(path) instanceof Boolean b ? b : fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return values.get(path) instanceof String s ? s : fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return values.get(path) instanceof Integer i ? i : fallback;
        }

        @Override
        public double getDouble(String path, double fallback) {
            return values.get(path) instanceof Number n ? n.doubleValue() : fallback;
        }

        @Override
        @SuppressWarnings("unchecked")
        public List<String> getStringList(String path, List<String> fallback) {
            return values.get(path) instanceof List<?> list ? List.copyOf((List<String>) list) : fallback;
        }
    }
}
