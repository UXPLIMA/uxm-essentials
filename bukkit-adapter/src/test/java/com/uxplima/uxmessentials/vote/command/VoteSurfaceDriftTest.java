package com.uxplima.uxmessentials.vote.command;

import static org.assertj.core.api.Assertions.assertThat;

import com.uxplima.uxmessentials.bootstrap.di.DefaultModuleRegistry;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import org.junit.jupiter.api.Test;

/**
 * Pins {@code /vote} and {@code /voteparty} into the vote context's command surface and their gating nodes.
 * The two top-level commands are the whole surface ({@code /vote testreward} is a subcommand of {@code /vote},
 * not a separate literal); this guard fails if a literal drops out or wires under a node other than its own,
 * keeping the kernel surface in lockstep with {@code paper-plugin.yml}.
 */
class VoteSurfaceDriftTest {

    private static CommandSpec voteSpec(String literal) {
        FeatureModule vote = new DefaultModuleRegistry()
                .byId(ModuleId.of("vote"))
                .orElseThrow(() -> new AssertionError("vote module must be registered"));
        return vote.commands().stream()
                .filter(spec -> spec.literal().equals(literal))
                .findFirst()
                .orElseThrow(() -> new AssertionError("vote surface must expose a /" + literal + " command"));
    }

    @Test
    void voteSurfaceExposesVote() {
        assertThat(voteSpec("vote").literal()).isEqualTo("vote");
    }

    @Test
    void voteWiresUnderItsOwnNode() {
        assertThat(voteSpec("vote").permission()).isEqualTo("uxmessentials.vote.use");
    }

    @Test
    void voteSurfaceExposesVoteParty() {
        assertThat(voteSpec("voteparty").literal()).isEqualTo("voteparty");
    }

    @Test
    void votePartyWiresUnderItsOwnNode() {
        assertThat(voteSpec("voteparty").permission()).isEqualTo("uxmessentials.voteparty.use");
    }
}
