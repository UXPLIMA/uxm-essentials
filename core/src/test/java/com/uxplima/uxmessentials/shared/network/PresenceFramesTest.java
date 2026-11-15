package com.uxplima.uxmessentials.shared.network;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Presence frames announced on one backend arrive intact on another, a large roster crosses in parts small enough to
 * relay, and a frame of an unknown type is refused but still routable.
 */
class PresenceFramesTest {

    private static final UUID STEVE = UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7");
    private static final UUID ALEX = UUID.fromString("ec561538-f3fd-461d-aff5-086b22154bce");

    @Test
    void aJoinCrossesTheWireUnchanged() {
        PresenceChanged sent = new PresenceChanged("lobby-1", 1_700_000_000_000L, 42L, STEVE, "Steve", true);

        assertThat(roundTrip(sent)).isEqualTo(sent);
    }

    @Test
    void aRosterPartCrossesTheWireUnchanged() {
        PresenceSnapshot sent = new PresenceSnapshot(
                "survival-1",
                1_700_000_000_000L,
                7L,
                true,
                1,
                3,
                List.of(new PresenceSnapshot.Member(STEVE, "Steve"), new PresenceSnapshot.Member(ALEX, "Alex")));

        assertThat(roundTrip(sent)).isEqualTo(sent);
    }

    @Test
    void aRosterOfTwoThousandPlayersCrossesInPartsUnderTheRelayLimit() {
        List<PresenceSnapshot.Member> players = new ArrayList<>();
        for (int i = 0; i < 2000; i++) {
            // Sixteen characters, the longest name an account can have, with a few that cost more than a byte each.
            players.add(new PresenceSnapshot.Member(
                    UUID.randomUUID(), String.format(Locale.ROOT, "Spieler_\u00e4%07d", i)));
        }

        List<PresenceSnapshot> parts = PresenceSnapshot.split(
                "a-backend-with-a-long-name", 1L, 9L, false, players, PresenceSnapshot.PART_BYTES);

        List<PresenceSnapshot.Member> received = new ArrayList<>();
        for (PresenceSnapshot part : parts) {
            byte[] frame = NetworkMessageCodec.encode(part);
            assertThat(frame.length).isLessThanOrEqualTo(BusChannel.MAX_FRAME_BYTES);
            PresenceSnapshot decoded = (PresenceSnapshot) NetworkMessageCodec.decode(frame);
            assertThat(decoded.parts()).isEqualTo(parts.size());
            received.addAll(decoded.players());
        }
        assertThat(parts).hasSizeGreaterThan(1);
        assertThat(received).containsExactlyElementsOf(players);
    }

    @Test
    void anEmptyRosterIsOneEmptyPart() {
        List<PresenceSnapshot> parts =
                PresenceSnapshot.split("lobby-1", 1L, 2L, false, List.of(), PresenceSnapshot.PART_BYTES);

        assertThat(parts).containsExactly(new PresenceSnapshot("lobby-1", 1L, 2L, false, 0, 1, List.of()));
    }

    @Test
    void aFrameWithATagThisVersionDoesNotKnowIsRefusedButItsOriginStillReads() {
        byte[] frame = NetworkMessageCodec.encode(new PresenceChanged("lobby-1", 1L, 1L, STEVE, "Steve", true));
        frame[1] = 127;

        assertThatThrownBy(() -> NetworkMessageCodec.decode(frame)).isInstanceOf(UnknownMessageTypeException.class);
        assertThat(NetworkMessageCodec.readOrigin(frame)).isEqualTo("lobby-1");
    }

    private static NetworkMessage roundTrip(NetworkMessage sent) {
        return NetworkMessageCodec.decode(NetworkMessageCodec.encode(sent));
    }
}
