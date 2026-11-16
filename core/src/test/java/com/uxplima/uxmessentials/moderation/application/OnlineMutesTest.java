package com.uxplima.uxmessentials.moderation.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.moderation.application.port.SanctionSync;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.moderation.domain.event.PlayerMuted;
import com.uxplima.uxmessentials.moderation.domain.event.PlayerUnmuted;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.testing.MutableClock;
import com.uxplima.uxmessentials.testing.QueuedScheduler;
import com.uxplima.uxmessentials.testing.RecordingEvents;
import com.uxplima.uxmessentials.testing.RecordingLogger;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import org.junit.jupiter.api.Test;

/**
 * A connected player's mute is kept in memory, so the checks on the server thread answer without the database: read
 * at join on the async lane, changed by a mute or unmute here as it lands, read again when another server changes it
 * and every so often, and over once its time is up.
 */
class OnlineMutesTest {

    private final MutableClock clock = new MutableClock(NOW);
    private final QueuedScheduler scheduler = new QueuedScheduler();
    private final SanctionRecords records = new SanctionRecords();
    private final RecordingLogger log = new RecordingLogger();
    private final OnlineMutes mutes = new OnlineMutes(records, scheduler, clock, log);

    private MuteState permanent() {
        return MuteState.permanent(Issuer.of(ALEX), Optional.empty(), NOW);
    }

    @Test
    void aConnectedPlayersMuteIsReadAtJoinAndThenAnsweredFromMemory() {
        records.saveMute(SAM, permanent());
        mutes.joined(SAM);
        scheduler.runAll();

        // Written behind the cache's back: a check on the server thread does not go to the database for it.
        records.saveMute(SAM, MuteState.none());

        assertThat(mutes.isMuted(SAM)).isTrue();
    }

    @Test
    void untilTheJoinReadLandsTheDatabaseAnswers() {
        records.saveMute(SAM, permanent());
        mutes.joined(SAM);

        assertThat(mutes.isMuted(SAM)).isTrue();
    }

    @Test
    void aMuteAndAnUnmuteIssuedHereAreKeptAsTheyLand() {
        mutes.joined(SAM);
        scheduler.runAll();

        mutes.onEvent(new PlayerMuted(SAM, permanent(), NOW));
        assertThat(mutes.isMuted(SAM)).isTrue();

        mutes.onEvent(new PlayerUnmuted(SAM, NOW));
        assertThat(mutes.isMuted(SAM)).isFalse();
    }

    @Test
    void aMuteFromAnotherServerIsReadAgain() {
        mutes.joined(SAM);
        scheduler.runAll();
        records.saveMute(SAM, permanent());

        mutes.changedElsewhere(SAM.uuid());
        scheduler.runAll();

        assertThat(mutes.isMuted(SAM)).isTrue();
    }

    @Test
    void aReadThatStartedBeforeAMuteHereDoesNotUndoIt() {
        mutes.joined(SAM);
        mutes.onEvent(new PlayerMuted(SAM, permanent(), NOW));

        scheduler.runAll();

        assertThat(mutes.isMuted(SAM)).isTrue();
    }

    @Test
    void aTimedMuteEndsOnItsOwn() {
        mutes.joined(SAM);
        scheduler.runAll();
        mutes.onEvent(new PlayerMuted(
                SAM, MuteState.timed(NOW.plus(Duration.ofMinutes(5)), Issuer.of(ALEX), Optional.empty(), NOW), NOW));

        clock.advance(Duration.ofMinutes(5));

        assertThat(mutes.isMuted(SAM)).isFalse();
    }

    @Test
    void aPlayerWhoLeftIsAnsweredFromTheDatabaseAndNotReadAgain() {
        mutes.joined(SAM);
        scheduler.runAll();
        mutes.left(SAM);
        records.saveMute(SAM, permanent());

        mutes.changedElsewhere(SAM.uuid());
        mutes.onEvent(new PlayerUnmuted(SAM, NOW));

        assertThat(mutes.isMuted(SAM)).isTrue();
    }

    @Test
    void aJoinReadThatFailsIsLoggedAndTriedAgainUntilItLands() {
        records.saveMute(SAM, permanent());
        records.failMuteReads(2);
        mutes.joined(SAM);

        scheduler.runAll();
        records.saveMute(SAM, MuteState.none());

        assertThat(log.errors())
                .containsExactly(
                        "event=online_mute_read_failed player=Sam attempt=1",
                        "event=online_mute_read_failed player=Sam attempt=2");
        assertThat(mutes.isMuted(SAM)).isTrue();
    }

    @Test
    void aJoinReadThatKeepsFailingLeavesTheDatabaseAnswering() {
        records.failMuteReads(OnlineMutes.READ_ATTEMPTS);
        mutes.joined(SAM);

        scheduler.runAll();
        records.saveMute(SAM, permanent());

        assertThat(log.errors()).hasSize(OnlineMutes.READ_ATTEMPTS);
        assertThat(mutes.isMuted(SAM)).isTrue();
    }

    @Test
    void theRegularReadPicksUpAChangeThatCameWithNoFrame() {
        mutes.joined(SAM);
        mutes.joined(ALEX);
        scheduler.runAll();
        records.saveMute(SAM, permanent());

        mutes.refreshAll();
        scheduler.runAll();
        records.saveMute(SAM, MuteState.none());

        assertThat(mutes.isMuted(SAM)).isTrue();
        assertThat(mutes.isMuted(ALEX)).isFalse();
    }

    @Test
    void aSlowReadFromAnEarlierVisitDoesNotOverwriteTheNewOne() {
        mutes.joined(SAM);
        // While the first visit's read is in flight (it has already read "not muted"), Sam leaves, is muted from
        // elsewhere and comes back.
        records.duringNextMuteRead(() -> {
            mutes.left(SAM);
            records.saveMute(SAM, permanent());
            mutes.joined(SAM);
        });

        scheduler.runNext();

        assertThat(mutes.isMuted(SAM)).isTrue();
        scheduler.runAll();
        assertThat(mutes.isMuted(SAM)).isTrue();
    }

    @Test
    void anUnmuteHereTellsThePeersSoTheyDropTheMuteToo() {
        List<PlayerRef> told = new ArrayList<>();
        records.saveMute(SAM, permanent());
        Unmute unmute = new Unmute(
                records,
                new RecordingNotifier().notifier(),
                new SilentAudit(),
                new RecordingEvents(),
                new SanctionHistoryRecorder(new DiscardedHistory(), clock),
                new SanctionSync() {
                    @Override
                    public void banChanged(PlayerRef target) {}

                    @Override
                    public void muteChanged(PlayerRef target) {
                        told.add(target);
                    }

                    @Override
                    public void ipBanChanged(String ip) {}
                },
                clock);

        unmute.unmute(ALEX, SAM);

        assertThat(told).containsExactly(SAM);
    }
}
