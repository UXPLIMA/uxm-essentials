package com.uxplima.uxmessentials.tablist.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.junit.jupiter.api.Test;

class TablistModuleTest {

    @Test
    void identityMatchesItsContextPackage() {
        assertThat(new TablistModule().id()).isEqualTo(ModuleId.of("tablist"));
        assertThat(new TablistModule().configRoot()).isEqualTo("modules.tablist");
    }

    @Test
    void shipsEnabledByDefault() {
        TablistModule module = new TablistModule();

        // With no override the module is on — a fresh install bundles an example header/footer (built-in {tokens}, no
        // PlaceholderAPI required) so a new operator sees a working tab out of the box.
        assertThat(module.enabled(new FixedConfig(Map.of()))).isTrue();
        // An explicit disable in modules.conf turns it off.
        assertThat(module.enabled(new FixedConfig(Map.of("modules.tablist.enabled", false))))
                .isFalse();
    }

    @Test
    void publishesNoCommand() {
        // The tablist is always-on when enabled — there is no per-player visibility toggle.
        assertThat(new TablistModule().commands()).isEmpty();
    }

    @Test
    void persistsNothingAndRegistersNoListenersInThePureModule() {
        TablistModule module = new TablistModule();

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
