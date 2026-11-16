package com.uxplima.uxmessentials.moderation.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.NOW;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.domain.PunishmentReport;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.testing.MutableClock;
import org.junit.jupiter.api.Test;

/**
 * {@code /modstats} counts what a staff member did, not how many rows it wrote: a STRICT ban is one ban however many
 * addresses it reached, and a {@code /kickall} is one kick however many players it cleared.
 */
class PunishmentStatsTest {

    private static final PlayerRef ROBIN =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-0000000000b0"), "Robin");
    private static final PlayerRef KAI = new PlayerRef(UUID.fromString("00000000-0000-0000-0000-0000000000c0"), "Kai");

    private final MutableClock clock = new MutableClock(NOW);
    private final RecordedHistory history = new RecordedHistory();
    private final SanctionHistoryRecorder recorder = new SanctionHistoryRecorder(history, clock);

    @Test
    void aBanAndTheAddressesItReachedCountAsOneBan() {
        recorder.ban(ALEX, SAM, Optional.empty(), Optional.empty());
        recorder.banIp(ALEX, Optional.of(SAM.uuid()), "198.51.100.9", Optional.empty(), Optional.empty());
        recorder.banIp(ALEX, Optional.of(SAM.uuid()), "203.0.113.4", Optional.empty(), Optional.empty());

        assertThat(report().totalBans()).isEqualTo(1);
    }

    @Test
    void aKickallIsOneKickAndSeparateKicksEachCount() {
        recorder.kicks(ALEX, List.of(SAM, ROBIN, KAI), Optional.of("restart"));
        clock.advance(Duration.ofMinutes(1));
        recorder.kick(ALEX, SAM, Optional.empty());
        clock.advance(Duration.ofMinutes(1));
        recorder.kick(ALEX, ROBIN, Optional.empty());

        assertThat(report().totalKicks()).isEqualTo(3);
    }

    @Test
    void kickallsOfTwoStaffMembersAtOneInstantCountForEach() {
        recorder.kicks(ALEX, List.of(SAM), Optional.empty());
        recorder.kicks(ROBIN, List.of(KAI), Optional.empty());

        assertThat(report().totalKicks()).isEqualTo(2);
    }

    private PunishmentReport report() {
        return new PunishmentStats().aggregate(history.allSince(Instant.EPOCH, 1_000), Optional.empty());
    }
}
