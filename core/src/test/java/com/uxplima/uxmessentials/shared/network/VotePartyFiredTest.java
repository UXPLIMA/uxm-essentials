package com.uxplima.uxmessentials.shared.network;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * A vote party tells the other servers whom they may reward and which party it is, and a party from an older server
 * still decodes.
 */
class VotePartyFiredTest {

    private static final UUID PARTY = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-000000000a1e");

    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

    @Test
    void theVotersOfAPartyCrossTheWireUnchanged() {
        VotePartyFired sent = new VotePartyFired(
                "survival-1", PARTY, 25, VotePartyFired.Reach.VOTERS, Set.of(ALEX, new UUID(7L, 9L)));

        NetworkMessage received = NetworkMessageCodec.decode(NetworkMessageCodec.encode(sent));

        assertThat(received).isEqualTo(sent);
    }

    @Test
    void theFireTimeAndCarriersCrossTheWireUnchanged() {
        VotePartyFired sent = new VotePartyFired(
                "survival-1",
                PARTY,
                25,
                VotePartyFired.Reach.EVERYONE,
                Set.of(),
                Instant.parse("2030-01-01T12:00:00.123Z"),
                true);

        NetworkMessage received = NetworkMessageCodec.decode(NetworkMessageCodec.encode(sent));

        assertThat(received).isEqualTo(sent);
    }

    @Test
    void aPartyFromAServerThatSendsNoFireTimeIsNeverTakenForALateCopy() {
        VotePartyFired sent = new VotePartyFired(
                "survival-1",
                PARTY,
                25,
                VotePartyFired.Reach.EVERYONE,
                Set.of(),
                Instant.parse("2030-01-01T12:00:00Z"),
                true);
        byte[] frame = NetworkMessageCodec.encode(sent);

        VotePartyFired received =
                (VotePartyFired) NetworkMessageCodec.decode(Arrays.copyOf(frame, frame.length - Long.BYTES - 1));

        assertThat(received)
                .isEqualTo(new VotePartyFired("survival-1", PARTY, 25, VotePartyFired.Reach.EVERYONE, Set.of()));
        assertThat(received.firedBefore(Instant.parse("2031-01-01T00:00:00Z"))).isFalse();
    }

    @Test
    void aPartyForEveryoneCrossesTheWireUnchanged() {
        VotePartyFired sent = new VotePartyFired("survival-1", PARTY, 25, VotePartyFired.Reach.EVERYONE, Set.of());

        NetworkMessage received = NetworkMessageCodec.decode(NetworkMessageCodec.encode(sent));

        assertThat(received).isEqualTo(sent);
    }

    @Test
    void aPartyFromAServerThatSendsOnlyTheThresholdRewardsNobody() throws IOException {
        try (DataOutputStream out = header()) {
            out.writeInt(25);
        }

        NetworkMessage received = NetworkMessageCodec.decode(bytes.toByteArray());

        assertThat(received)
                .isEqualTo(new VotePartyFired(
                        "survival-1", VotePartyFired.NO_ID, 25, VotePartyFired.Reach.NONE, Set.of()));
    }

    @Test
    void aPartyFromAServerThatSendsNoPartyIdStillRewardsItsVoters() throws IOException {
        try (DataOutputStream out = header()) {
            out.writeInt(25);
            out.writeByte(VotePartyFired.Reach.VOTERS.wireTag());
            out.writeInt(1);
            out.writeLong(ALEX.getMostSignificantBits());
            out.writeLong(ALEX.getLeastSignificantBits());
        }

        NetworkMessage received = NetworkMessageCodec.decode(bytes.toByteArray());

        assertThat(received)
                .isEqualTo(new VotePartyFired(
                        "survival-1", VotePartyFired.NO_ID, 25, VotePartyFired.Reach.VOTERS, Set.of(ALEX)));
    }

    @Test
    void aPartyFromAServerThatSendsNoPartyIdStillRewardsEveryone() throws IOException {
        try (DataOutputStream out = header()) {
            out.writeInt(25);
            out.writeByte(VotePartyFired.Reach.EVERYONE.wireTag());
        }

        NetworkMessage received = NetworkMessageCodec.decode(bytes.toByteArray());

        assertThat(received)
                .isEqualTo(new VotePartyFired(
                        "survival-1", VotePartyFired.NO_ID, 25, VotePartyFired.Reach.EVERYONE, Set.of()));
    }

    @Test
    void aPartyCutBeforeItsIdDecodesAsTheSamePartyWithoutAnId() {
        VotePartyFired sent = new VotePartyFired("survival-1", PARTY, 25, VotePartyFired.Reach.VOTERS, Set.of(ALEX));
        byte[] frame = NetworkMessageCodec.encode(sent);

        NetworkMessage received = NetworkMessageCodec.decode(Arrays.copyOf(frame, frame.length - 16 - Long.BYTES - 1));

        assertThat(received)
                .isEqualTo(new VotePartyFired(
                        "survival-1", VotePartyFired.NO_ID, 25, VotePartyFired.Reach.VOTERS, Set.of(ALEX)));
    }

    private DataOutputStream header() throws IOException {
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(NetworkMessageCodec.VERSION);
        out.writeByte(NetworkMessage.MessageType.VOTE_PARTY_FIRED.wireTag());
        out.writeUTF("survival-1");
        return out;
    }
}
