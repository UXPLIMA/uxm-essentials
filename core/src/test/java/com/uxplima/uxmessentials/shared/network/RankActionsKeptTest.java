package com.uxplima.uxmessentials.shared.network;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/** Kept rank actions announced on one server arrive intact on another. */
class RankActionsKeptTest {

    @Test
    void keptRankActionsCrossTheWireUnchanged() {
        RankActionsKept sent =
                new RankActionsKept("survival-1", UUID.fromString("00000000-0000-0000-0000-0000000004a1"));

        NetworkMessage received = NetworkMessageCodec.decode(NetworkMessageCodec.encode(sent));

        assertThat(received).isEqualTo(sent);
    }
}
