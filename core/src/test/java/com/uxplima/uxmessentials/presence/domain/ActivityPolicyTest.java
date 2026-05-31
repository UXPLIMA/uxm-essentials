package com.uxplima.uxmessentials.presence.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;

import org.junit.jupiter.api.Test;

/**
 * The anti-machine activity filter's rule in one place: an ignored kind does not reset the AFK clock, an
 * un-ignored kind does, a real {@link ActivityKind#MOVE} is never ignored even when listed, and the default
 * policy counts everything so out-of-the-box behaviour is unchanged. The Bukkit event mapping that feeds these
 * kinds is the adapter's; this is the pure decision the adapter consults.
 */
class ActivityPolicyTest {

    @Test
    void theDefaultPolicyCountsEveryKind() {
        ActivityPolicy policy = ActivityPolicy.countingEverything();

        for (ActivityKind kind : ActivityKind.values()) {
            assertThat(policy.countsAsActivity(kind))
                    .as("%s counts by default", kind)
                    .isTrue();
        }
    }

    @Test
    void anIgnoredKindDoesNotCountAsActivity() {
        ActivityPolicy policy = ActivityPolicy.ignoring(EnumSet.of(ActivityKind.FISH));

        assertThat(policy.countsAsActivity(ActivityKind.FISH)).isFalse();
        assertThat(policy.isIgnored(ActivityKind.FISH)).isTrue();
        assertThat(policy.countsAsActivity(ActivityKind.CHAT)).isTrue();
    }

    @Test
    void aRealMoveIsNeverIgnoredEvenWhenListed() {
        ActivityPolicy policy = ActivityPolicy.ignoring(EnumSet.of(ActivityKind.MOVE, ActivityKind.ROTATE));

        assertThat(policy.countsAsActivity(ActivityKind.MOVE)).isTrue();
        assertThat(policy.countsAsActivity(ActivityKind.ROTATE)).isFalse();
    }

    @Test
    void aTokenResolvesToItsKindCaseInsensitively() {
        assertThat(ActivityKind.fromToken("FISH")).contains(ActivityKind.FISH);
        assertThat(ActivityKind.fromToken(" rotate ")).contains(ActivityKind.ROTATE);
    }

    @Test
    void anUnknownTokenResolvesToEmpty() {
        assertThat(ActivityKind.fromToken("teleport")).isEmpty();
    }

    @Test
    void everyKindRoundTripsThroughItsToken() {
        for (ActivityKind kind : ActivityKind.values()) {
            assertThat(ActivityKind.fromToken(kind.token())).contains(kind);
        }
    }
}
