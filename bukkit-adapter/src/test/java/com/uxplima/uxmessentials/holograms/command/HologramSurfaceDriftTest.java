package com.uxplima.uxmessentials.holograms.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import org.junit.jupiter.api.Test;

/**
 * Pins {@code /hologram} into the holograms context's command surface and its gating node. The single
 * operator command (with its create / delete / list / line-edit / move subcommands) is the whole surface;
 * this guard fails if the literal drops out or wires under a node other than
 * {@code uxmessentials.hologram.use}, keeping the kernel surface in lockstep with {@code paper-plugin.yml}.
 */
class HologramSurfaceDriftTest {

    private static CommandSpec hologramSpec(String literal) {
        FeatureModule holograms = new DefaultModuleRegistry()
                .byId(ModuleId.of("holograms"))
                .orElseThrow(() -> new AssertionError("holograms module must be registered"));
        return holograms.commands().stream()
                .filter(spec -> spec.literal().equals(literal))
                .findFirst()
                .orElseThrow(() -> new AssertionError("holograms surface must expose a /" + literal + " command"));
    }

    @Test
    void hologramsSurfaceExposesHologram() {
        assertThat(hologramSpec("hologram").literal()).isEqualTo("hologram");
    }

    @Test
    void hologramWiresUnderItsOwnNode() {
        assertThat(hologramSpec("hologram").permission()).isEqualTo("uxmessentials.hologram.use");
    }
}
