package com.uxplima.uxmessentials.playerstate.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The pure no-fly-world rule: a listed world is no-fly, the match ignores case, a blank entry never matches
 * every world, and an empty list reports empty so the adapter skips the check.
 */
class NoFlyWorldPolicyTest {

    @Test
    void aListedWorldIsNoFly() {
        NoFlyWorldPolicy policy = new NoFlyWorldPolicy(List.of("pvp", "arena"));

        assertThat(policy.isNoFly("pvp")).isTrue();
        assertThat(policy.isNoFly("arena")).isTrue();
        assertThat(policy.isNoFly("world")).isFalse();
    }

    @Test
    void matchIgnoresCase() {
        NoFlyWorldPolicy policy = new NoFlyWorldPolicy(List.of("PvP"));

        assertThat(policy.isNoFly("pvp")).isTrue();
        assertThat(policy.isNoFly("PVP")).isTrue();
    }

    @Test
    void anEmptyListIsEmptyAndNoWorldIsNoFly() {
        NoFlyWorldPolicy policy = new NoFlyWorldPolicy(List.of());

        assertThat(policy.isEmpty()).isTrue();
        assertThat(policy.isNoFly("pvp")).isFalse();
    }

    @Test
    void blankEntriesAreDroppedSoTheyNeverMatchEverything() {
        NoFlyWorldPolicy policy = new NoFlyWorldPolicy(List.of("", "  ", "arena"));

        assertThat(policy.isEmpty()).isFalse();
        assertThat(policy.isNoFly("arena")).isTrue();
        assertThat(policy.isNoFly("")).isFalse();
        assertThat(policy.isNoFly("anything")).isFalse();
    }
}
