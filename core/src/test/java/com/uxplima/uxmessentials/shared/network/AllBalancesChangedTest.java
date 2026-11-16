package com.uxplima.uxmessentials.shared.network;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** A sweep of every balance announced on one server arrives intact on another. */
class AllBalancesChangedTest {

    @Test
    void aSweepOfEveryBalanceCrossesTheWireUnchanged() {
        AllBalancesChanged sent = new AllBalancesChanged("survival", "coins");

        NetworkMessage received = NetworkMessageCodec.decode(NetworkMessageCodec.encode(sent));

        assertThat(received).isEqualTo(sent);
        assertThat(sent.type().wireTag()).isEqualTo((byte) 33);
    }
}
