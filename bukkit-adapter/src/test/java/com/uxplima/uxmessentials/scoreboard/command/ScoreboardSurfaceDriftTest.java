package com.uxplima.uxmessentials.scoreboard.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import org.junit.jupiter.api.Test;

/**
 * Pins {@code /scoreboard} into the scoreboard context's command surface and its gating node. The single per-player
 * toggle command is the whole surface; this guard fails if the literal drops out or wires under a node other than
 * {@code uxmessentials.scoreboard.use}, keeping the kernel surface in lockstep with {@code paper-plugin.yml}.
 */
class ScoreboardSurfaceDriftTest {

    private static CommandSpec scoreboardSpec(String literal) {
        FeatureModule scoreboard = new DefaultModuleRegistry()
                .byId(ModuleId.of("scoreboard"))
                .orElseThrow(() -> new AssertionError("scoreboard module must be registered"));
        return scoreboard.commands().stream()
                .filter(spec -> spec.literal().equals(literal))
                .findFirst()
                .orElseThrow(() -> new AssertionError("scoreboard surface must expose a /" + literal + " command"));
    }

    @Test
    void scoreboardSurfaceExposesScoreboard() {
        assertThat(scoreboardSpec("scoreboard").literal()).isEqualTo("scoreboard");
    }

    @Test
    void scoreboardWiresUnderItsOwnNode() {
        assertThat(scoreboardSpec("scoreboard").permission()).isEqualTo("uxmessentials.scoreboard.use");
    }
}
