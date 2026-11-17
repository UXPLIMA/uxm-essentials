package com.uxplima.uxmessentials.moderation.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;

import com.uxplima.uxmessentials.moderation.application.port.SanctionSync;
import com.uxplima.uxmessentials.moderation.domain.AddressStrictness;
import com.uxplima.uxmessentials.moderation.domain.ModerationMessageKey;
import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.shared.application.IpAlts;
import com.uxplima.uxmessentials.shared.application.permission.EnforcementPermissions;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaResult;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.application.port.StoredPermissions.Answer;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.message.SharedMessageKey;
import com.uxplima.uxmessentials.testing.FakePermissions;
import com.uxplima.uxmessentials.testing.FakeStoredPermissions;
import com.uxplima.uxmessentials.testing.MutableClock;
import com.uxplima.uxmessentials.testing.OnlineLookup;
import com.uxplima.uxmessentials.testing.QueuedScheduler;
import com.uxplima.uxmessentials.testing.RecordingEvents;
import com.uxplima.uxmessentials.testing.RecordingLogger;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import org.junit.jupiter.api.Test;

/**
 * A ban asked for on the server thread is stored on the async lane, and the player is kicked only once it is stored.
 * A player who leaves before then is still banned and cannot log back in. The staff member's caps are read before the
 * work leaves their thread, the target's immunity on the target's own thread, and work about one player is stored in
 * the order it was asked. An immunity that cannot be read keeps the sanction off the target.
 */
class StorageLaneTest {

    private static final String ADDRESS = "198.51.100.9";

    private final MutableClock clock = new MutableClock(NOW);
    private final QueuedScheduler scheduler = new QueuedScheduler();
    private final SanctionRecords records = new SanctionRecords();
    private final OnlinePlayers online = new OnlinePlayers().connect(SAM, ADDRESS);
    private final FakePermissions permissions = new FakePermissions();
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final RecordingLogger log = new RecordingLogger();
    private boolean liveReadable = true;
    private final LanePermissions reader = new LanePermissions(permissions, () -> liveReadable, log);
    private final OnlineLookup lookup = new OnlineLookup().with(ALEX, SAM);
    private final FakeStoredPermissions stored = new FakeStoredPermissions();
    private final StorageLane lane = new StorageLane(scheduler, notifier.notifier(), permissions, stored, lookup, log);
    private final Ban ban = new Ban(
            records,
            online,
            new ModerationGuard(reader),
            notifier.notifier(),
            new SilentAudit(),
            new RecordingEvents(),
            new SanctionHistoryRecorder(new DiscardedHistory(), clock),
            new SanctionDurationLimit(reader),
            (key, placeholders) -> {},
            SanctionSync.NONE,
            AddressStrictness.NORMAL,
            new NoIpHistory(),
            clock);
    private final Mute mute = new Mute(
            records,
            new ModerationGuard(reader),
            notifier.notifier(),
            new SilentAudit(),
            new RecordingEvents(),
            new SanctionHistoryRecorder(new DiscardedHistory(), clock),
            new SanctionDurationLimit(reader),
            (key, placeholders) -> {},
            SanctionSync.NONE,
            clock);
    private final Unmute unmute = new Unmute(
            records,
            notifier.notifier(),
            new SilentAudit(),
            new RecordingEvents(),
            new SanctionHistoryRecorder(new DiscardedHistory(), clock),
            SanctionSync.NONE,
            clock);
    private final LoginEnforcement login = new LoginEnforcement(
            records, new IpAlts(new NoIpHistory(), address -> address), notifier.notifier(), new SilentAudit(), clock);

    @Test
    void nothingIsStoredOrKickedUntilTheLaneRunsAndTheKickFollowsTheWrite() {
        lane.run(ALEX, SAM, () -> ban.ban(ALEX, SAM, Optional.of("cheating"), false));

        assertThat(records.loadTempban(SAM)).isEqualTo(TempbanState.none());
        assertThat(online.kicked()).isEmpty();
        assertThat(notifier.sent()).isEmpty();

        scheduler.runAll();

        assertThat(records.loadTempban(SAM)).isInstanceOf(TempbanState.Active.class);
        assertThat(online.kicked()).containsOnlyKeys(SAM);
    }

