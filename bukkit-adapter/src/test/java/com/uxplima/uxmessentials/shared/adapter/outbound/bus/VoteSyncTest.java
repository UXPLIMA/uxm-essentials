package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import com.uxplima.uxmessentials.shared.network.VotePartyFired;
import com.uxplima.uxmessentials.vote.application.PartyConfig;
import com.uxplima.uxmessentials.vote.application.RewardNetworkParty;
import com.uxplima.uxmessentials.vote.domain.PartyResetSchedule;
import com.uxplima.uxmessentials.vote.domain.event.VotePartyTriggered;
import com.uxplima.uxmessentials.vote.domain.reward.RewardSpec;
import org.junit.jupiter.api.Test;

/**
 * A party is sent under its own id with the voters other servers may pay, and a server pays only the listed voters
 * it has, once per party however many copies of the frame reach it.
 */
class VoteSyncTest {

    private static final PlayerRef ALEX =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-000000000a1e"), "Alex");
    private static final PlayerRef SAM = new PlayerRef(UUID.fromString("00000000-0000-0000-0000-000000000005"), "Sam");
    private static final RewardSpec REWARD = new RewardSpec(
            100, Optional.empty(), List.of("give {player} diamond"), List.of(), List.of(), List.of(), Set.of());

    private static final UUID PARTY = new UUID(0L, 0xf1L);
    private static final Instant STARTED = Instant.parse("2030-01-01T12:00:00Z");
    private static final Clock NOW = Clock.fixed(STARTED.plusSeconds(60), ZoneOffset.UTC);

    private final List<PlayerRef> paid = new ArrayList<>();
    private int announcements;
    private final RemoteSyncListener listener = VoteSync.listener(
            () -> {},
            (key, placeholders, channels) -> announcements++,
            Set.of(),
            new RewardNetworkParty(
                    (voter, online, grant) -> paid.add(voter),
                    () -> List.of(ALEX, SAM),
                    new PartyConfig(REWARD, 25, false, 0, PartyResetSchedule.NONE, List.of(), true)),
            STARTED);

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

        VoteSync.partyPublisher(bus, NOW, true).accept(new VotePartyTriggered(25, true, voters));

        assertThat(published)
                .containsExactly(new VotePartyFired(
                        "survival-1", partyId(0), 25, VotePartyFired.Reach.VOTERS, voters, NOW.instant(), true));
    }

    @Test
    void aVoterListTooLongForTheFrameNamesNobody() {
        VoteSync.partyPublisher(bus, NOW, false)
                .accept(new VotePartyTriggered(25, true, voters(VoteSync.MAX_VOTERS_ON_WIRE + 1)));

        assertThat(published)
                .containsExactly(new VotePartyFired(
                        "survival-1", partyId(0), 25, VotePartyFired.Reach.NONE, Set.of(), NOW.instant(), false));
    }

    @Test
    void everyPartyIsSentUnderItsOwnId() {
        Consumer<DomainEvent> publisher = VoteSync.partyPublisher(bus, NOW, true);

        publisher.accept(new VotePartyTriggered(25, false, Set.of()));
        publisher.accept(new VotePartyTriggered(25, false, Set.of()));

        assertThat(partyId(0)).isNotEqualTo(VotePartyFired.NO_ID).isNotEqualTo(partyId(1));
    }

    @Test
    void aRemoteVotersPartyPaysOnlyTheListedPlayersOnlineHere() {
        listener.onRemoteChange(new VotePartyFired(
                "lobby-1", PARTY, 25, VotePartyFired.Reach.VOTERS, Set.of(ALEX.uuid(), new UUID(7L, 9L))));

        assertThat(paid).containsExactly(ALEX);
    }

    @Test
    void aPartyArrivingOverBothTransportsIsCelebratedAndPaidOnce() {
        VotePartyFired party = new VotePartyFired("lobby-1", PARTY, 25, VotePartyFired.Reach.EVERYONE, Set.of());

        listener.onRemoteChange(party);
        listener.onRemoteChange(party);

        assertThat(paid).containsExactly(ALEX, SAM);
        assertThat(announcements).isEqualTo(1);
    }

    @Test
    void aBothCarrierCopyOfAPartyThatFiredBeforeThisServerStartedListeningIsNotPaidAgain() {
        listener.onRemoteChange(new VotePartyFired(
                "vote-1", PARTY, 25, VotePartyFired.Reach.EVERYONE, Set.of(), STARTED.minusSeconds(3600), true));

        assertThat(paid).isEmpty();
        assertThat(announcements).isZero();
    }

    @Test
    void aProxyOnlyPartyThatArrivesLateIsStillPaid() {
        listener.onRemoteChange(new VotePartyFired(
                "vote-1", PARTY, 25, VotePartyFired.Reach.EVERYONE, Set.of(), STARTED.minusSeconds(3600), false));

        assertThat(paid).containsExactly(ALEX, SAM);
    }

    @Test
    void aServerOnTheProxyAlonePaysABothCarrierPartyHoweverLateItArrives() {
        RemoteSyncListener proxyOnly = VoteSync.listener(
                () -> {},
                (key, placeholders, channels) -> announcements++,
                Set.of(),
                new RewardNetworkParty(
                        (voter, online, grant) -> paid.add(voter),
                        () -> List.of(ALEX, SAM),
                        new PartyConfig(REWARD, 25, false, 0, PartyResetSchedule.NONE, List.of(), true)),
                Instant.MIN);

        proxyOnly.onRemoteChange(new VotePartyFired(
                "vote-1", PARTY, 25, VotePartyFired.Reach.EVERYONE, Set.of(), STARTED.minusSeconds(3600), true));

        assertThat(paid).containsExactly(ALEX, SAM);
    }

    @Test
    void aBothCarrierPartyThatFiredWhileThisServerListensIsPaidOnce() {
        VotePartyFired party = new VotePartyFired(
                "vote-1", PARTY, 25, VotePartyFired.Reach.EVERYONE, Set.of(), STARTED.plusSeconds(5), true);

        listener.onRemoteChange(party);
        listener.onRemoteChange(party);

        assertThat(paid).containsExactly(ALEX, SAM);
    }

    @Test
    void twoPartiesAtTheSameThresholdAreBothPaid() {
        listener.onRemoteChange(new VotePartyFired("lobby-1", PARTY, 25, VotePartyFired.Reach.EVERYONE, Set.of()));
        listener.onRemoteChange(
                new VotePartyFired("lobby-1", new UUID(0L, 0xf2L), 25, VotePartyFired.Reach.EVERYONE, Set.of()));

        assertThat(paid).containsExactly(ALEX, SAM, ALEX, SAM);
        assertThat(announcements).isEqualTo(2);
    }

    @Test
    void aPartyFromAServerThatSendsNoIdIsStillPaid() {
        listener.onRemoteChange(
                new VotePartyFired("lobby-1", VotePartyFired.NO_ID, 25, VotePartyFired.Reach.EVERYONE, Set.of()));

        assertThat(paid).containsExactly(ALEX, SAM);
    }

    private UUID partyId(int index) {
        return ((VotePartyFired) published.get(index)).partyId();
    }

    private static Set<UUID> voters(int count) {
        Set<UUID> voters = new HashSet<>();
        for (int i = 0; i < count; i++) {
            voters.add(new UUID(0L, i));
        }
        return voters;
    }
}
