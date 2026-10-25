package com.uxplima.uxmessentials.bossbar.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.stream.Collectors;

import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.junit.jupiter.api.Test;

class BossBarModuleTest {

    @Test
    void identityMatchesItsContextPackage() {
        assertThat(new BossBarModule().id()).isEqualTo(ModuleId.of("bossbar"));
        assertThat(new BossBarModule().configRoot()).isEqualTo("modules.bossbar");
    }

    @Test
    void shipsDisabledByDefault() {
        BossBarModule module = new BossBarModule();

        // With no override the module is off: the boss bar is a surface a dedicated HUD plugin also draws.
        assertThat(module.enabled(new FixedConfig(Map.of()))).isFalse();
        assertThat(module.enabled(new FixedConfig(Map.of("modules.bossbar.enabled", true))))
                .isTrue();
    }

    @Test
    void publishesOnlyTheBossBarsCommand() {
        BossBarModule module = new BossBarModule();

        assertThat(module.commands().stream().map(CommandSpec::literal).collect(Collectors.toSet()))
                .containsExactly("bossbars");
        assertThat(module.commands().get(0).permission()).isEqualTo("uxmessentials.bossbar.use");
    }

    @Test
    void persistsNothingAndRegistersNoListenersInThePureModule() {
        BossBarModule module = new BossBarModule();

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
