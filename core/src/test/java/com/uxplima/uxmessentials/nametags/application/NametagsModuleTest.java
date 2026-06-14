package com.uxplima.uxmessentials.nametags.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.junit.jupiter.api.Test;

class NametagsModuleTest {

    @Test
    void identityMatchesItsContextPackage() {
        assertThat(new NametagsModule().id()).isEqualTo(ModuleId.of("nametags"));
        assertThat(new NametagsModule().configRoot()).isEqualTo("modules.nametags");
    }

    @Test
    void shipsEnabledByDefault() {
        NametagsModule module = new NametagsModule();

        // With no override the module is on — the bundled config ships a single plain-name format and hides the
        // vanilla name under it, so the default surface is one clean custom nametag per wearer.
        assertThat(module.enabled(new FixedConfig(Map.of()))).isTrue();
        // An explicit disable in modules.conf turns it off.
        assertThat(module.enabled(new FixedConfig(Map.of("modules.nametags.enabled", false))))
                .isFalse();
    }

    @Test
    void publishesNoCommand() {
        // The nametag is always-on when enabled — there is no per-player visibility toggle.
        assertThat(new NametagsModule().commands()).isEmpty();
    }

    @Test
    void persistsNothingAndRegistersNoListenersInThePureModule() {
        NametagsModule module = new NametagsModule();

        assertThat(module.migrations()).isEmpty();
        // Bukkit-facing listeners land with the adapter, not the pure module.
        assertThat(module.listeners()).isEmpty();
    }

    /** A map-backed {@link ConfigStore} for driving the enable gate. */
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
