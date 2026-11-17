package com.uxplima.uxmessentials.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.migration.convert.SourceId;
import com.uxplima.uxmessentials.migration.convert.essentialsx.EssentialsXConvert;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/**
 * The EssentialsX fixture tree run end to end through the import use case: the backup, the audit lines, the summary,
 * and what a dry run and a failing record do to the run.
 */
class ImportDataTest {

    private static final Path SOURCE = Fixtures.path("/essentialsx");
    private static final Currency COINS =
            Currency.builder(CurrencyId.of("coins")).build();

    private final RecordingAudit audit = new RecordingAudit();
    private final AtomicInteger backups = new AtomicInteger();
    private final ImportData importData = new ImportData(
            audit,
            () -> {
                backups.incrementAndGet();
                return "backup-1";
            },
            new RecordingLogger());
    private final EssentialsXConvert essentialsx = new EssentialsXConvert(Fixtures.ONLY_WORLD, new RecordingLogger());

    @Test
    void aDryRunTakesNoBackupWritesNothingAndCountsWhatALiveRunWould() {
        InMemoryHomes homes = new InMemoryHomes();
        InMemoryWallets wallets = new InMemoryWallets();
        InMemoryMail mail = new InMemoryMail();
        InMemoryKitClaims kitClaims = new InMemoryKitClaims();
        UserRecordWriter users = new UserRecordWriter(
                homes,
                wallets,
                Optional.of(mail),
                kitClaims,
                Duration.ZERO,
                text -> text,
                COINS,
                Clock.fixed(Instant.parse("2025-06-01T12:00:00Z"), ZoneOffset.UTC));
        RecordWriter dryRun = (rec, options) -> rec instanceof ImportRecord.UserRecord user
                ? users.preview(user.user(), options)
                : RecordResult.of(RecordOutcome.WRITTEN);

        ImportSummary summary = importData.run(
                "console", essentialsx, ImportOptions.live(SOURCE).asDryRun(), dryRun);

        assertThat(backups).hasValue(0);
        assertThat(audit.starts).containsExactly("console essentialsx DRY_RUN dry-run");
        assertThat(summary.dryRun()).isTrue();
        assertThat(homes.all()).isEmpty();
        assertThat(mail.all()).isEmpty();
        assertThat(wallets.findByOwner(new PlayerRef(EssentialsXFixture.STEVE, "Steve")))
                .isEmpty();
        assertThat(kitClaims.of(new PlayerRef(EssentialsXFixture.STEVE, "Steve")))
                .isEmpty();
        assertSummaryOfTheFixture(summary);
    }

    @Test
    void aLiveRunTakesTheBackupBeforeItWritesAnythingAndAuditsEveryRecord() {
        List<String> order = Collections.synchronizedList(new ArrayList<>());
        ImportData withOrderedBackup = new ImportData(
                audit,
                () -> {
                    order.add("backup");
                    return "backup-1";
                },
                new RecordingLogger());

        ImportSummary summary = withOrderedBackup.run("console", essentialsx, ImportOptions.live(SOURCE), (rec, o) -> {
            order.add("write");
            return RecordResult.of(RecordOutcome.WRITTEN);
        });

        assertThat(order).first().isEqualTo("backup");
        assertThat(order).filteredOn("write"::equals).hasSize((int) summary.total());
        assertThat(audit.starts).containsExactly("console essentialsx LIVE backup-1");
        assertThat(audit.records).hasSize((int) summary.total());
        assertThat(audit.finishes).isEqualTo(1);
        assertSummaryOfTheFixture(summary);
    }

    @Test
    void aRecordThatFailsToWriteIsCountedAndTheRestOfTheRunStillLands() {
        List<ImportRecord> written = Collections.synchronizedList(new ArrayList<>());

        ImportSummary summary = importData.run("console", essentialsx, ImportOptions.live(SOURCE), (rec, options) -> {
            if (rec instanceof ImportRecord.WarpRecord) {
                throw new IllegalStateException("database is down for warps");
            }
            written.add(rec);
            return RecordResult.of(RecordOutcome.WRITTEN);
        });

        assertThat(summary.failed()).isEqualTo(summary.warps());
        assertThat(written).hasSize((int) (summary.total() - summary.warps()));
        assertThat(audit.records).filteredOn(line -> line.endsWith("failed")).hasSize((int) summary.warps());
    }

    /**
     * Four named accounts, three readable players (two with sanctions), three warps in a known world and five
     * importable kits.
     */
    private static void assertSummaryOfTheFixture(ImportSummary summary) {
        assertThat(summary.source()).isEqualTo(SourceId.of("essentialsx"));
        assertThat(summary.accounts()).isEqualTo(4);
        assertThat(summary.users()).isEqualTo(3);
        assertThat(summary.mutes()).isEqualTo(2);
        assertThat(summary.jails()).isEqualTo(1);
        assertThat(summary.warps()).isEqualTo(3);
        assertThat(summary.kits()).isEqualTo(5);
        assertThat(summary.total()).isEqualTo(17);
    }

    private static final class RecordingAudit implements MigrationAudit {

        private final List<String> starts = Collections.synchronizedList(new ArrayList<>());
        private final List<String> records = Collections.synchronizedList(new ArrayList<>());
        private int finishes;

        @Override
        public void start(String actor, SourceId source, ImportMode mode, String sourcePath, String backup) {
            starts.add(actor + " " + source + " " + mode + " " + backup);
        }

        @Override
        public void record(SourceId source, ImportRecord rec, RecordResult result) {
            records.add(rec.kind() + " " + result.outcome().token());
        }

        @Override
        public synchronized void finish(String actor, ImportSummary summary, Duration elapsed) {
            finishes++;
        }
    }
}
