package com.uxplima.uxmessentials.economy.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import org.junit.jupiter.api.Test;

/**
 * Pins {@code /sellall} into the economy context's command surface. {@code /sell} sells only the held stack;
 * EssentialsX's {@code /sell all} sells every sellable item in the inventory, so {@code /sellall} reuses the
 * existing {@code SellItem} use case and the {@code uxmessentials.economy.sell} node (same capability). This
 * guard fails if the literal drops out of the surface or wires under a different node.
 */
class SellAllSurfaceDriftTest {

    private static CommandSpec economySpec(String literal) {
        FeatureModule economy = new DefaultModuleRegistry()
                .byId(ModuleId.of("economy"))
                .orElseThrow(() -> new AssertionError("economy module must be registered"));
        return economy.commands().stream()
                .filter(spec -> spec.literal().equals(literal))
                .findFirst()
                .orElseThrow(() -> new AssertionError("economy surface must expose a /" + literal + " command"));
    }

    @Test
    void economySurfaceExposesSellAll() {
        assertThat(economySpec("sellall").literal()).isEqualTo("sellall");
    }

    @Test
    void sellAllReusesTheSharedSellPermission() {
        assertThat(economySpec("sellall").permission()).isEqualTo("uxmessentials.economy.sell");
    }
}
