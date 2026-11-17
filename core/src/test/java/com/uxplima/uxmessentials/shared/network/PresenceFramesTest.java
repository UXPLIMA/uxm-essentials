package com.uxplima.uxmessentials.shared.network;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PresenceTraits;
import org.junit.jupiter.api.Test;

/**
 * Presence frames announced on one backend arrive intact on another with each player's traits, a large roster crosses
 * in parts small enough to relay, a frame from a peer that sends no traits reads as hidden at an unknown level, and a
 * frame of an unknown type is refused but still routable.
 */
class PresenceFramesTest {

    private static final UUID STEVE = UUID.fromString("8667ba71-b85a-4004-af54-457a9734eed7");
    private static final UUID ALEX = UUID.fromString("ec561538-f3fd-461d-aff5-086b22154bce");
    private static final PresenceTraits VANISHED_STAFF = new PresenceTraits(3, true);
    /** The see level of a viewer who sees every vanished player: an operator, or a wildcard see node. */
    private static final int SEES_ALL = 100_000;

    @Test
    void aJoinCrossesTheWireUnchanged() {
        PresenceChanged sent =
                new PresenceChanged("lobby-1", 1_700_000_000_000L, 42L, STEVE, "Steve", true, VANISHED_STAFF);

        assertThat(roundTrip(sent)).isEqualTo(sent);
    }

    @Test
    void aJoinFromAPeerThatSendsNoTraitsReadsAsHiddenAtAnUnknownLevel() {
        PresenceChanged sent = new PresenceChanged("lobby-1", 1L, 42L, STEVE, "Steve", true, PresenceTraits.PLAIN);
        byte[] frame = NetworkMessageCodec.encode(sent);
        byte[] older = Arrays.copyOf(frame, frame.length - NetworkMessageCodec.TRAITS_BYTES);

        PresenceChanged read = (PresenceChanged) NetworkMessageCodec.decode(older);

        assertThat(read.player()).isEqualTo(STEVE);
        assertThat(read.traits()).isEqualTo(PresenceTraits.UNKNOWN);
        assertThat(read.traits().visibleTo(SEES_ALL)).isTrue();
        assertThat(read.traits().visibleTo(50)).isFalse();
    }

    @Test
    void aRosterFromAPeerThatSendsNoTraitsReadsEveryPlayerAsHiddenAtAnUnknownLevel() {
        PresenceSnapshot sent = new PresenceSnapshot(
                "survival-1",
                1L,
                7L,
                false,
                0,
                1,
                List.of(
                        new PresenceSnapshot.Member(STEVE, "Steve", PresenceTraits.PLAIN),
                        new PresenceSnapshot.Member(ALEX, "Alex", VANISHED_STAFF)));
        byte[] frame = NetworkMessageCodec.encode(sent);
        byte[] older = Arrays.copyOf(frame, frame.length - 2 * NetworkMessageCodec.TRAITS_BYTES);

        PresenceSnapshot read = (PresenceSnapshot) NetworkMessageCodec.decode(older);

        assertThat(read.players())
                .extracting(PresenceSnapshot.Member::traits)
                .containsExactly(PresenceTraits.UNKNOWN, PresenceTraits.UNKNOWN);
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
                List.of(
                        new PresenceSnapshot.Member(STEVE, "Steve", PresenceTraits.PLAIN),
                        new PresenceSnapshot.Member(ALEX, "Alex", VANISHED_STAFF)));

        assertThat(roundTrip(sent)).isEqualTo(sent);
    }

    @Test
    void aRosterOfTwoThousandPlayersCrossesInPartsUnderTheRelayLimit() {
        List<PresenceSnapshot.Member> players = new ArrayList<>();
        for (int i = 0; i < 2000; i++) {
            // Sixteen characters, the longest name an account can have, with a few that cost more than a byte each.
            players.add(new PresenceSnapshot.Member(
                    UUID.randomUUID(), String.format(Locale.ROOT, "Spieler_\u00e4%07d", i), VANISHED_STAFF));
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
        byte[] frame = NetworkMessageCodec.encode(
                new PresenceChanged("lobby-1", 1L, 1L, STEVE, "Steve", true, PresenceTraits.PLAIN));
        frame[1] = 127;

        assertThatThrownBy(() -> NetworkMessageCodec.decode(frame)).isInstanceOf(UnknownMessageTypeException.class);
        assertThat(NetworkMessageCodec.readOrigin(frame)).isEqualTo("lobby-1");
    }

    private static NetworkMessage roundTrip(NetworkMessage sent) {
        return NetworkMessageCodec.decode(NetworkMessageCodec.encode(sent));
    }
}
