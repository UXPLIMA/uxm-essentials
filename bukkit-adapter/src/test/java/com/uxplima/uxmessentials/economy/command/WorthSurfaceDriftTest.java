package com.uxplima.uxmessentials.economy.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import org.junit.jupiter.api.Test;

/**
 * Pins {@code /worth} into the economy context's command surface. {@code /worth} reports
 * the configured sell value of the held item before a player commits to {@code /sell}; this guard fails if the
 * literal drops out of the surface or wires under a node other than {@code uxmessentials.economy.worth}, which
 * would otherwise drift the permissions reference and paper-plugin.yml.
 */
class WorthSurfaceDriftTest {

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
    void economySurfaceExposesWorth() {
        assertThat(economySpec("worth").literal()).isEqualTo("worth");
    }

    @Test
    void worthWiresUnderItsOwnPermission() {
        assertThat(economySpec("worth").permission()).isEqualTo("uxmessentials.economy.worth");
    }
}
