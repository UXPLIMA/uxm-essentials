package com.uxplima.uxmessentials.invrollback.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.junit.jupiter.api.Test;

/**
 * Covers {@link InvrollbackConfig#from} resolving each knob from the scoped store, and falling back to the
 * shipped defaults when a key is absent.
 */
class InvrollbackConfigTest {

    @Test
    void resolvesEveryKnobFromTheStore() {
        InvrollbackConfig config = InvrollbackConfig.from(new MapConfig(Map.of(
                "enabled", false,
                "capture.on-death", false,
                "capture.on-logout", true,
                "include-enderchest", false,
                "retention.max-per-player", 25,
                "retention.max-age-days", 7)));

        assertThat(config.enabled()).isFalse();
        assertThat(config.captureOnDeath()).isFalse();
        assertThat(config.captureOnLogout()).isTrue();
        assertThat(config.includeEnderchest()).isFalse();
        assertThat(config.maxPerPlayer()).isEqualTo(25);
        assertThat(config.maxAgeDays()).isEqualTo(7);
        assertThat(config.retentionPolicy().maxPerPlayer()).isEqualTo(25);
        assertThat(config.retentionPolicy().maxAgeDays()).isEqualTo(7);
    }

    @Test
    void fallsBackToTheShippedDefaultsWhenKeysAreAbsent() {
        InvrollbackConfig config = InvrollbackConfig.from(new MapConfig(Map.of()));

        assertThat(config.enabled()).isTrue();
        assertThat(config.captureOnDeath()).isTrue();
        assertThat(config.captureOnLogout()).isTrue();
        assertThat(config.includeEnderchest()).isTrue();
        assertThat(config.maxPerPlayer()).isEqualTo(10);
        assertThat(config.maxAgeDays()).isEqualTo(30);
    }

    /** A map-backed {@link ConfigStore} keyed by the module-scoped path. */
    private record MapConfig(Map<String, Object> values) implements ConfigStore {
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
