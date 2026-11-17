package com.uxplima.uxmessentials.shared.network;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/** Queued vote rewards announced on one server arrive intact on another. */
class VoteRewardsQueuedTest {

    @Test
    void queuedVoteRewardsCrossTheWireUnchanged() {
        VoteRewardsQueued sent = new VoteRewardsQueued(
                "lobby", UUID.fromString("00000000-0000-0000-0000-0000000004a1"), "PlanetMinecraft");

        NetworkMessage received = NetworkMessageCodec.decode(NetworkMessageCodec.encode(sent));

        assertThat(received).isEqualTo(sent);
    }
}
