package com.uxplima.uxmessentials.shared.network;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * The cross-server teleport frames arrive intact on another backend, and a backend from before they existed refuses
 * them as an unknown type while still reading their origin, which is what lets it and the proxy skip or relay them.
 */
class TeleportFramesTest {

    private static final UUID REQUEST = UUID.fromString("5d1c8f3e-2b7a-4c49-9a0e-6f3b2d1c0a99");
    private static final UUID STEVE = UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7");
    private static final UUID ALEX = UUID.fromString("ec561538-f3fd-461d-aff5-086b22154bce");
    private static final UUID MOD = UUID.fromString("0f0e0d0c-0b0a-0908-0706-050403020100");

    @Test
    void aTeleportRequestCrossesTheWireUnchanged() {
        TeleportRequestFrame sent = new TeleportRequestFrame(
                "lobby-1", REQUEST, STEVE, "Steve", ALEX, "Alex", "survival-1", "TO_REQUESTER", 3, 60_000L);

        assertThat(roundTrip(sent)).isEqualTo(sent);
    }

    @Test
    void anAnswerCrossesTheWireUnchanged() {
        TeleportAnswerFrame sent =
                new TeleportAnswerFrame("survival-1", REQUEST, "SENT", "lobby-1", ALEX, "Alex", "creative-ä");

        assertThat(roundTrip(sent)).isEqualTo(sent);
    }

    @Test
    void aStaffTeleportCrossesTheWireUnchanged() {
        StaffTeleportFrame sent =
                new StaffTeleportFrame("creative-1", REQUEST, MOD, "Mod", STEVE, "Steve", ALEX, "Alex", "survival-1");

        assertThat(roundTrip(sent)).isEqualTo(sent);
    }

    @Test
    void eachTeleportFrameGoesOutUnderTheTagReservedForIt() {
        byte[] request = NetworkMessageCodec.encode(new TeleportRequestFrame(
                "lobby-1", REQUEST, STEVE, "Steve", ALEX, "Alex", "survival-1", "TO_TARGET", 0, 1L));
        byte[] answer = NetworkMessageCodec.encode(
                new TeleportAnswerFrame("survival-1", REQUEST, "DELIVERED", "lobby-1", ALEX, "Alex", ""));
        byte[] staff = NetworkMessageCodec.encode(
                new StaffTeleportFrame("creative-1", REQUEST, MOD, "Mod", STEVE, "Steve", ALEX, "Alex", "survival-1"));

        assertThat(new byte[] {request[1], answer[1], staff[1]}).containsExactly(25, 26, 27);
    }

    @Test
    void aTeleportFrameAnOlderPeerDoesNotKnowIsRefusedButItsOriginStillReads() {
        byte[] frame = NetworkMessageCodec.encode(
                new StaffTeleportFrame("creative-1", REQUEST, MOD, "Mod", STEVE, "Steve", ALEX, "Alex", "survival-1"));
        // What an older peer sees: a tag it has no type for.
        frame[1] = 120;

        assertThatThrownBy(() -> NetworkMessageCodec.decode(frame)).isInstanceOf(UnknownMessageTypeException.class);
        assertThat(NetworkMessageCodec.readOrigin(frame)).isEqualTo("creative-1");
    }

    private static NetworkMessage roundTrip(NetworkMessage message) {
        return NetworkMessageCodec.decode(NetworkMessageCodec.encode(message));
    }
}
