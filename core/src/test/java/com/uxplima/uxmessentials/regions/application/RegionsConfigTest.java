package com.uxplima.uxmessentials.regions.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.junit.jupiter.api.Test;

/**
 * Covers {@link RegionsConfig#from} resolving each knob from the scoped store, falling back to the shipped defaults
 * when a key is absent, clamping an out-of-range page size into a single chest page, and normalising the editable
 * flag list.
 */
class RegionsConfigTest {

    @Test
    void resolvesEveryKnobFromTheStore() {
        RegionsConfig config = RegionsConfig.from(new MapConfig(
                Map.of("enabled", false, "list.page-size", 20), Map.of("flags.editable", List.of("pvp", "build"))));

        assertThat(config.enabled()).isFalse();
        assertThat(config.listPageSize()).isEqualTo(20);
        assertThat(config.editableFlags()).containsExactly("pvp", "build");
    }

    @Test
    void fallsBackToTheShippedDefaultsWhenKeysAreAbsent() {
        RegionsConfig config = RegionsConfig.from(new MapConfig(Map.of()));

        assertThat(config.enabled()).isTrue();
        assertThat(config.listPageSize()).isEqualTo(45);
        assertThat(config.editableFlags()).contains("pvp", "build", "mob-spawning");
    }

    @Test
    void normalisesTheFlagListLowerCasingTrimmingAndDeduplicating() {
        RegionsConfig config = RegionsConfig.from(
                new MapConfig(Map.of(), Map.of("flags.editable", List.of("PvP", " Build ", "pvp", "", "Entry"))));

        assertThat(config.editableFlags()).containsExactly("pvp", "build", "entry");
    }

    @Test
    void clampsAnOversizedPageSizeToaSingleChestPage() {
        RegionsConfig config = RegionsConfig.from(new MapConfig(Map.of("list.page-size", 200)));

        assertThat(config.listPageSize()).isEqualTo(45);
    }

    @Test
    void clampsAnUnderSizedPageSizeUpToOne() {
        RegionsConfig config = RegionsConfig.from(new MapConfig(Map.of("list.page-size", 0)));

        assertThat(config.listPageSize()).isEqualTo(1);
    }

    /** A map-backed {@link ConfigStore} keyed by the module-scoped path, plus a separate map for string lists. */
    private record MapConfig(Map<String, Object> values, Map<String, List<String>> lists) implements ConfigStore {

        MapConfig(Map<String, Object> values) {
            this(values, Map.of());
        }

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
        public List<String> getStringList(String path, List<String> fallback) {
            return lists.getOrDefault(path, fallback);
        }
    }
}
