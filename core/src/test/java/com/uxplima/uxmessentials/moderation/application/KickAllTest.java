package com.uxplima.uxmessentials.moderation.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.domain.ModerationMessageKey;
import com.uxplima.uxmessentials.moderation.domain.SanctionAction;
import com.uxplima.uxmessentials.moderation.domain.SanctionHistoryEntry;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.testing.FakePermissions;
import com.uxplima.uxmessentials.testing.MutableClock;
import com.uxplima.uxmessentials.testing.QueuedScheduler;
import com.uxplima.uxmessentials.testing.RecordingLogger;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import org.junit.jupiter.api.Test;

/**
 * {@code /kickall} writes the same history row {@code /kick} does for every player it kicks, and for nobody else.
 * The kicks, the audit line and the answer never wait on the history: it is written afterwards in one batch, and a
 * history that cannot be written changes nothing about who was kicked.
 */
class KickAllTest {

    private static final PlayerRef ROBIN =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-0000000000b0"), "Robin");
    private static final PlayerRef KAI = new PlayerRef(UUID.fromString("00000000-0000-0000-0000-0000000000c0"), "Kai");

    private final MutableClock clock = new MutableClock(NOW);
    private final FakePermissions permissions = new FakePermissions().grant(KAI, ModerationGuard.EXEMPT_NODE);
    private final OnlinePlayers online = new OnlinePlayers()
            .connect(ALEX, "198.51.100.1")
            .connect(SAM, "198.51.100.2")
            .connect(ROBIN, "198.51.100.3")
            .connect(KAI, "198.51.100.4");
    private final RecordedHistory history = new RecordedHistory();
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final QueuedScheduler scheduler = new QueuedScheduler();
    private final RecordingLogger log = new RecordingLogger();
    private final List<Integer> auditedCounts = new ArrayList<>();
    private final KickAll kickAll = new KickAll(
            online,
            new ModerationGuard(permissions),
            notifier.notifier(),
            new SilentAudit() {
                @Override
                public void kickedAll(PlayerRef actor, int affected, Optional<String> reason) {
                    auditedCounts.add(affected);
                }
            },
            new SanctionHistoryRecorder(history, clock),
            scheduler,
            log);

    @Test
    void everyKickedPlayerHasAKickInTheirHistoryWrittenAsOneBatch() {
        assertThat(kickAll.kickAll(ALEX, Optional.of("restart"))).isEqualTo(2);
        scheduler.runAll();

        assertThat(history.batches()).isEqualTo(1);
        assertThat(history.recentForTarget(SAM.uuid(), 20)).singleElement().satisfies(KickAllTest::isKickByAlex);
        assertThat(history.recentForTarget(ROBIN.uuid(), 20)).singleElement().satisfies(KickAllTest::isKickByAlex);
    }

    @Test
    void theStaffMemberAndExemptPlayersStayOnlineAndOutOfTheHistory() {
        kickAll.kickAll(ALEX, Optional.empty());
        scheduler.runAll();

        assertThat(online.kicked()).containsOnlyKeys(SAM, ROBIN);
        assertThat(history.recentForTarget(ALEX.uuid(), 20)).isEmpty();
        assertThat(history.recentForTarget(KAI.uuid(), 20)).isEmpty();
    }

    @Test
    void everyoneIsKickedAndTheStaffMemberAnsweredBeforeTheHistoryIsWritten() {
        kickAll.kickAll(ALEX, Optional.empty());

        assertThat(online.kicked()).containsOnlyKeys(SAM, ROBIN);
        assertThat(auditedCounts).containsExactly(2);
        assertThat(notifier.sent())
                .contains(new RecordingNotifier.Sent(
                        ALEX, ModerationMessageKey.KICKALL_APPLIED.key(), Map.of("count", "2")));
        assertThat(history.entries()).isEmpty();
    }

    @Test
    void aHistoryThatCannotBeWrittenIsLoggedAndChangesNothingElse() {
        history.goDown();

        kickAll.kickAll(ALEX, Optional.empty());
        scheduler.runAll();

        assertThat(online.kicked()).containsOnlyKeys(SAM, ROBIN);
        assertThat(auditedCounts).containsExactly(2);
        assertThat(notifier.keys()).contains(ModerationMessageKey.KICKALL_APPLIED.key());
        assertThat(log.errors()).singleElement().asString().startsWith("event=kickall_history_failed");
    }

    @Test
    void nobodyToKickWritesNoHistory() {
        OnlinePlayers alone = new OnlinePlayers().connect(ALEX, "198.51.100.1");
        new KickAll(
                        alone,
                        new ModerationGuard(permissions),
                        notifier.notifier(),
                        new SilentAudit(),
                        new SanctionHistoryRecorder(history, clock),
                        scheduler,
                        log)
                .kickAll(ALEX, Optional.empty());
        scheduler.runAll();

        assertThat(history.batches()).isZero();
    }

    private static void isKickByAlex(SanctionHistoryEntry row) {
        assertThat(row.action()).isEqualTo(SanctionAction.KICK);
        assertThat(row.actor().uuid()).contains(ALEX.uuid());
        assertThat(row.reason()).contains("restart");
        assertThat(row.at()).isEqualTo(NOW);
    }
}
