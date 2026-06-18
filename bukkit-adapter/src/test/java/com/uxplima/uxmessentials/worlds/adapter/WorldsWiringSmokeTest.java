package com.uxplima.uxmessentials.worlds.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.worlds.application.WorldsSettings;
import org.junit.jupiter.api.Test;

class WorldsWiringSmokeTest {

    @Test
    void settingsReadDefaultsTrueAndOverride() {
        ConfigStore defaults = new ConfigStore() {
            @Override
            public boolean getBoolean(String path, boolean fallback) {
                return fallback;
            }

            @Override
            public String getString(String path, String fallback) {
                return fallback;
            }

            @Override
            public int getInt(String path, int fallback) {
                return fallback;
            }
        };
        WorldsSettings settings = new WorldsSettings(defaults);
        assertThat(settings.protectDefaultWorld()).isTrue();
        assertThat(settings.autoAdoptLoaded()).isTrue();
        assertThat(settings.autoLoadRegistered()).isTrue();

        ConfigStore off = new ConfigStore() {
            @Override
            public boolean getBoolean(String path, boolean fallback) {
                return path.endsWith("protect-default-world") ? false : fallback;
            }

            @Override
            public String getString(String path, String fallback) {
                return fallback;
            }

            @Override
            public int getInt(String path, int fallback) {
                return fallback;
            }
        };
        assertThat(new WorldsSettings(off).protectDefaultWorld()).isFalse();
    }
}