    @Test
    void aPlayerWhoLeavesBeforeTheBanIsStoredIsBannedAllTheSame() {
        lane.run(ALEX, SAM, () -> ban.ban(ALEX, SAM, Optional.of("cheating"), false));
        online.leave(SAM);

        scheduler.runAll();

        assertThat(online.kicked()).isEmpty();
        assertThat(Logins.check(login, SAM, ADDRESS, false, false).allowed()).isFalse();
    }

    @Test
    void workThatFailsTellsTheStaffMemberAndIsLogged() {
        lane.run(ALEX, SAM, () -> {
            throw new IllegalStateException("the database is down");
        });

        scheduler.runAll();

        assertThat(notifier.keys()).containsExactly(SharedMessageKey.COMMAND_ERROR.key());
        assertThat(log.errors()).containsExactly("event=moderation_lane_failed actor=Alex");
    }

    @Test
    void workTheSchedulerRefusesIsLoggedAndFailsTheCallersFuture() {
        StorageLane closing =
                new StorageLane(new RefusingScheduler(), notifier.notifier(), permissions, stored, lookup, log);

        closing.run(ALEX, SAM, () -> {});
        CompletableFuture<String> answer = closing.supply(ALEX, SAM, () -> "stored");

        assertThat(answer).isCompletedExceptionally();
        assertThat(log.errors())
                .containsExactly(
                        "event=moderation_lane_dropped actor=Alex", "event=moderation_lane_dropped actor=Alex");
    }

    @Test
    void onTheTargetsOwnThreadTheirImmunityIsReadWhenAskedAndKept() {
        StorageLane owning =
                new StorageLane(new OwningScheduler(scheduler), notifier.notifier(), permissions, stored, lookup, log);
        permissions.grant(SAM, ModerationGuard.EXEMPT_NODE);
        owning.run(ALEX, SAM, () -> ban.ban(ALEX, SAM, Optional.of("cheating"), false));
        permissions.revoke(SAM, ModerationGuard.EXEMPT_NODE);

        scheduler.runAll();

        assertThat(records.loadTempban(SAM)).isEqualTo(TempbanState.none());
    }

    @Test
    void fromAThreadThatDoesNotOwnTheTargetTheImmunityIsReadOnTheTargetsThreadBeforeTheBanRuns() {
        permissions.grant(SAM, ModerationGuard.EXEMPT_NODE);
        lane.run(ALEX, SAM, () -> ban.ban(ALEX, SAM, Optional.of("cheating"), false));
        // Read on the target's thread, which has not run yet: what they hold by then is what counts.
        permissions.revoke(SAM, ModerationGuard.EXEMPT_NODE);

        scheduler.runAll();

        assertThat(records.loadTempban(SAM)).isInstanceOf(TempbanState.Active.class);
    }

    @Test
    void anExemptTargetInAnotherRegionIsNotBanned() {
        permissions.grant(SAM, ModerationGuard.EXEMPT_NODE);
        lane.run(ALEX, SAM, () -> ban.ban(ALEX, SAM, Optional.of("cheating"), false));

        scheduler.runAll();

        assertThat(records.loadTempban(SAM)).isEqualTo(TempbanState.none());
        assertThat(online.kicked()).isEmpty();
    }

    @Test
    void aTargetWhoLeavesBeforeTheirThreadAnswersIsNotSanctionedAndTheStaffMemberIsToldWhy() {
        lane.run(ALEX, SAM, () -> ban.ban(ALEX, SAM, Optional.of("cheating"), false));
        scheduler.leave(SAM);

        scheduler.runAll();

        assertThat(records.loadTempban(SAM)).isEqualTo(TempbanState.none());
        assertThat(notifier.keys()).contains(ModerationMessageKey.TARGET_EXEMPTION_UNREAD.key());
        assertThat(log.warnings()).containsExactly("event=moderation_exemption_unread actor={} target={} [Alex, Sam]");
    }

