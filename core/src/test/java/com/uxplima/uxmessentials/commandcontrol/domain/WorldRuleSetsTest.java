package com.uxplima.uxmessentials.commandcontrol.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * The world-scoped rule resolution behind per-world command groups: a world with its own override is governed by that
 * override, every other world (and a null/unknown world) falls back to the base rule set, and the world name is matched
 * case-insensitively. The worked case: {@code /fly} is allowed in a creative world but blocked in the survival world.
 */
class WorldRuleSetsTest {

    private static final String BYPASS = "uxmessentials.commandcontrol.bypass";

    private static PlayerFacts noGroup() {
        return new PlayerFacts() {
            @Override
            public Optional<String> group() {
                return Optional.empty();
            }

            @Override
            public boolean hasPermission(String node) {
                return false;
            }
        };
    }

    @Test
    void aCommandAllowedInOneWorldIsBlockedInAnother() {
        // Base: a blacklist that denies /fly everywhere. Override for "creative": an empty blacklist that denies
        // nothing.
        RuleSet base = RuleSet.of(RuleMode.BLACKLIST, List.of("fly"), Map.of(), BYPASS);
        RuleSet creative = RuleSet.of(RuleMode.BLACKLIST, List.of(), Map.of(), BYPASS);
        WorldRuleSets worlds = WorldRuleSets.of(base, Map.of("creative", creative));

        // In the creative world /fly is allowed; in the survival world (base) it is denied.
        assertThat(worlds.forWorld("creative").decide("fly", noGroup())).isEqualTo(RuleSet.Decision.ALLOW);
        assertThat(worlds.forWorld("survival").decide("fly", noGroup())).isEqualTo(RuleSet.Decision.DENY);
    }

    @Test
    void theWorldNameIsMatchedCaseInsensitively() {
        RuleSet base = RuleSet.of(RuleMode.BLACKLIST, List.of("fly"), Map.of(), BYPASS);
        RuleSet creative = RuleSet.of(RuleMode.BLACKLIST, List.of(), Map.of(), BYPASS);
        WorldRuleSets worlds = WorldRuleSets.of(base, Map.of("Creative", creative));

        assertThat(worlds.forWorld("CREATIVE").decide("fly", noGroup())).isEqualTo(RuleSet.Decision.ALLOW);
    }

    @Test
    void aNullOrUnknownWorldFallsBackToTheBase() {
        RuleSet base = RuleSet.of(RuleMode.BLACKLIST, List.of("fly"), Map.of(), BYPASS);
        WorldRuleSets worlds =
                WorldRuleSets.of(base, Map.of("creative", RuleSet.of(RuleMode.BLACKLIST, List.of(), Map.of(), BYPASS)));

        assertThat(worlds.forWorld(null).decide("fly", noGroup())).isEqualTo(RuleSet.Decision.DENY);
        assertThat(worlds.forWorld("nether").decide("fly", noGroup())).isEqualTo(RuleSet.Decision.DENY);
    }

    @Test
    void hasWorldOverridesReflectsWhetherAnyWorldOverridesTheBase() {
        RuleSet base = RuleSet.of(RuleMode.BLACKLIST, List.of(), Map.of(), BYPASS);
        assertThat(WorldRuleSets.ofBase(base).hasWorldOverrides()).isFalse();
        assertThat(WorldRuleSets.of(base, Map.of("creative", base)).hasWorldOverrides())
                .isTrue();
    }
}
