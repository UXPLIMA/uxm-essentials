package com.uxplima.uxmessentials.vote.adapter.inbound.listener;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.event.player.PlayerJoinEvent;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.vote.application.ApplyQueuedRewards;
import com.uxplima.uxmessentials.vote.application.VoteMessageKey;
import com.uxplima.uxmessentials.vote.application.VoteNotifier;
import com.uxplima.uxmessentials.vote.application.VoteReminderEligibility;
import com.uxplima.uxmessentials.vote.application.port.ReminderPreferences;
import com.uxplima.uxmessentials.vote.application.port.VoteRanking;
import com.uxplima.uxmessentials.vote.application.port.VoteRepository;
import com.uxplima.uxmessentials.vote.domain.QueuedReward;
import com.uxplima.uxmessentials.vote.domain.VotePeriod;
import com.uxplima.uxmessentials.vote.domain.VoteSiteCatalog;
import com.uxplima.uxmessentials.vote.domain.VoteSiteSpec;
import com.uxplima.uxmessentials.vote.domain.VoteTally;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Verifies the login-nag reminder threading added in the V4 follow-up: the DB eligibility check runs
 * through {@link Scheduler#async} (off-tick) and the PDC opt-in read + send run through
 * {@link Scheduler#onEntity} (entity thread), never the other way round. A {@link PhaseScheduler}
 * records which phase each access happened in so the routing can be asserted without a real Folia
 * runtime.
 */
class VoteJoinListenerReminderTest {

    private ServerMock server;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void eligibleOptedInPlayerGetsReminderWithDbAsyncAndPdcOnEntity() {
        PhaseScheduler scheduler = new PhaseScheduler();
        PhaseRepository repository = new PhaseRepository(scheduler);
        PhasePrefs prefs = new PhasePrefs(scheduler, true);
        RecordingSink sink = new RecordingSink();

        VoteSiteCatalog catalog =
                new VoteSiteCatalog(List.of(new VoteSiteSpec("PMC", Optional.empty(), Duration.ofHours(24))));
        VoteJoinListener listener = listener(scheduler, repository, catalog, prefs, notifier(sink));

        listener.onJoin(new PlayerJoinEvent(player, Component.empty()));

        // The player is eligible (never voted) and opted in → the reminder is sent.
        assertThat(sink.delivered).contains(VoteMessageKey.VOTE_REMINDER.key());
        // DB eligibility ran in the async phase; PDC opt-in read ran in the entity phase.
        assertThat(repository.lastVoteAtSitePhase).isEqualTo(Phase.ASYNC);
        assertThat(prefs.wantsRemindersPhase).isEqualTo(Phase.ENTITY);
    }

    @Test
    void optedOutPlayerGetsNoReminderEvenWhenEligible() {
        PhaseScheduler scheduler = new PhaseScheduler();
        PhaseRepository repository = new PhaseRepository(scheduler);
        PhasePrefs prefs = new PhasePrefs(scheduler, false); // opted out
        RecordingSink sink = new RecordingSink();

        VoteSiteCatalog catalog =
                new VoteSiteCatalog(List.of(new VoteSiteSpec("PMC", Optional.empty(), Duration.ofHours(24))));
        VoteJoinListener listener = listener(scheduler, repository, catalog, prefs, notifier(sink));

        listener.onJoin(new PlayerJoinEvent(player, Component.empty()));

        assertThat(sink.delivered).doesNotContain(VoteMessageKey.VOTE_REMINDER.key());
        // The PDC opt-in read still happened on the entity thread (before the opt-out short-circuit).
        assertThat(prefs.wantsRemindersPhase).isEqualTo(Phase.ENTITY);
    }

    @Test
    void ineligiblePlayerNeverReadsPdc() {
        PhaseScheduler scheduler = new PhaseScheduler();
        // Recorded a vote just now → on cooldown → not eligible.
        PhaseRepository repository = new PhaseRepository(scheduler, Optional.of(Instant.now()));
        PhasePrefs prefs = new PhasePrefs(scheduler, true);
        RecordingSink sink = new RecordingSink();

        VoteSiteCatalog catalog =
                new VoteSiteCatalog(List.of(new VoteSiteSpec("PMC", Optional.empty(), Duration.ofHours(24))));
        VoteJoinListener listener = listener(scheduler, repository, catalog, prefs, notifier(sink));

        listener.onJoin(new PlayerJoinEvent(player, Component.empty()));

        assertThat(sink.delivered).doesNotContain(VoteMessageKey.VOTE_REMINDER.key());
        // Not eligible → the entity hop never runs → PDC is never read.
        assertThat(prefs.wantsRemindersPhase).isNull();
    }

    // --- helpers ---

    private VoteJoinListener listener(
            Scheduler scheduler,
            VoteRepository repository,
            VoteSiteCatalog catalog,
            ReminderPreferences prefs,
            VoteNotifier notifier) {
        ApplyQueuedRewards applyQueued = new ApplyQueuedRewards(repository, (commands, name) -> {});
        VoteReminderEligibility eligibility = new VoteReminderEligibility(repository, catalog);
        return new VoteJoinListener(
                applyQueued, repository, scheduler, true, Duration.ofSeconds(1), eligibility, prefs, notifier);
    }

    /** A real {@link VoteNotifier} whose {@link Messages} echoes the key and whose sink records it. */
    private static VoteNotifier notifier(RecordingSink sink) {
        Messages messages = (viewer, key, placeholders) -> key.key();
        return new VoteNotifier(messages, sink);
    }

    private static final class RecordingSink implements MessageSink {
        final List<String> delivered = new ArrayList<>();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            delivered.add(renderedText);
        }
    }

    private enum Phase {
        ASYNC,
        ENTITY
    }

    /** Inline scheduler that records which phase the calling thread is in while a task runs. */
    private static final class PhaseScheduler implements Scheduler {
        @Nullable Phase current;

        @Override
        public void async(Runnable task) {
            Phase prev = current;
            current = Phase.ASYNC;
            try {
                task.run();
            } finally {
                current = prev;
            }
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            async(task);
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            Phase prev = current;
            current = Phase.ENTITY;
            try {
                task.run();
            } finally {
                current = prev;
            }
        }

        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }
    }

    private static final class PhasePrefs implements ReminderPreferences {
        private final PhaseScheduler scheduler;
        private final boolean wants;

        @Nullable Phase wantsRemindersPhase;

        PhasePrefs(PhaseScheduler scheduler, boolean wants) {
            this.scheduler = scheduler;
            this.wants = wants;
        }

        @Override
        public boolean wantsReminders(PlayerRef who) {
            wantsRemindersPhase = scheduler.current;
            return wants;
        }

        @Override
        public boolean toggle(PlayerRef who) {
            return wants;
        }
    }

    private static final class PhaseRepository implements VoteRepository {
        private final PhaseScheduler scheduler;
        private final Optional<Instant> lastVote;

        @Nullable Phase lastVoteAtSitePhase;

        PhaseRepository(PhaseScheduler scheduler) {
            this(scheduler, Optional.empty());
        }

        PhaseRepository(PhaseScheduler scheduler, Optional<Instant> lastVote) {
            this.scheduler = scheduler;
            this.lastVote = lastVote;
        }

        @Override
        public Optional<Instant> lastVoteAtSite(PlayerRef player, String site) {
            lastVoteAtSitePhase = scheduler.current;
            return lastVote;
        }

        @Override
        public int partyCount() {
            return 0;
        }

        @Override
        public void setPartyCount(int count) {}

        @Override
        public int incrementAndGetPartyCount() {
            return 0;
        }

        @Override
        public void enqueue(QueuedReward reward) {}

        @Override
        public List<QueuedReward> drainFor(PlayerRef player) {
            return List.of();
        }

        @Override
        public boolean hasPending(PlayerRef player) {
            return false;
        }

        @Override
        public VoteTally totalsOf(PlayerRef player) {
            return VoteTally.empty();
        }

        @Override
        public void saveTotals(PlayerRef player, VoteTally tally) {}

        @Override
        public List<VoteRanking> topVoters(VotePeriod period, int limit) {
            return List.of();
        }

        @Override
        public void markPartyParticipant(PlayerRef player) {}

        @Override
        public java.util.Set<UUID> partyParticipants() {
            return java.util.Set.of();
        }

        @Override
        public void clearPartyParticipants() {}

        @Override
        public long partyPeriodKey() {
            return 0L;
        }

        @Override
        public void setPartyPeriodKey(long key) {}

        @Override
        public int thresholdOverride() {
            return 0;
        }

        @Override
        public void setThresholdOverride(int override) {}

        @Override
        public boolean claimPartyFire(int threshold) {
            return false;
        }

        @Override
        public void resetTotals(PlayerRef player) {}

        @Override
        public void recordLastVoteAtSite(PlayerRef player, String site, Instant at) {}
    }
}
