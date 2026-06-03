package com.uxplima.uxmessentials.teleport.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import org.junit.jupiter.api.Test;

/**
 * Pins {@code /down} into the teleport context's command surface alongside its inverse {@code /up}.
 * EssentialsX ships the pair together; this guard fails if the vertical family ever loses {@code /down}
 * or wires it under a permission node other than the shared {@code uxmessentials.tp.vertical} the rest of
 * the vertical verbs already use, which would otherwise drift the permissions reference and paper-plugin.yml.
 */
class VerticalSurfaceDriftTest {

    private static CommandSpec downSpec() {
        FeatureModule teleport = new DefaultModuleRegistry()
                .byId(ModuleId.of("teleport"))
                .orElseThrow(() -> new AssertionError("teleport module must be registered"));
        return teleport.commands().stream()
                .filter(spec -> spec.literal().equals("down"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("teleport surface must expose a /down command"));
    }

    @Test
    void teleportSurfaceExposesDown() {
        assertThat(downSpec().literal()).isEqualTo("down");
    }

    @Test
    void downReusesTheSharedVerticalPermission() {
        assertThat(downSpec().permission()).isEqualTo("uxmessentials.tp.vertical");
    }
}
