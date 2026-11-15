package com.uxplima.uxmessentials.shared.network;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/** A vote party tells the other servers whom they may reward, and a party from an older server rewards nobody. */
class VotePartyFiredTest {

    @Test
    void theVotersOfAPartyCrossTheWireUnchanged() {
        VotePartyFired sent = new VotePartyFired(
                "survival-1",
                25,
                VotePartyFired.Reach.VOTERS,
                Set.of(UUID.fromString("00000000-0000-0000-0000-000000000a1e"), new UUID(7L, 9L)));

        NetworkMessage received = NetworkMessageCodec.decode(NetworkMessageCodec.encode(sent));

        assertThat(received).isEqualTo(sent);
    }

    @Test
    void aPartyForEveryoneCrossesTheWireUnchanged() {
        VotePartyFired sent = new VotePartyFired("survival-1", 25, VotePartyFired.Reach.EVERYONE, Set.of());

        NetworkMessage received = NetworkMessageCodec.decode(NetworkMessageCodec.encode(sent));

        assertThat(received).isEqualTo(sent);
    }

    @Test
    void aPartyFromAServerThatSendsOnlyTheThresholdRewardsNobody() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(NetworkMessageCodec.VERSION);
            out.writeByte(NetworkMessage.MessageType.VOTE_PARTY_FIRED.wireTag());
            out.writeUTF("survival-1");
            out.writeInt(25);
        }

        NetworkMessage received = NetworkMessageCodec.decode(bytes.toByteArray());

        assertThat(received).isEqualTo(new VotePartyFired("survival-1", 25, VotePartyFired.Reach.NONE, Set.of()));
    }
}
