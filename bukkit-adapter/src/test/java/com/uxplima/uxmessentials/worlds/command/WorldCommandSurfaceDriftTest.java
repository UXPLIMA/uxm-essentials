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
    void exposesTheWorldRootAndConfirmCompanion() {
        assertThat(worldsSpec("worlds").permission()).isEqualTo("uxmessentials.world.use");
        assertThat(worldsSpec("worldsconfirm").permission()).isEqualTo("uxmessentials.world.delete");
    }
}
