package com.uxplima.uxmessentials.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.migration.convert.SourceId;
import com.uxplima.uxmessentials.moderation.domain.IpBan;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.moderation.domain.Warn;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/**
 * The summary's tally of the ban / IP ban / warning kinds (docs/12-migration §9). Each kind is one record
 * counted on its own counter and into the total, and the {@code describe} finish line carries a field for
 * each so an operator reads them independently.
 */
class ImportSummarySanctionTest {

    private static final PlayerRef GRIEFER = new PlayerRef(new UUID(0L, 11L), "griefer");
    private static final Instant NOW = Instant.parse("2026-06-14T00:00:00Z");
    private static final Issuer ADMIN = Issuer.console("admin");

    @Test
    void countsEachNewSanctionKindOnItsOwnCounter() {
        ImportSummary summary = new ImportSummary(SourceId.of("litebans"), ImportMode.LIVE);

        summary.record(banRecord(), RecordOutcome.WRITTEN);
        summary.record(ipBanRecord(), RecordOutcome.WRITTEN);
        summary.record(warnRecord(), RecordOutcome.WRITTEN);

        assertThat(summary.bans()).isEqualTo(1);
        assertThat(summary.ipBans()).isEqualTo(1);
        assertThat(summary.warns()).isEqualTo(1);
        assertThat(summary.total()).isEqualTo(3);
    }

    @Test
    void rendersTheNewCountersOnTheFinishLine() {
        ImportSummary summary = new ImportSummary(SourceId.of("litebans"), ImportMode.LIVE);
        summary.record(banRecord(), RecordOutcome.WRITTEN);
        summary.record(ipBanRecord(), RecordOutcome.WRITTEN);
        summary.record(warnRecord(), RecordOutcome.WRITTEN);

        String line = summary.describe(Duration.ZERO);

        assertThat(line).contains("bans=1").contains("ip_bans=1").contains("warns=1");
    }

    @Test
    void aSkippedSanctionIsStillCountedByKindButAlsoCountedSkipped() {
        ImportSummary summary = new ImportSummary(SourceId.of("litebans"), ImportMode.LIVE);

        summary.record(banRecord(), RecordOutcome.SKIPPED);

        assertThat(summary.bans()).isEqualTo(1);
        assertThat(summary.skipped()).isEqualTo(1);
    }

    private static ImportRecord.BanRecord banRecord() {
        return new ImportRecord.BanRecord(
                GRIEFER, TempbanState.active(NOW.plus(Duration.ofDays(7)), ADMIN, Optional.empty(), NOW));
    }

    private static ImportRecord.IpBanRecord ipBanRecord() {
        return new ImportRecord.IpBanRecord(
                new IpBan("203.0.113.7", Optional.empty(), Optional.empty(), Optional.empty(), ADMIN, NOW));
    }

    private static ImportRecord.WarnRecord warnRecord() {
        return new ImportRecord.WarnRecord(GRIEFER, Warn.standing(ADMIN, Optional.of("rule 1"), NOW));
    }
}