    @Test
    void anUnreadImmunityAlsoKeepsAnApiSanctionOffTheTarget() {
        CompletableFuture<Boolean> banned = lane.supply(
                ALEX,
                SAM,
                () -> ban.ban(ALEX, SAM, Optional.of("cheating"), false).isOk());
        scheduler.leave(SAM);

        scheduler.runAll();

        assertThat(banned).isCompletedWithValue(false);
        assertThat(records.loadTempban(SAM)).isEqualTo(TempbanState.none());
    }

    @Test
    void aKickOfEveryoneReadsEachImmunityOnItsOwnThreadAndKeepsTheUnreadOnesOut() {
        PlayerRef robin = new PlayerRef(java.util.UUID.fromString("00000000-0000-0000-0000-0000000000a3"), "Robin");
        lookup.with(robin);
        permissions.grant(SAM, ModerationGuard.EXEMPT_NODE);
        java.util.List<String> exempt = new java.util.ArrayList<>();
        ModerationGuard guard = new ModerationGuard(reader);
        lane.run(ALEX, java.util.List.of(SAM, robin), () -> {
            exempt.add("Sam=" + guard.isExempt(SAM));
            exempt.add("Robin=" + guard.isExempt(robin));
        });
        scheduler.leave(robin);
        liveReadable = false;

        scheduler.runAll();

        assertThat(exempt).containsExactly("Sam=true", "Robin=true");
        assertThat(notifier.keys()).containsExactly(ModerationMessageKey.TARGET_EXEMPTION_UNREAD.key());
    }

    @Test
    void aTargetWhoIsNotHereAndHoldsTheNodeInStorageIsNotBanned() {
        lookup.leave(SAM);
        stored.answer(SAM, Answer.HELD);
        lane.run(ALEX, SAM, () -> ban.ban(ALEX, SAM, Optional.of("cheating"), false));

        scheduler.runAll();

        assertThat(records.loadTempban(SAM)).isEqualTo(TempbanState.none());
        assertThat(notifier.keys()).contains(ModerationMessageKey.TARGET_EXEMPT.key());
    }

    @Test
    void aTargetWhoIsNotHereAndDoesNotHoldTheNodeInStorageIsBanned() {
        lookup.leave(SAM);
        stored.answer(SAM, Answer.NOT_HELD);
        lane.run(ALEX, SAM, () -> ban.ban(ALEX, SAM, Optional.of("cheating"), false));

        scheduler.runAll();

        assertThat(records.loadTempban(SAM)).isInstanceOf(TempbanState.Active.class);
        assertThat(log.warnings()).isEmpty();
    }

    @Test
    void aTargetWhoseStoredNodeNothingHereCanReadIsNotBannedAndTheStaffMemberIsToldWhy() {
        lookup.leave(SAM);
        lane.run(ALEX, SAM, () -> ban.ban(ALEX, SAM, Optional.of("cheating"), false));

        scheduler.runAll();

        assertThat(records.loadTempban(SAM)).isEqualTo(TempbanState.none());
        assertThat(notifier.keys()).contains(ModerationMessageKey.TARGET_EXEMPTION_UNKNOWN.key());
        assertThat(notifier.keys()).doesNotContain(ModerationMessageKey.TARGET_EXEMPTION_UNREAD.key());
        assertThat(log.warnings())
                .containsExactly("event=moderation_exemption_unknowable actor={} target={} [Alex, Sam]");
    }

