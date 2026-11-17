package com.uxplima.uxmessentials.shared.network;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** Queued vote rewards announced on one server arrive intact on another. */
class VoteRewardsQueuedTest {

    @Test
    void queuedVoteRewardsCrossTheWireUnchanged() {
        VoteRewardsQueued sent = new VoteRewardsQueued(
                "lobby",
                UUID.fromString("00000000-0000-0000-0000-00000000f07e"),
                UUID.fromString("00000000-0000-0000-0000-0000000004a1"),
                "PlanetMinecraft");

        NetworkMessage received = NetworkMessageCodec.decode(NetworkMessageCodec.encode(sent));

        assertThat(received).isEqualTo(sent);
    }

    @Test
    void theSendTimeCrossesTheWire() {
        VoteRewardsQueued sent = new VoteRewardsQueued(
                "lobby",
                new UUID(1L, 2L),
                new UUID(3L, 4L),
                "PlanetMinecraft",
                Instant.parse("2026-11-17T12:00:00Z"),
                true);

        assertThat(NetworkMessageCodec.decode(NetworkMessageCodec.encode(sent))).isEqualTo(sent);
    }

    @Test
    void aVoteFromAServerThatSendsNoTimeIsReadAndNeverTakenForALateCopy() {
        byte[] current = NetworkMessageCodec.encode(new VoteRewardsQueued(
                "lobby",
                new UUID(1L, 2L),
                new UUID(3L, 4L),
                "PlanetMinecraft",
                Instant.parse("2026-01-01T00:00:00Z"),
                true));
        // An older server wrote no time and no carrier flag: a long and a boolean fewer.
        byte[] older = Arrays.copyOf(current, current.length - Long.BYTES - 1);

        VoteRewardsQueued read = (VoteRewardsQueued) NetworkMessageCodec.decode(older);

        assertThat(read.service()).isEqualTo("PlanetMinecraft");
        assertThat(read.sentBefore(Instant.parse("2026-11-17T12:00:00Z"))).isFalse();
    }
}
