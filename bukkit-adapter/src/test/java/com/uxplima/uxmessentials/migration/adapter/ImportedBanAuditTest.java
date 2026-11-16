package com.uxplima.uxmessentials.migration.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.migration.ImportRecord;
import com.uxplima.uxmessentials.migration.RecordOutcome;
import com.uxplima.uxmessentials.migration.RecordResult;
import com.uxplima.uxmessentials.migration.convert.SourceId;
import com.uxplima.uxmessentials.moderation.application.Ban;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/** The audit line of an imported ban says {@code until=permanent} for a permanent ban, as it does for a mute or jail. */
class ImportedBanAuditTest {

    private static final Instant ISSUED = Instant.parse("2024-05-01T10:00:00Z");
    private static final PlayerRef SAM = new PlayerRef(UUID.fromString("00000000-0000-0000-0000-000000000005"), "Sam");

    private final Lines lines = new Lines();
    private final AuditChannelMigrationAudit audit = new AuditChannelMigrationAudit(lines);

    private void imported(Instant until) {
        TempbanState ban = TempbanState.active(until, Issuer.console("Console"), Optional.empty(), ISSUED);
        audit.record(
                SourceId.of("litebans"), new ImportRecord.BanRecord(SAM, ban), RecordResult.of(RecordOutcome.WRITTEN));
    }

    @Test
    void aPermanentBanIsLoggedAsPermanent() {
        imported(ISSUED.plus(Ban.PERMANENT_SPAN));

        assertThat(lines.only()).contains("permanent");
    }

    @Test
    void aTimedBanIsLoggedWithItsEnd() {
        Instant end = ISSUED.plus(Duration.ofDays(7));

        imported(end);

        assertThat(lines.only()).contains(end.toString()).doesNotContain("permanent");
    }

    /** Keeps the arguments of every info line. */
    private static final class Lines implements Logger {

        private final List<List<Object>> info = new ArrayList<>();

        List<Object> only() {
            assertThat(info).hasSize(1);
            return info.getFirst();
        }

        @Override
        public void info(String message, Object... args) {
            info.add(Arrays.asList(args));
        }

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
