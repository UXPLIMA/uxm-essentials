package com.uxplima.uxmessentials.moderation.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.uxplima.uxmessentials.moderation.domain.ModerationMessageKey;
import com.uxplima.uxmessentials.testing.MutableClock;
import com.uxplima.uxmessentials.testing.RecordingEvents;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import org.junit.jupiter.api.Test;

/** A rollback reads at most {@link StaffRollback#MAX_LIMIT} history rows, and says so when it was asked for more. */
class StaffRollbackTest {

    private final MutableClock clock = new MutableClock(NOW);
    private final SanctionRecords records = new SanctionRecords();
    private final RecordedHistory history = new RecordedHistory();
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final StaffRollback rollback;

    StaffRollbackTest() {
        SanctionHistoryRecorder recorder = new SanctionHistoryRecorder(history, clock);
        SilentAudit audit = new SilentAudit();
        this.rollback = new StaffRollback(
                history,
                records,
                new Unban(records, notifier.notifier(), audit, recorder),
                new Unmute(records, notifier.notifier(), audit, new RecordingEvents(), recorder, clock),
                notifier.notifier(),
                audit,
                clock);
    }

    @Test
    void aLimitAboveTheMostIsLoweredAndTheStaffMemberIsTold() {
        rollback.rollback(ALEX, SAM, 2_000);

        assertThat(history.revocableReadLimits()).containsExactly(StaffRollback.MAX_LIMIT);
        assertThat(notifier.sent())
                .contains(new RecordingNotifier.Sent(
                        ALEX,
                        ModerationMessageKey.MOD_STAFFROLLBACK_CAPPED.key(),
                        Map.of("requested", "2000", "limit", "500")));
    }

    @Test
    void aLimitWithinTheMostIsReadAsGivenWithoutANotice() {
        rollback.rollback(ALEX, SAM, StaffRollback.MAX_LIMIT);

        assertThat(history.revocableReadLimits()).containsExactly(StaffRollback.MAX_LIMIT);
        assertThat(notifier.keys()).containsExactly(ModerationMessageKey.MOD_STAFFROLLBACK_NONE.key());
    }
}
