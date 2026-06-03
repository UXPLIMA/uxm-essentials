package com.uxplima.uxmessentials.vote.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * The vote-party counter arithmetic: an increment advances the count by one against the same threshold,
 * the reached check fires exactly at (and past) the threshold, and a reset returns the count to zero.
 * The record validates its inputs at construction.
 */
class VotePartyCounterTest {

    @Test
    void incrementAdvancesTheCountByOne() {
        VotePartyCounter counter = new VotePartyCounter(3, 25);

        assertThat(counter.incremented().count()).isEqualTo(4);
        assertThat(counter.incremented().threshold()).isEqualTo(25);
    }

    @Test
    void isReachedIsFalseBelowTheThreshold() {
        assertThat(new VotePartyCounter(24, 25).isReached()).isFalse();
    }

    @Test
    void isReachedIsTrueAtTheThreshold() {
        assertThat(new VotePartyCounter(25, 25).isReached()).isTrue();
    }

    @Test
    void isReachedIsTruePastTheThreshold() {
        assertThat(new VotePartyCounter(26, 25).isReached()).isTrue();
    }

    @Test
    void resetReturnsTheCountToZero() {
        VotePartyCounter reset = new VotePartyCounter(25, 25).reset();

        assertThat(reset.count()).isZero();
        assertThat(reset.threshold()).isEqualTo(25);
    }

    @Test
    void aNegativeCountIsRejected() {
        assertThatThrownBy(() -> new VotePartyCounter(-1, 25)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aThresholdBelowOneIsRejected() {
        assertThatThrownBy(() -> new VotePartyCounter(0, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
