package com.uxplima.uxmessentials.shared.network;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/** A permission question and its answer arrive intact on the backend they are addressed to. */
class PermissionQueryFrameTest {

    @Test
    void aQuestionAndItsAnswerCrossTheWireUnchanged() {
        UUID query = new UUID(1L, 2L);
        UUID player = new UUID(3L, 4L);
        PermissionQueryFrame ask = new PermissionQueryFrame(
                "survival", query, "lobby", player, "uxmessentials.bypass.punishment", PermissionQueryFrame.ASK);
        PermissionQueryFrame answer = new PermissionQueryFrame(
                "lobby", query, "survival", player, "uxmessentials.bypass.punishment", PermissionQueryFrame.HELD);

        assertThat(NetworkMessageCodec.decode(NetworkMessageCodec.encode(ask))).isEqualTo(ask);
        assertThat(NetworkMessageCodec.decode(NetworkMessageCodec.encode(answer)))
                .isEqualTo(answer);
        assertThat(ask.type().wireTag()).isEqualTo((byte) 34);
    }
}
