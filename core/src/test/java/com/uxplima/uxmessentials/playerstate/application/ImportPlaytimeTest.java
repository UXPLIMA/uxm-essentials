package com.uxplima.uxmessentials.playerstate.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.CLOCK;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;

import com.uxplima.uxmessentials.testing.RecordingNotifier;
import org.junit.jupiter.api.Test;

class ImportPlaytimeTest {

    private static final LocalDate TODAY = LocalDate.now(CLOCK);

    private final InMemoryPlaytime ledger = new InMemoryPlaytime();
    private final RecordingNotifier notifier = new RecordingNotifier();
    private final ImportPlaytime imports = new ImportPlaytime(ledger, notifier.notifier(), CLOCK);

    private Duration total() {
        return ledger.summaryOf(SAM.uuid(), TODAY).totalCombined();
    }

    @Test
    void importRaisesTheTotalToTheVanillaFigure() {
        ledger.addSeconds(SAM.uuid(), TODAY, 3_600, 0);

        imports.importFor(ALEX, SAM, Duration.ofDays(3));

        assertThat(total()).isEqualTo(Duration.ofDays(3));
        assertThat(notifier.keys()).containsExactly("playerstate.playtime.imported");
    }

    @Test
    void importNeverLowersTheTotalAndRunningItTwiceChangesNothing() {
        ledger.addSeconds(SAM.uuid(), TODAY, 7_200, 0);

        imports.importFor(ALEX, SAM, Duration.ofHours(1));
        assertThat(total()).isEqualTo(Duration.ofHours(2));

        imports.importFor(ALEX, SAM, Duration.ofHours(5));
        imports.importFor(ALEX, SAM, Duration.ofHours(5));
        assertThat(total()).isEqualTo(Duration.ofHours(5));
        assertThat(notifier.keys())
                .containsExactly(
                        "playerstate.playtime.import-nothing",
                        "playerstate.playtime.imported",
                        "playerstate.playtime.import-nothing");
    }

    @Test
    void theJoinImportRunsOncePerPlayer() {
        imports.importOnJoin(SAM, Duration.ofHours(4));
        assertThat(total()).isEqualTo(Duration.ofHours(4));

        ledger.reset(SAM.uuid());
        imports.importOnJoin(SAM, Duration.ofHours(4));

        assertThat(total()).isZero();
    }

    @Test
    void aPlayerImportedByCommandIsSkippedOnJoin() {
        imports.importFor(ALEX, SAM, Duration.ofHours(1));
        ledger.reset(SAM.uuid());

        imports.importOnJoin(SAM, Duration.ofHours(1));

        assertThat(total()).isZero();
    }
}
