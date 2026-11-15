package com.uxplima.uxmessentials.shared.network;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/** A rank change announced on one server arrives intact on another. */
class RankChangedTest {

    @Test
    void aRankChangeCrossesTheWireUnchanged() {
        RankChanged sent = new RankChanged("survival-1", UUID.fromString("00000000-0000-0000-0000-000000000a1e"));

        NetworkMessage received = NetworkMessageCodec.decode(NetworkMessageCodec.encode(sent));

        assertThat(received).isEqualTo(sent);
    }
}
