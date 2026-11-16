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
import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.shared.application.IpAlts;
import com.uxplima.uxmessentials.shared.application.permission.EnforcementPermissions;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaResult;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.message.SharedMessageKey;
import com.uxplima.uxmessentials.testing.FakePermissions;
import com.uxplima.uxmessentials.testing.MutableClock;
import com.uxplima.uxmessentials.testing.QueuedScheduler;
import com.uxplima.uxmessentials.testing.RecordingEvents;
import com.uxplima.uxmessentials.testing.RecordingLogger;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import org.junit.jupiter.api.Test;

/**
 * A ban asked for on the server thread is stored on the async lane, and the player is kicked only once it is stored.
 * A player who leaves before then is still banned and cannot log back in. The permission answers the sanction needs
 * are the ones read before it left the server thread, and work about one player is stored in the order it was asked.
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
    private final StorageLane lane = new StorageLane(scheduler, notifier.notifier(), permissions, log);
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
        assertThat(login.evaluate(SAM, ADDRESS, false, false).allowed()).isFalse();
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
        StorageLane closing = new StorageLane(new RefusingScheduler(), notifier.notifier(), permissions, log);

        closing.run(ALEX, SAM, () -> {});
        CompletableFuture<String> answer = closing.supply(ALEX, SAM, () -> "stored");

        assertThat(answer).isCompletedExceptionally();
        assertThat(log.errors())
                .containsExactly(
                        "event=moderation_lane_dropped actor=Alex", "event=moderation_lane_dropped actor=Alex");
    }

    @Test
    void anExemptTargetStaysExemptWhenTheLiveReadLaterSaysOtherwise() {
        permissions.grant(SAM, ModerationGuard.EXEMPT_NODE);
        lane.run(ALEX, SAM, () -> ban.ban(ALEX, SAM, Optional.of("cheating"), false));
        permissions.revoke(SAM, ModerationGuard.EXEMPT_NODE);

        scheduler.runAll();

        assertThat(records.loadTempban(SAM)).isEqualTo(TempbanState.none());
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
