package com.uxplima.uxmessentials.playerstate.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.CLOCK;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;

import com.uxplima.uxmessentials.testing.RecordingNotifier;
import org.junit.jupiter.api.Test;

class AdjustPlaytimeTest {

    private static final LocalDate TODAY = LocalDate.now(CLOCK);

    private final InMemoryPlaytime ledger = new InMemoryPlaytime();
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final AdjustPlaytime adjust = new AdjustPlaytime(ledger, notifier.notifier(), CLOCK);

    private Duration total() {
        return ledger.summaryOf(SAM.uuid(), TODAY).totalCombined();
    }

    @Test
    void setMakesTheTotalExactlyTheGivenTime() {
        ledger.addSeconds(SAM.uuid(), TODAY, 3_600, 600);

        adjust.set(ALEX, SAM, "2d");

        assertThat(total()).isEqualTo(Duration.ofDays(2));
        assertThat(notifier.sent().getLast().placeholders()).containsEntry("total", "2d 0h 0m");
    }

    @Test
    void setBelowTheCountedTimeLowersTheTotal() {
        ledger.addSeconds(SAM.uuid(), TODAY, 3_600, 7_200);

        adjust.set(ALEX, SAM, "30m");

        assertThat(total()).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void addRaisesTheTotal() {
        ledger.addSeconds(SAM.uuid(), TODAY, 3_600, 0);

        adjust.add(ALEX, SAM, "1h30m");

        assertThat(total()).isEqualTo(Duration.ofMinutes(150));
        assertThat(notifier.keys()).containsExactly("playerstate.playtime.add");
    }

    @Test
    void takeStopsAtZeroAndSaysHowMuchItTook() {
        ledger.addSeconds(SAM.uuid(), TODAY, 3_600, 0);

        adjust.take(ALEX, SAM, "5h");

        assertThat(total()).isZero();
        assertThat(notifier.sent().getLast().placeholders()).containsEntry("amount", "1h 0m");

        adjust.add(ALEX, SAM, "10m");
        assertThat(total()).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void aTimeThatCannotBeReadChangesNothing() {
        ledger.addSeconds(SAM.uuid(), TODAY, 3_600, 0);

        adjust.add(ALEX, SAM, "ten minutes");

        assertThat(total()).isEqualTo(Duration.ofHours(1));
        assertThat(notifier.keys()).containsExactly("playerstate.playtime.bad-amount");
    }

    @Test
    void aResetClearsTheCorrectionToo() {
        adjust.add(ALEX, SAM, "3d");

        ledger.reset(SAM.uuid());

        assertThat(total()).isZero();
    }
}
