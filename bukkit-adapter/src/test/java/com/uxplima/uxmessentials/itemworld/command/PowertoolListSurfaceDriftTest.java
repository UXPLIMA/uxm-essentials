package com.uxplima.uxmessentials.itemworld.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import org.junit.jupiter.api.Test;

/**
 * Pins {@code /powertoollist} into the itemworld context's command surface. EssentialsX and zEssentials both
 * ship {@code /powertoollist} to print the commands bound to the held item by {@code /powertool}; it is a pure
 * read, so it reuses the existing {@code uxmessentials.powertool.use} node. This guard fails if the literal
 * drops out of the surface or wires under a different node.
 */
class PowertoolListSurfaceDriftTest {

    private static CommandSpec itemworldSpec(String literal) {
        FeatureModule itemworld = new DefaultModuleRegistry()
                .byId(ModuleId.of("itemworld"))
                .orElseThrow(() -> new AssertionError("itemworld module must be registered"));
        return itemworld.commands().stream()
                .filter(spec -> spec.literal().equals(literal))
                .findFirst()
                .orElseThrow(() -> new AssertionError("itemworld surface must expose a /" + literal + " command"));
    }

    @Test
    void itemworldSurfaceExposesPowertoolList() {
        assertThat(itemworldSpec("powertoollist").literal()).isEqualTo("powertoollist");
    }

    @Test
    void powertoolListReusesTheSharedPowertoolPermission() {
        assertThat(itemworldSpec("powertoollist").permission()).isEqualTo("uxmessentials.powertool.use");
    }
}
