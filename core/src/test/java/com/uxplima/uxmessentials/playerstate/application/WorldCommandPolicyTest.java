package com.uxplima.uxmessentials.playerstate.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * The pure per-world command-block rule: a label listed under a world is blocked only in that world, a label
 * listed under {@code "*"} is blocked in every world, the match ignores case and a leading slash and the
 * {@code uxmessentials:} namespace prefix on the queried label, and an empty map blocks nothing (so the
 * adapter can short-circuit before reading the world).
 */
class WorldCommandPolicyTest {

    @Test
    void blocksAListedCommandOnlyInItsWorld() {
        WorldCommandPolicy policy = new WorldCommandPolicy(Map.of("creative", List.of("tpa", "warp")));

        assertThat(policy.isBlocked("creative", "tpa")).isTrue();
        assertThat(policy.isBlocked("creative", "warp")).isTrue();
        assertThat(policy.isBlocked("creative", "home")).isFalse();
        assertThat(policy.isBlocked("world", "tpa")).isFalse();
    }

    @Test
    void wildcardWorldBlocksInEveryWorld() {
        WorldCommandPolicy policy = new WorldCommandPolicy(Map.of("*", List.of("nuke")));

        assertThat(policy.isBlocked("creative", "nuke")).isTrue();
        assertThat(policy.isBlocked("survival", "nuke")).isTrue();
        assertThat(policy.isBlocked("anywhere", "tpa")).isFalse();
    }

    @Test
    void matchIgnoresCaseSlashAndNamespacePrefix() {
        WorldCommandPolicy policy = new WorldCommandPolicy(Map.of("Creative", List.of("/Tpa")));

        assertThat(policy.isBlocked("creative", "TPA")).isTrue();
        assertThat(policy.isBlocked("CREATIVE", "/tpa")).isTrue();
        assertThat(policy.isBlocked("creative", "uxmessentials:tpa")).isTrue();
    }

    @Test
    void anEmptyMapBlocksNothingAndReportsEmpty() {
        WorldCommandPolicy policy = new WorldCommandPolicy(Map.of());

        assertThat(policy.isEmpty()).isTrue();
        assertThat(policy.isBlocked("creative", "tpa")).isFalse();
    }

    @Test
    void worldsWithOnlyBlankLabelsAreDroppedAndAWildcardCombinesWithPerWorld() {
        WorldCommandPolicy policy = new WorldCommandPolicy(
                Map.of("creative", List.of("warp"), "empty", List.of("", "  ", "/"), "*", List.of("nuke")));

        assertThat(policy.isEmpty()).isFalse();
        assertThat(policy.isBlocked("empty", "warp")).isFalse();
        assertThat(policy.isBlocked("creative", "warp")).isTrue();
        assertThat(policy.isBlocked("creative", "nuke")).isTrue();
        assertThat(policy.isBlocked("empty", "nuke")).isTrue();
    }
}
