package com.uxplima.uxmessentials.staff.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.staff.adapter.StaffAdapterFakes.NoopLogger;
import org.junit.jupiter.api.Test;

/**
 * {@link StaffSettings} reads the STAFF-C in-mode perk flags: both {@code flight-on-enter} and
 * {@code night-vision-on-enter} default to on, and each honours an explicit override.
 */
class StaffSettingsTest {

    @Test
    void thePerkFlagsDefaultToOn() {
        StaffSettings settings = StaffAdapterFakes.defaultSettings();

        assertThat(settings.flightOnEnter()).isTrue();
        assertThat(settings.nightVisionOnEnter()).isTrue();
    }

    @Test
    void thePerkFlagsHonourExplicitFalseOverrides() {
        ConfigStore config = new FlagConfig(Map.of(
                "flight-on-enter", false,
                "night-vision-on-enter", false));

        StaffSettings settings = new StaffSettings(config, new NoopLogger());

        assertThat(settings.flightOnEnter()).isFalse();
        assertThat(settings.nightVisionOnEnter()).isFalse();
    }

    /** A config that returns the caller's fallback except for the boolean paths it is explicitly seeded with. */
    private static final class FlagConfig implements ConfigStore {
        private final Map<String, Boolean> booleans;

        FlagConfig(Map<String, Boolean> booleans) {
            this.booleans = Map.copyOf(booleans);
        }

        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return booleans.getOrDefault(path, fallback);
        }

        @Override
        public String getString(String path, String fallback) {
            return fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return fallback;
        }
    }
}
