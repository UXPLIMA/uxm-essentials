package com.uxplima.uxmessentials.worlds.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.worlds.domain.BiomeId;
import com.uxplima.uxmessentials.worlds.domain.BlockId;
import com.uxplima.uxmessentials.worlds.domain.FlatLayer;
import com.uxplima.uxmessentials.worlds.domain.FlatLayerPlan;
import org.junit.jupiter.api.Test;

/**
 * Pins the worlds module's generator config view: the flat-layer plan parsed from the
 * {@code generators.flat.layers} list and the void/flat biome ids, each with the spec's default when
 * the config omits the key. The store is the module-scoped view, so keys are read relative to
 * {@code modules.worlds}.
 */
class WorldsSettingsTest {

    @Test
    void flatLayersParseTheConfiguredBands() {
        WorldsSettings settings = new WorldsSettings(new FixedConfig(Map.of(
                "generators.flat.layers",
                List.of("minecraft:bedrock 1", "minecraft:stone 2", "minecraft:grass_block 1"))));

        assertThat(settings.flatLayers().layers())
                .containsExactly(
                        new FlatLayer(BlockId.of("minecraft:bedrock"), 1),
                        new FlatLayer(BlockId.of("minecraft:stone"), 2),
                        new FlatLayer(BlockId.of("minecraft:grass_block"), 1));
    }

    @Test
    void flatLayersFallBackToTheClassicFlatPlanWhenAbsent() {
        WorldsSettings settings = new WorldsSettings(new FixedConfig(Map.of()));

        assertThat(settings.flatLayers()).isEqualTo(FlatLayerPlan.defaults());
    }

    @Test
    void voidBiomeDefaultsToPlainsWhenAbsent() {
        WorldsSettings settings = new WorldsSettings(new FixedConfig(Map.of()));

        assertThat(settings.voidBiome()).isEqualTo(BiomeId.of("plains"));
    }

    @Test
    void voidAndFlatBiomesAreReadFromConfig() {
        WorldsSettings settings = new WorldsSettings(new FixedConfig(
                Map.of("generators.void.biome", "the_void", "generators.flat.biome", "minecraft:desert")));

        assertThat(settings.voidBiome()).isEqualTo(BiomeId.of("the_void"));
        assertThat(settings.flatBiome()).isEqualTo(BiomeId.of("minecraft:desert"));
    }

    @Test
    void flatBiomeDefaultsToPlainsWhenAbsent() {
        WorldsSettings settings = new WorldsSettings(new FixedConfig(Map.of()));

        assertThat(settings.flatBiome()).isEqualTo(BiomeId.of("plains"));
    }

    /** A map-backed {@link ConfigStore} addressing keys by their dotted path relative to the module root. */
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
        @SuppressWarnings("unchecked") // the test only ever stores List<String> under list keys
        public List<String> getStringList(String path, List<String> fallback) {
            return values.get(path) instanceof List<?> list ? (List<String>) list : fallback;
        }
    }
}
