package com.uxplima.uxmessentials.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/** The two collision knobs an operator types into the migration config. */
class ImportPolicyTest {

    @Test
    void theDefaultsKeepWhatTheServerAlreadyHas() {
        ImportOptions options = ImportOptions.live(Path.of("source"));

        assertThat(options.onConflict()).isEqualTo(ConflictPolicy.SKIP);
        assertThat(options.balancePolicy()).isEqualTo(BalancePolicy.SKIP_IF_PRESENT);
        assertThat(options.isDryRun()).isFalse();
    }

    @Test
    void aValueIsReadWhateverItsCaseAndSurroundingSpace() {
        assertThat(ConflictPolicy.parse(" Overwrite ")).contains(ConflictPolicy.OVERWRITE);
        assertThat(ConflictPolicy.parse("MERGE")).contains(ConflictPolicy.MERGE);
        assertThat(ConflictPolicy.parse("skip")).contains(ConflictPolicy.SKIP);
        assertThat(BalancePolicy.parse(" Skip-If-Present")).contains(BalancePolicy.SKIP_IF_PRESENT);
        assertThat(BalancePolicy.parse("overwrite")).contains(BalancePolicy.OVERWRITE);
    }

    @Test
    void anUnknownValueIsNotQuietlyTurnedIntoAPolicy() {
        assertThat(ConflictPolicy.parse("replace")).isEmpty();
        assertThat(ConflictPolicy.parse("")).isEmpty();
        assertThat(BalancePolicy.parse("add")).isEmpty();
        assertThat(BalancePolicy.parse("skip")).isEmpty();
    }

    @Test
    void switchingToADryRunKeepsEveryOtherDecision() {
        ImportOptions live = new ImportOptions(
                Path.of("source"), ImportMode.LIVE, ConflictPolicy.OVERWRITE, BalancePolicy.OVERWRITE);

        ImportOptions dryRun = live.asDryRun();

        assertThat(dryRun.isDryRun()).isTrue();
        assertThat(dryRun.onConflict()).isEqualTo(ConflictPolicy.OVERWRITE);
        assertThat(dryRun.balancePolicy()).isEqualTo(BalancePolicy.OVERWRITE);
        assertThat(dryRun.sourcePath()).isEqualTo(live.sourcePath());
    }
}
