package com.uxplima.uxmessentials.worlds.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import org.junit.jupiter.api.Test;

class WorldCommandSurfaceDriftTest {

    private static CommandSpec worldsSpec(String literal) {
        FeatureModule worlds = new DefaultModuleRegistry()
                .byId(ModuleId.of("worlds"))
                .orElseThrow(() -> new AssertionError("worlds module must be registered"));
        return worlds.commands().stream()
                .filter(s -> s.literal().equals(literal))
                .findFirst()
                .orElseThrow(() -> new AssertionError("worlds surface must expose /" + literal));
    }

    @Test
    void exposesTheWorldRoot() {
        assertThat(worldsSpec("worlds").permission()).isEqualTo("uxmessentials.world.use");
    }

    @Test
    void worldsExposesNoExtraTopLevelLiteral() {
        // Deletion confirmation is the `/worlds confirm <name>` subcommand, not a separate top-level command,
        // so the worlds surface is a single literal.
        FeatureModule worlds =
                new DefaultModuleRegistry().byId(ModuleId.of("worlds")).orElseThrow();
        assertThat(worlds.commands().stream().map(CommandSpec::literal).toList())
                .containsExactlyInAnyOrder("worlds");
    }
}
