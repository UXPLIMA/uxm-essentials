package com.uxplima.uxmessentials.homes.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import org.junit.jupiter.api.Test;

/**
 * Pins {@code /setmainhome} into the homes context's command surface. Plain {@code /home} (no name) now
 * teleports to the home the player chose with this command, falling back to the first-created one when they
 * never chose. This guard fails if the literal drops out of the surface or wires under a node other than the
 * dedicated {@code uxmessentials.home.setmain}.
 */
class SetMainHomeSurfaceDriftTest {

    private static CommandSpec homesSpec(String literal) {
        FeatureModule homes = new DefaultModuleRegistry()
                .byId(ModuleId.of("homes"))
                .orElseThrow(() -> new AssertionError("homes module must be registered"));
        return homes.commands().stream()
                .filter(spec -> spec.literal().equals(literal))
                .findFirst()
                .orElseThrow(() -> new AssertionError("homes surface must expose a /" + literal + " command"));
    }

    @Test
    void homesSurfaceExposesSetMainHome() {
        assertThat(homesSpec("setmainhome").literal()).isEqualTo("setmainhome");
    }

    @Test
    void setMainHomeWiresUnderTheDedicatedNode() {
        assertThat(homesSpec("setmainhome").permission()).isEqualTo("uxmessentials.home.setmain");
    }
}
