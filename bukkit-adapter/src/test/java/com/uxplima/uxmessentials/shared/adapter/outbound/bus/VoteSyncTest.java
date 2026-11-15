package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import com.uxplima.uxmessentials.shared.network.VotePartyFired;
import com.uxplima.uxmessentials.vote.application.PartyConfig;
import com.uxplima.uxmessentials.vote.application.RewardNetworkParty;
import com.uxplima.uxmessentials.vote.domain.PartyResetSchedule;
import com.uxplima.uxmessentials.vote.domain.event.VotePartyTriggered;
import com.uxplima.uxmessentials.vote.domain.reward.RewardSpec;
import org.junit.jupiter.api.Test;

/** A party is sent with the voters other servers may pay, and a server pays only the listed voters it has. */
class VoteSyncTest {

    private static final PlayerRef ALEX =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-000000000a1e"), "Alex");
    private static final PlayerRef SAM = new PlayerRef(UUID.fromString("00000000-0000-0000-0000-000000000005"), "Sam");
    private static final RewardSpec REWARD = new RewardSpec(
            100, Optional.empty(), List.of("give {player} diamond"), List.of(), List.of(), List.of(), Set.of());

    private final List<NetworkMessage> published = new ArrayList<>();
    private final BusPublisher bus = new BusPublisher() {
        @Override
        public void publish(NetworkMessage message) {
            published.add(message);
        }

        @Override
        public String serverId() {
            return "survival-1";
        }
    };

    @Test
    void aVoterListThatFitsTheFrameIsSent() {
        Set<UUID> voters = voters(VoteSync.MAX_VOTERS_ON_WIRE);

        VoteSync.partyPublisher(bus).accept(new VotePartyTriggered(25, true, voters));

        assertThat(published)
                .containsExactly(new VotePartyFired("survival-1", 25, VotePartyFired.Reach.VOTERS, voters));
    }

    @Test
    void aVoterListTooLongForTheFrameNamesNobody() {
        VoteSync.partyPublisher(bus).accept(new VotePartyTriggered(25, true, voters(VoteSync.MAX_VOTERS_ON_WIRE + 1)));

        assertThat(published)
                .containsExactly(new VotePartyFired("survival-1", 25, VotePartyFired.Reach.NONE, Set.of()));
    }

    @Test
    void aRemoteVotersPartyPaysOnlyTheListedPlayersOnlineHere() {
        List<PlayerRef> paid = new ArrayList<>();
        RewardNetworkParty rewards = new RewardNetworkParty(
                (voter, online, grant) -> paid.add(voter),
                () -> List.of(ALEX, SAM),
                new PartyConfig(REWARD, 25, false, 0, PartyResetSchedule.NONE, List.of(), true));
        RemoteSyncListener listener =
                VoteSync.listener(() -> {}, (key, placeholders, channels) -> {}, Set.of(), rewards);

        listener.onRemoteChange(
                new VotePartyFired("lobby-1", 25, VotePartyFired.Reach.VOTERS, Set.of(ALEX.uuid(), new UUID(7L, 9L))));

        assertThat(paid).containsExactly(ALEX);
    }

    private static Set<UUID> voters(int count) {
        Set<UUID> voters = new HashSet<>();
        for (int i = 0; i < count; i++) {
            voters.add(new UUID(0L, i));
        }
        return voters;
    }
}
