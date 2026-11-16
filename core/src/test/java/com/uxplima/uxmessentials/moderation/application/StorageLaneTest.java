package com.uxplima.uxmessentials.moderation.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import com.uxplima.uxmessentials.moderation.application.port.SanctionSync;
import com.uxplima.uxmessentials.moderation.domain.AddressStrictness;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.shared.application.IpAlts;
import com.uxplima.uxmessentials.shared.domain.message.SharedMessageKey;
import com.uxplima.uxmessentials.testing.FakePermissions;
import com.uxplima.uxmessentials.testing.MutableClock;
import com.uxplima.uxmessentials.testing.QueuedScheduler;
import com.uxplima.uxmessentials.testing.RecordingEvents;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import org.junit.jupiter.api.Test;

/**
 * A ban asked for on the server thread is stored on the async lane, and the player is kicked only once it is stored.
 * A player who leaves before then is still banned and cannot log back in.
 */
class StorageLaneTest {

    private static final String ADDRESS = "198.51.100.9";

    private final MutableClock clock = new MutableClock(NOW);
    private final QueuedScheduler scheduler = new QueuedScheduler();
    private final SanctionRecords records = new SanctionRecords();
    private final OnlinePlayers online = new OnlinePlayers().connect(SAM, ADDRESS);
    private final FakePermissions permissions = new FakePermissions();
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final StorageLane lane = new StorageLane(scheduler, notifier.notifier());
    private final Ban ban = new Ban(
            records,
            online,
            new ModerationGuard(permissions),
            notifier.notifier(),
            new SilentAudit(),
            new RecordingEvents(),
            new SanctionHistoryRecorder(new DiscardedHistory(), clock),
            new SanctionDurationLimit(permissions),
            (key, placeholders) -> {},
            SanctionSync.NONE,
            AddressStrictness.NORMAL,
            new NoIpHistory(),
            clock);
    private final LoginEnforcement login = new LoginEnforcement(
            records, new IpAlts(new NoIpHistory(), address -> address), notifier.notifier(), new SilentAudit(), clock);

    @Test
    void nothingIsStoredOrKickedUntilTheLaneRunsAndTheKickFollowsTheWrite() {
        lane.run(ALEX, () -> ban.ban(ALEX, SAM, Optional.of("cheating"), false));

        assertThat(records.loadTempban(SAM)).isEqualTo(TempbanState.none());
        assertThat(online.kicked()).isEmpty();
        assertThat(notifier.sent()).isEmpty();

        scheduler.runAll();

        assertThat(records.loadTempban(SAM)).isInstanceOf(TempbanState.Active.class);
        assertThat(online.kicked()).containsOnlyKeys(SAM);
    }

    @Test
    void aPlayerWhoLeavesBeforeTheBanIsStoredIsBannedAllTheSame() {
        lane.run(ALEX, () -> ban.ban(ALEX, SAM, Optional.of("cheating"), false));
        online.leave(SAM);

        scheduler.runAll();

        assertThat(online.kicked()).isEmpty();
        assertThat(login.evaluate(SAM, ADDRESS, false, false).allowed()).isFalse();
    }

    @Test
    void workThatFailsTellsTheStaffMemberAndIsReportedToTheScheduler() {
        lane.run(ALEX, () -> {
            throw new IllegalStateException("the database is down");
        });

        assertThatThrownBy(scheduler::runAll).isInstanceOf(IllegalStateException.class);

        assertThat(notifier.keys()).containsExactly(SharedMessageKey.COMMAND_ERROR.key());
    }
}
