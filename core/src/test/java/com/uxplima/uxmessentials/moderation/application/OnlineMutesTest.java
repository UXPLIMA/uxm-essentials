package com.uxplima.uxmessentials.moderation.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import com.uxplima.uxmessentials.messaging.application.port.MuteAnswer;
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
 * and every so often, and over once its time is up. While it is not known the answer says so, and the read is tried
 * again until the database answers.
 */
class OnlineMutesTest {

    private final MutableClock clock = new MutableClock(NOW);
    private final QueuedScheduler scheduler = new QueuedScheduler();
    private final SanctionRecords records = new SanctionRecords();
    private final RecordingLogger log = new RecordingLogger();
    private final AtomicLong ticks = new AtomicLong();
    private final OnlineMutes mutes = new OnlineMutes(records, scheduler, clock, log, ticks::get);

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

        assertThat(mutes.check(SAM)).isEqualTo(MuteAnswer.MUTED);
    }

    @Test
    void untilTheJoinReadLandsTheMuteIsUnknownAndTheAskingThreadReadsNothing() {
        records.saveMute(SAM, permanent());
        mutes.joined(SAM);

        assertThat(mutes.check(SAM)).isEqualTo(MuteAnswer.UNKNOWN);
        assertThat(mutes.check(SAM)).isEqualTo(MuteAnswer.UNKNOWN);
        assertThat(records.muteReads()).isZero();

        scheduler.runAll();

        assertThat(mutes.check(SAM)).isEqualTo(MuteAnswer.MUTED);
        assertThat(records.muteReads())
                .as("one read, however often it was asked")
                .isEqualTo(1);
    }

    @Test
    void aMuteAndAnUnmuteIssuedHereAreKeptAsTheyLand() {
        mutes.joined(SAM);
        scheduler.runAll();

        mutes.onEvent(new PlayerMuted(SAM, permanent(), NOW));
        assertThat(mutes.check(SAM)).isEqualTo(MuteAnswer.MUTED);

        mutes.onEvent(new PlayerUnmuted(SAM, NOW));
        assertThat(mutes.check(SAM)).isEqualTo(MuteAnswer.NOT_MUTED);
    }

    @Test
    void aMuteFromAnotherServerIsUnknownUntilItIsReadAgain() {
        mutes.joined(SAM);
        scheduler.runAll();
        records.saveMute(SAM, permanent());

        mutes.changedElsewhere(SAM.uuid());
        assertThat(mutes.check(SAM)).as("the kept answer is stale").isEqualTo(MuteAnswer.UNKNOWN);
        scheduler.runAll();

        assertThat(mutes.check(SAM)).isEqualTo(MuteAnswer.MUTED);
    }

    @Test
    void aChangeFromAnotherServerIsReadUntilTheDatabaseAnswersAgain() {
        mutes.joined(SAM);
        scheduler.runAll();
        records.saveMute(SAM, permanent());
        records.failMuteReads(5);

        mutes.changedElsewhere(SAM.uuid());
        scheduler.runNext();
        assertThat(mutes.check(SAM)).isEqualTo(MuteAnswer.UNKNOWN);
        scheduler.runAll();

        assertThat(mutes.check(SAM)).isEqualTo(MuteAnswer.MUTED);
    }

    @Test
    void aReadThatStartedBeforeAMuteHereDoesNotUndoIt() {
        mutes.joined(SAM);
        mutes.onEvent(new PlayerMuted(SAM, permanent(), NOW));

        scheduler.runAll();

        assertThat(mutes.check(SAM)).isEqualTo(MuteAnswer.MUTED);
    }

    @Test
    void aTimedMuteEndsOnItsOwn() {
        mutes.joined(SAM);
        scheduler.runAll();
        mutes.onEvent(new PlayerMuted(
                SAM, MuteState.timed(NOW.plus(Duration.ofMinutes(5)), Issuer.of(ALEX), Optional.empty(), NOW), NOW));

        clock.advance(Duration.ofMinutes(5));

        assertThat(mutes.check(SAM)).isEqualTo(MuteAnswer.NOT_MUTED);
    }

    @Test
    void aPlayerNotConnectedHereIsUnknownAndNotRead() {
        mutes.joined(SAM);
        scheduler.runAll();
        mutes.left(SAM);
        records.saveMute(SAM, permanent());

        mutes.changedElsewhere(SAM.uuid());
        mutes.onEvent(new PlayerUnmuted(SAM, NOW));
        scheduler.runAll();

        assertThat(mutes.check(SAM)).isEqualTo(MuteAnswer.UNKNOWN);
        assertThat(records.muteReads()).isEqualTo(1);
    }

    @Test
    void aJoinReadThatFailsIsTriedAgainUntilItLandsHoweverLongTheDatabaseIsDown() {
        records.saveMute(SAM, permanent());
        records.failMuteReads(12);
        mutes.joined(SAM);

        scheduler.runAll();
        records.saveMute(SAM, MuteState.none());

        assertThat(mutes.check(SAM)).isEqualTo(MuteAnswer.MUTED);
        assertThat(records.muteReads()).isEqualTo(13);
    }

    @Test
    void aReadThatKeepsFailingStopsWhenThePlayerLeaves() {
        records.failMuteReads(1);
        mutes.joined(SAM);

        scheduler.runNext();
        mutes.left(SAM);
        scheduler.runAll();

        assertThat(records.muteReads()).isEqualTo(1);
    }

    @Test
    void anOutageIsLoggedOncePerQuietSpellForEachKindOfRead() {
        records.failMuteReads(5);
        mutes.joined(SAM);
        scheduler.runNext();

        mutes.fresh().check(ALEX);
        mutes.fresh().check(ALEX);
        mutes.fresh().check(ALEX);
        ticks.addAndGet(ReadFailureLog.QUIET.toNanos());
        mutes.fresh().check(ALEX);

        assertThat(log.errors())
                .containsExactly(
                        "event=online_mute_read_failed player=Sam attempt=1 unlogged=0",
                        "event=mute_check_read_failed player=Alex unlogged=0",
                        "event=mute_check_read_failed player=Alex unlogged=2");
    }

    @Test
    void aReadThatSucceedsEndsTheOutageSoTheNextFailureIsLoggedAtOnce() {
        records.failMuteReads(2);
        mutes.fresh().check(SAM);
        mutes.fresh().check(SAM);
        mutes.fresh().check(SAM);

        records.failMuteReads(1);
        mutes.fresh().check(SAM);

        assertThat(log.errors())
                .containsExactly(
                        "event=mute_check_read_failed player=Sam unlogged=0",
                        "event=mute_check_read_failed player=Sam unlogged=0");
    }

    @Test
    void aWallClockSetBackDoesNotKeepTheLogQuiet() {
        records.failMuteReads(2);
        mutes.fresh().check(SAM);

        clock.advance(Duration.ofHours(-1));
        ticks.addAndGet(ReadFailureLog.QUIET.toNanos());
        mutes.fresh().check(SAM);

        assertThat(log.errors()).hasSize(2);
    }

    @Test
    void theFreshCheckSeesAMuteSetElsewhereAtOnce() {
        mutes.joined(SAM);
        scheduler.runAll();

        records.saveMute(SAM, permanent());

        assertThat(mutes.fresh().check(SAM)).isEqualTo(MuteAnswer.MUTED);
    }

    @Test
    void whenTheFreshReadFailsTheKeptMuteAnswers() {
        records.saveMute(SAM, permanent());
        mutes.joined(SAM);
        scheduler.runAll();
        records.failMuteReads(1);

        assertThat(mutes.fresh().check(SAM)).isEqualTo(MuteAnswer.MUTED);
    }

    @Test
    void whenTheFreshReadFailsForAPlayerWithNothingKnownTheAnswerIsUnknown() {
        mutes.joined(SAM);
        records.failMuteReads(1);

        assertThat(mutes.fresh().check(SAM)).isEqualTo(MuteAnswer.UNKNOWN);
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

        assertThat(mutes.check(SAM)).isEqualTo(MuteAnswer.MUTED);
        assertThat(mutes.check(ALEX)).isEqualTo(MuteAnswer.NOT_MUTED);
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

        assertThat(mutes.check(SAM)).isEqualTo(MuteAnswer.MUTED);
        scheduler.runAll();
        assertThat(mutes.check(SAM)).isEqualTo(MuteAnswer.MUTED);
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
