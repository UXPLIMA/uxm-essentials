package com.uxplima.uxmessentials.vote.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.testing.RecordingEvents;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import com.uxplima.uxmessentials.vote.application.port.RewardApplier;
import com.uxplima.uxmessentials.vote.application.port.VoteAudience;
import com.uxplima.uxmessentials.vote.domain.PartyResetSchedule;
import com.uxplima.uxmessentials.vote.domain.event.VotePartyTriggered;
import com.uxplima.uxmessentials.vote.domain.reward.RewardGrant;
import com.uxplima.uxmessentials.vote.domain.reward.RewardSpec;
import org.junit.jupiter.api.Test;

/** A vote party pays every eligible player of the network once, each on the server they are connected to. */
class NetworkPartyTest {

    private static final PlayerRef ROBIN =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-0000000000b0"), "Robin");
    private static final PlayerRef KIM = new PlayerRef(UUID.fromString("00000000-0000-0000-0000-0000000000c1"), "Kim");
    private static final RewardSpec REWARD = new RewardSpec(
            100, Optional.empty(), List.of("give {player} diamond"), List.of(), List.of(), List.of(), Set.of());

    private final PartyVotes votes = new PartyVotes();
    private final RecordingEvents events = new RecordingEvents();
    private final Paid paid = new Paid();

    @Test
    void aVotersOnlyPartyPaysTheVotersHereAndHandsTheOthersToTheNetwork() {
        votes.markPartyParticipant(ALEX);
        votes.markPartyParticipant(ROBIN);

        force(party(true, true), ALEX, SAM);

        assertThat(paid.players).containsExactly(ALEX);
        assertThat(events.published()).contains(new VotePartyTriggered(25, true, Set.of(ROBIN.uuid())));
    }

    @Test
    void anOpenPartyPaysEveryoneHereAndListsNoVoters() {
        force(party(false, true), ALEX, SAM);

        assertThat(paid.players).containsExactlyInAnyOrder(ALEX, SAM);
        assertThat(events.published()).contains(new VotePartyTriggered(25, false, Set.of()));
    }

    @Test
    void anotherServerPaysOnlyTheVotersConnectedToIt() {
        new RewardNetworkParty(paid, online(ROBIN, KIM), party(true, true)).rewardVoters(Set.of(ROBIN.uuid()));

        assertThat(paid.players).containsExactly(ROBIN);
    }

    @Test
    void anotherServerPaysEveryoneConnectedToItForAnOpenParty() {
        new RewardNetworkParty(paid, online(ROBIN, KIM), party(false, true)).rewardEveryone();

        assertThat(paid.players).containsExactlyInAnyOrder(ROBIN, KIM);
    }

    @Test
    void anotherServerWithNetworkRewardsOffPaysNobody() {
        RewardNetworkParty rewards = new RewardNetworkParty(paid, online(ROBIN, KIM), party(false, false));

        rewards.rewardEveryone();
        rewards.rewardVoters(Set.of(ROBIN.uuid()));

        assertThat(paid.players).isEmpty();
    }

    private void force(PartyConfig party, PlayerRef... connected) {
        new ForceParty(
                        votes,
                        paid,
                        online(connected),
                        new RecordingNotifier().notifier(),
                        (key, placeholders, channels) -> {},
                        Set.of(),
                        events,
                        party)
                .execute(ALEX);
    }

    private static PartyConfig party(boolean onlyVoters, boolean networkRewards) {
        return new PartyConfig(REWARD, 25, onlyVoters, 0, PartyResetSchedule.NONE, List.of(), networkRewards);
    }

    private static VoteAudience online(PlayerRef... players) {
        List<PlayerRef> roster = List.of(players);
        return () -> roster;
    }

    /** Every player who received the party reward, once per payment. */
    private static final class Paid implements RewardApplier {

        private final List<PlayerRef> players = new ArrayList<>();

        @Override
        public void apply(PlayerRef voter, boolean online, RewardGrant grant) {
            players.add(voter);
        }
    }
}
