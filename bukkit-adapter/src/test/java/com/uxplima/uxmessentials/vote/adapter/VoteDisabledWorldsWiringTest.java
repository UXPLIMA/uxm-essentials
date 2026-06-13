package com.uxplima.uxmessentials.vote.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.RewardContext;
import com.uxplima.uxmessentials.vote.application.RewardEngine;
import com.uxplima.uxmessentials.vote.domain.VoteTally;
import com.uxplima.uxmessentials.vote.domain.reward.RewardCatalog;
import com.uxplima.uxmessentials.vote.domain.reward.RewardSpec;
import org.junit.jupiter.api.Test;

/**
 * Verifies the {@code reward.disabled-worlds} config list reaches the {@link RewardEngine} through the same
 * {@code Set.copyOf(config.getStringList(...))} parse {@code VoteWiring} performs: a vote cast in a disabled
 * world earns nothing while a vote in any other world earns the configured per-vote grant. Standing up the
 * full {@code wire(...)} would require a Persistence + ModuleContext fixture, so this exercises the parse and
 * the engine wiring directly with a fixed in-memory {@link ConfigStore}.
 */
class VoteDisabledWorldsWiringTest {

    private static final String CONFIG_PATH = "reward.disabled-worlds";

    @Test
    void aVoteInADisabledWorldEarnsNothing() {
        RewardEngine engine = engineFrom(config(List.of("creative")));

        assertThat(engine.resolve(voteIn("creative"))).isEmpty();
    }

    @Test
    void aVoteInAnAllowedWorldStillEarnsItsGrant() {
        RewardEngine engine = engineFrom(config(List.of("creative")));

        assertThat(engine.resolve(voteIn("survival"))).hasSize(1);
    }

    @Test
    void anEmptyDisabledWorldsListDisablesNothing() {
        RewardEngine engine = engineFrom(config(List.of()));

        assertThat(engine.resolve(voteIn("creative"))).hasSize(1);
    }

    /** Mirror of the {@code VoteWiring} parse: the config list becomes the engine's disabled-worlds set. */
    private static RewardEngine engineFrom(ConfigStore config) {
        Set<String> disabledWorlds = Set.copyOf(config.getStringList(CONFIG_PATH, List.of()));
        RewardSpec perVote = new RewardSpec(
                100, Optional.empty(), List.of("eco give {player} 1"), List.of(), List.of(), List.of(), Set.of());
        RewardCatalog catalog = new RewardCatalog(List.of(perVote), Map.of(), List.of(), List.of(), List.of());
        return new RewardEngine(catalog, disabledWorlds);
    }

    private static RewardContext voteIn(String world) {
        return new RewardContext(
                new PlayerRef(java.util.UUID.randomUUID(), "Alice"),
                true,
                world,
                "PMC",
                new VoteTally(1L, 1L, 1L, 1L, 0L, 0L, 0L, 1L, 1L, 0L),
                false,
                node -> true,
                chance -> true);
    }

    private static ConfigStore config(List<String> disabledWorlds) {
        return new ConfigStore() {
            @Override
            public boolean getBoolean(String path, boolean fallback) {
                return fallback;
            }

            @Override
            public String getString(String path, String fallback) {
                return fallback;
            }

            @Override
            public int getInt(String path, int fallback) {
                return fallback;
            }

            @Override
            public List<String> getStringList(String path, List<String> fallback) {
                return CONFIG_PATH.equals(path) ? List.copyOf(disabledWorlds) : List.copyOf(fallback);
            }
        };
    }
}
