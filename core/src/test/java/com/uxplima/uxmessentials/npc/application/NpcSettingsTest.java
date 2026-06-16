package com.uxplima.uxmessentials.npc.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.junit.jupiter.api.Test;

/**
 * Pins the npc module's typed config view, in particular the optional MineSkin API key: absent means the
 * unauthenticated tier (an empty key), a configured value is returned stripped of surrounding whitespace so it
 * never reaches the {@code Authorization} header malformed.
 */
class NpcSettingsTest {

    @Test
    void mineSkinApiKeyDefaultsToEmptyWhenAbsent() {
        NpcSettings settings = new NpcSettings(new FixedConfig(Map.of()));

        assertThat(settings.mineSkinApiKey()).isEmpty();
    }

    @Test
    void mineSkinApiKeyIsReadAndStripped() {
        NpcSettings settings = new NpcSettings(new FixedConfig(Map.of("skin.mineskin-api-key", "  abc-123  ")));

        assertThat(settings.mineSkinApiKey()).isEqualTo("abc-123");
    }

    @Test
    void otherKnobsKeepTheirDefaults() {
        NpcSettings settings = new NpcSettings(new FixedConfig(Map.of()));

        assertThat(settings.lookRange()).isEqualTo(12.0);
        assertThat(settings.clickCooldown().toMillis()).isEqualTo(500L);
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
    }
}
