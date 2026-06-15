package com.uxplima.uxmessentials.npc.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import org.junit.jupiter.api.Test;

/**
 * Pins {@code /npc} into the npc context's command surface and its gating node. The single operator command
 * (with its create / delete / list / movehere / skin / command subcommands) is the whole surface; this guard
 * fails if the literal drops out or wires under a node other than {@code uxmessentials.npc.admin}, keeping the
 * kernel surface in lockstep with {@code paper-plugin.yml}.
 */
class NpcSurfaceDriftTest {

    private static CommandSpec npcSpec(String literal) {
        FeatureModule npc = new DefaultModuleRegistry()
                .byId(ModuleId.of("npc"))
                .orElseThrow(() -> new AssertionError("npc module must be registered"));
        return npc.commands().stream()
                .filter(spec -> spec.literal().equals(literal))
                .findFirst()
                .orElseThrow(() -> new AssertionError("npc surface must expose a /" + literal + " command"));
    }

    @Test
    void npcSurfaceExposesNpc() {
        assertThat(npcSpec("npc").literal()).isEqualTo("npc");
    }

    @Test
    void npcWiresUnderItsOwnNode() {
        assertThat(npcSpec("npc").permission()).isEqualTo("uxmessentials.npc.admin");
    }
}
