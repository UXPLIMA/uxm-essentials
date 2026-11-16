package com.uxplima.uxmessentials.moderation.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.moderation.application.port.SanctionHistory.Tally;
import com.uxplima.uxmessentials.moderation.domain.PunishmentReport;
import com.uxplima.uxmessentials.moderation.domain.SanctionAction;
import com.uxplima.uxmessentials.moderation.domain.StaffPunishmentCount;
import org.junit.jupiter.api.Test;

/**
 * {@code /modstats} folds the store's counts into one line per staff member: a renamed staff member is one line under
 * their newest name, the console is one line per name, and the busiest staff member comes first.
 */
class PunishmentStatsTest {

    @Test
    void aRenamedStaffMemberIsOneLineUnderTheirNewestName() {
        PunishmentReport report = new PunishmentStats()
                .aggregate(List.of(
                        new Tally(Optional.of(ALEX.uuid()), "OldAlex", SanctionAction.BAN, 2, NOW.minusSeconds(60)),
                        new Tally(Optional.of(ALEX.uuid()), "Alex", SanctionAction.KICK, 3, NOW)));

        assertThat(report.leaderboard())
                .containsExactly(new StaffPunishmentCount(Optional.of(ALEX.uuid()), "Alex", 2, 0, 0, 3));
    }

    @Test
    void theBusiestStaffMemberComesFirstAndTheConsoleIsGroupedByName() {
        PunishmentReport report = new PunishmentStats()
                .aggregate(List.of(
                        new Tally(Optional.empty(), "CONSOLE", SanctionAction.MUTE, 1, NOW),
                        new Tally(Optional.of(ALEX.uuid()), "Alex", SanctionAction.WARN, 4, NOW),
                        new Tally(Optional.empty(), "CONSOLE", SanctionAction.WARN, 1, NOW)));

        assertThat(report.leaderboard())
                .extracting(StaffPunishmentCount::staffName, StaffPunishmentCount::total)
                .containsExactly(tuple("Alex", 4), tuple("CONSOLE", 2));
    }

    @Test
    void liftsHandedInAreNotPunishments() {
        PunishmentReport report = new PunishmentStats()
                .aggregate(List.of(new Tally(Optional.of(ALEX.uuid()), "Alex", SanctionAction.UNBAN, 5, NOW)));

        assertThat(report.isEmpty()).isTrue();
    }
}
