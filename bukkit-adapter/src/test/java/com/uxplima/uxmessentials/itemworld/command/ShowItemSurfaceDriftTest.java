package com.uxplima.uxmessentials.itemworld.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import org.junit.jupiter.api.Test;

/**
 * Pins {@code /showitem} into the itemworld context's command surface. {@code /showitem} broadcasts the held
 * item's name to chat so other players can read what someone is
 * holding; this guard fails if the literal drops out of the surface or wires under a node other than
 * {@code uxmessentials.showitem.use}, which would otherwise drift the permissions reference and
 * paper-plugin.yml.
 */
class ShowItemSurfaceDriftTest {

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
    void itemworldSurfaceExposesShowItem() {
        assertThat(itemworldSpec("showitem").literal()).isEqualTo("showitem");
    }

    @Test
    void showItemWiresUnderItsOwnPermission() {
        assertThat(itemworldSpec("showitem").permission()).isEqualTo("uxmessentials.showitem.use");
    }
}
