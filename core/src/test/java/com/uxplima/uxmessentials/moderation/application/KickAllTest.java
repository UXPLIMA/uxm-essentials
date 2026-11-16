package com.uxplima.uxmessentials.moderation.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.domain.SanctionAction;
import com.uxplima.uxmessentials.moderation.domain.SanctionHistoryEntry;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.testing.FakePermissions;
import com.uxplima.uxmessentials.testing.MutableClock;
import com.uxplima.uxmessentials.testing.RecordingNotifier;
import org.junit.jupiter.api.Test;

/** {@code /kickall} writes the same history row {@code /kick} does for every player it kicks, and for nobody else. */
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
    private final KickAll kickAll = new KickAll(
            online,
            new ModerationGuard(permissions),
            new RecordingNotifier().notifier(),
            new SilentAudit(),
            new SanctionHistoryRecorder(history, clock));

    @Test
    void everyKickedPlayerHasAKickInTheirHistory() {
        assertThat(kickAll.kickAll(ALEX, Optional.of("restart"))).isEqualTo(2);

        assertThat(history.recentForTarget(SAM.uuid(), 20)).singleElement().satisfies(row -> isKickByAlex(row));
        assertThat(history.recentForTarget(ROBIN.uuid(), 20)).singleElement().satisfies(row -> isKickByAlex(row));
    }

    @Test
    void theStaffMemberAndExemptPlayersStayOnlineAndOutOfTheHistory() {
        kickAll.kickAll(ALEX, Optional.empty());

        assertThat(online.kicked()).containsOnlyKeys(SAM, ROBIN);
        assertThat(history.recentForTarget(ALEX.uuid(), 20)).isEmpty();
        assertThat(history.recentForTarget(KAI.uuid(), 20)).isEmpty();
    }

    private static void isKickByAlex(SanctionHistoryEntry row) {
        assertThat(row.action()).isEqualTo(SanctionAction.KICK);
        assertThat(row.actor().uuid()).contains(ALEX.uuid());
        assertThat(row.reason()).contains("restart");
        assertThat(row.at()).isEqualTo(NOW);
    }
}
