package com.uxplima.uxmessentials.playerwarps.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import org.junit.jupiter.api.Test;

/**
 * Pins each player-warps command literal to its base permission node. The four self-service commands are the
 * whole top-level surface; this guard fails if a literal drops out or wires under a node other than its
 * documented one, keeping the kernel surface in lockstep with {@code paper-plugin.yml}. The
 * {@code public}/{@code private} visibility toggles are subcommands of {@code /pwarp} (gated by
 * {@code uxmessentials.pwarp.public}) rather than command literals, so they are not part of this table.
 */
class PlayerWarpSurfaceDriftTest {

    private static CommandSpec spec(String literal) {
        FeatureModule playerwarps = new DefaultModuleRegistry()
                .byId(ModuleId.of("playerwarps"))
                .orElseThrow(() -> new AssertionError("playerwarps module must be registered"));
        return playerwarps.commands().stream()
                .filter(s -> s.literal().equals(literal))
                .findFirst()
                .orElseThrow(() -> new AssertionError("playerwarps surface must expose a /" + literal + " command"));
    }

    @Test
    void pwarpWiresUnderItsUseNode() {
        assertThat(spec("pwarp").permission()).isEqualTo("uxmessentials.pwarp.use");
    }

    @Test
    void setPwarpWiresUnderItsSetNode() {
        assertThat(spec("setpwarp").permission()).isEqualTo("uxmessentials.pwarp.set");
    }

    @Test
    void delPwarpWiresUnderItsDeleteNode() {
        assertThat(spec("delpwarp").permission()).isEqualTo("uxmessentials.pwarp.delete");
    }

    @Test
    void pwarpsWiresUnderItsListNode() {
        assertThat(spec("pwarps").permission()).isEqualTo("uxmessentials.pwarp.list");
    }
}