    @Test
    void aStoredReadThatFailsKeepsAnApiSanctionOffTheTargetAndAsksToTryAgain() {
        lookup.leave(SAM);
        stored.answer(SAM, Answer.NO_ANSWER);
        CompletableFuture<Boolean> banned = lane.supply(
                ALEX,
                SAM,
                () -> ban.ban(ALEX, SAM, Optional.of("cheating"), false).isOk());

        scheduler.runAll();

        assertThat(banned).isCompletedWithValue(false);
        assertThat(notifier.keys()).contains(ModerationMessageKey.TARGET_EXEMPTION_UNREAD.key());
    }

    @Test
    void theBanWaitsForTheStoredAnswerAndLaterWorkAboutTheTargetWaitsBehindIt() {
        lookup.leave(SAM);
        stored.hold(SAM);
        java.util.List<String> order = new java.util.ArrayList<>();
        lane.run(ALEX, SAM, () -> {
            ban.ban(ALEX, SAM, Optional.of("cheating"), false);
            order.add("ban");
        });
        lane.background(SAM, () -> order.add("bookkeeping"));

        scheduler.runAll();
        assertThat(order).isEmpty();
        assertThat(records.loadTempban(SAM)).isEqualTo(TempbanState.none());

        stored.release(SAM, Answer.NOT_HELD);
        scheduler.runAll();

        assertThat(order).containsExactly("ban", "bookkeeping");
        assertThat(records.loadTempban(SAM)).isInstanceOf(TempbanState.Active.class);
    }

    @Test
    void aTargetWhoWasNotExemptWhenAskedIsBannedOffTheServerThread() {
        lane.run(ALEX, SAM, () -> ban.ban(ALEX, SAM, Optional.of("cheating"), false));
        liveReadable = false;

        scheduler.runAll();

        assertThat(records.loadTempban(SAM)).isInstanceOf(TempbanState.Active.class);
        assertThat(log.warnings()).isEmpty();
    }

    @Test
    void aCappedHelperStaysCappedWhenTheCapIsLiftedMeanwhile() {
        String cap = EnforcementPermissions.maxDuration("ban");
        permissions.quota(cap, QuotaResult.limited(Duration.ofHours(1).toSeconds()));
        lane.run(ALEX, SAM, () -> ban.ban(ALEX, SAM, Optional.of("cheating"), false));
        permissions.quota(cap, QuotaResult.unlimited());

        scheduler.runAll();

        assertThat(((TempbanState.Active) records.loadTempban(SAM)).until()).isEqualTo(NOW.plus(Duration.ofHours(1)));
    }

    @Test
    void aMuteAndTheUnmuteAfterItAreStoredInThatOrderWhicheverWorkerRunsFirst() {
        scheduler.lastFirst();

        lane.run(ALEX, SAM, () -> mute.mute(ALEX, SAM, "", Optional.empty(), false));
        lane.run(ALEX, SAM, () -> unmute.unmute(ALEX, SAM));
        scheduler.runAll();

        assertThat(records.loadMute(SAM)).isEqualTo(MuteState.none());
    }

    /** The queued scheduler, asked from the thread that owns every player: the Paper main thread. */
    private static final class OwningScheduler implements Scheduler {

        private final Scheduler inner;

        OwningScheduler(Scheduler inner) {
            this.inner = inner;
        }

        @Override
        public boolean ownsEntity(PlayerRef player) {
            return true;
        }

        @Override
        public void onGlobal(Runnable task) {
            inner.onGlobal(task);
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            inner.onRegion(position, task);
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            inner.onEntity(player, task);
        }

        @Override
        public void async(Runnable task) {
            inner.async(task);
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            inner.asyncAfter(delay, task);
        }
    }

    /** A scheduler that is shutting down: it refuses every task. */
    private static final class RefusingScheduler implements Scheduler {

        @Override
        public void onGlobal(Runnable task) {
            throw new RejectedExecutionException("shutting down");
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            throw new RejectedExecutionException("shutting down");
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            throw new RejectedExecutionException("shutting down");
        }

        @Override
        public void async(Runnable task) {
            throw new RejectedExecutionException("shutting down");
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            throw new RejectedExecutionException("shutting down");
        }
    }
}
