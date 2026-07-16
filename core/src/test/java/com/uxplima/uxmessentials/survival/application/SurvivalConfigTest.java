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
    }

    @Test
    void explicitKeysOverrideEachKnob() {
        SurvivalConfig config = SurvivalConfig.from(new FixedConfig(Map.of(
                "enabled", false,
                "tree-feller.require-axe", false,
                "tree-feller.max-blocks", 12,
                "tree-feller.sneak-required", true,
                "veinminer.enabled", false,
                "veinminer.max-blocks", 32,
                "veinminer.blocks", List.of("COAL_ORE", "IRON_ORE"))));

        assertThat(config.enabled()).isFalse();
        assertThat(config.treeFeller().requireAxe()).isFalse();
        assertThat(config.treeFeller().maxBlocks()).isEqualTo(12);
        assertThat(config.treeFeller().sneakRequired()).isTrue();
        assertThat(config.veinminer().enabled()).isFalse();
        assertThat(config.veinminer().maxBlocks()).isEqualTo(32);
        assertThat(config.veinminer().blocks()).containsExactly("COAL_ORE", "IRON_ORE");
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
