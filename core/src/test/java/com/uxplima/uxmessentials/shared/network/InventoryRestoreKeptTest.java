package com.uxplima.uxmessentials.shared.network;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/** A kept inventory restore announced on one server arrives intact on another. */
class InventoryRestoreKeptTest {

    @Test
    void aKeptRestoreCrossesTheWireUnchanged() {
        InventoryRestoreKept sent =
                new InventoryRestoreKept("survival-1", UUID.fromString("00000000-0000-0000-0000-0000000004a1"));

        NetworkMessage received = NetworkMessageCodec.decode(NetworkMessageCodec.encode(sent));

        assertThat(received).isEqualTo(sent);
    }
}
