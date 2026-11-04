package com.uxplima.uxmessentials.shared.network;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** A guard edit announced on one server arrives intact on another. */
class GuardChangedTest {

    @Test
    void aGuardChangeCrossesTheWireUnchanged() {
        GuardChanged sent = new GuardChanged("lobby-1", "set:hub", "world_nether", "spawn-gate");

        NetworkMessage received = NetworkMessageCodec.decode(NetworkMessageCodec.encode(sent));

        assertThat(received).isEqualTo(sent);
    }
}
