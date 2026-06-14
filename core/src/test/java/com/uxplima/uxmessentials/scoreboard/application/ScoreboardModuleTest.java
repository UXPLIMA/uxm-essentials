package com.uxplima.uxmessentials.scoreboard.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.stream.Collectors;

import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.junit.jupiter.api.Test;

class ScoreboardModuleTest {

    @Test
    void identityMatchesItsContextPackage() {
        assertThat(new ScoreboardModule().id()).isEqualTo(ModuleId.of("scoreboard"));
        assertThat(new ScoreboardModule().configRoot()).isEqualTo("modules.scoreboard");
    }

    @Test
    void shipsEnabledByDefault() {
        ScoreboardModule module = new ScoreboardModule();

        // With no override the module is on — a fresh install bundles an example sidebar (built-in {tokens}, no
        // PlaceholderAPI required) so a new operator sees a working board out of the box.
        assertThat(module.enabled(new FixedConfig(Map.of()))).isTrue();
        // An explicit disable in modules.conf turns it off.
        assertThat(module.enabled(new FixedConfig(Map.of("modules.scoreboard.enabled", false))))
                .isFalse();
    }

    @Test
    void publishesOnlyTheScoreboardToggleCommand() {
        ScoreboardModule module = new ScoreboardModule();

        assertThat(module.commands().stream().map(CommandSpec::literal).collect(Collectors.toSet()))
                .containsExactly("scoreboard");
        assertThat(module.commands().get(0).permission()).isEqualTo("uxmessentials.scoreboard.use");
    }

    @Test
    void persistsNothingAndRegistersNoListenersInThePureModule() {
        ScoreboardModule module = new ScoreboardModule();

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
