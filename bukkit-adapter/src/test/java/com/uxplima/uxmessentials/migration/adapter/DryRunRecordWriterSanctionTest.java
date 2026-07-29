package com.uxplima.uxmessentials.migration.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.kits.application.port.KitRepository;
import com.uxplima.uxmessentials.migration.BalancePolicy;
import com.uxplima.uxmessentials.migration.ConflictPolicy;
import com.uxplima.uxmessentials.migration.ImportMode;
import com.uxplima.uxmessentials.migration.ImportOptions;
import com.uxplima.uxmessentials.migration.ImportRecord;
import com.uxplima.uxmessentials.migration.RecordOutcome;
import com.uxplima.uxmessentials.migration.RecordWriter;
import com.uxplima.uxmessentials.moderation.domain.IpBan;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.moderation.domain.Warn;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import org.junit.jupiter.api.Test;

/**
 * The dry-run writer reports the outcome each sanction record <em>would</em> get, reading the live
 * moderation store but never writing (docs/12-migration §1, §7). A fresh target's ban/IP ban/warn would be
 * WRITTEN; an existing one is SKIPPED under SKIP; nothing lands in the store either way.
 */
class DryRunRecordWriterSanctionTest {

    private static final UUID GRIEFER = new UUID(0L, 9L);
    private static final PlayerRef GRIEFER_REF = new PlayerRef(GRIEFER, "griefer");
    private static final Instant NOW = Instant.parse("2026-06-14T00:00:00Z");
    private static final Issuer ADMIN = Issuer.console("admin");

    private final FakeModerationRepository moderation = new FakeModerationRepository();

    @Test
    void reportsAFreshBanAsWrittenButStoresNothing() {
        RecordWriter writer = writer();
        TempbanState ban = TempbanState.active(NOW.plus(Duration.ofDays(7)), ADMIN, Optional.empty(), NOW);

        RecordOutcome outcome =
                writer.write(new ImportRecord.BanRecord(GRIEFER_REF, ban), options(ConflictPolicy.SKIP));

        assertThat(outcome).isEqualTo(RecordOutcome.WRITTEN);
        assertThat(moderation.tempbans).isEmpty();
        assertThat(moderation.ensured).isEmpty();
    }

    @Test
    void reportsAnExistingBanAsSkippedUnderSkip() {
        moderation.tempbans.put(
                GRIEFER, TempbanState.active(NOW.plus(Duration.ofDays(1)), ADMIN, Optional.empty(), NOW));
        RecordWriter writer = writer();
        TempbanState ban = TempbanState.active(NOW.plus(Duration.ofDays(30)), ADMIN, Optional.empty(), NOW);

        RecordOutcome outcome =
                writer.write(new ImportRecord.BanRecord(GRIEFER_REF, ban), options(ConflictPolicy.SKIP));

        assertThat(outcome).isEqualTo(RecordOutcome.SKIPPED);
    }

    @Test
    void reportsAFreshIpBanAsWrittenButStoresNothing() {
        RecordWriter writer = writer();
        IpBan ban = new IpBan("198.51.100.4", Optional.empty(), Optional.empty(), Optional.empty(), ADMIN, NOW);

        RecordOutcome outcome = writer.write(new ImportRecord.IpBanRecord(ban), options(ConflictPolicy.SKIP));

        assertThat(outcome).isEqualTo(RecordOutcome.WRITTEN);
        assertThat(moderation.ipBans).isEmpty();
    }

    @Test
    void reportsAFreshWarnAsWrittenButStoresNothing() {
        RecordWriter writer = writer();
        Warn warn = Warn.standing(ADMIN, Optional.of("rule 1"), NOW);

        RecordOutcome outcome =
                writer.write(new ImportRecord.WarnRecord(GRIEFER_REF, warn), options(ConflictPolicy.SKIP));

        assertThat(outcome).isEqualTo(RecordOutcome.WRITTEN);
        assertThat(moderation.warns).isEmpty();
    }

    private RecordWriter writer() {
        return new DryRunRecordWriter(
                mock(WarpRepository.class),
                moderation,
                mock(KitRepository.class),
                mock(com.uxplima.uxmessentials.holograms.application.port.HologramRepository.class),
                mock(com.uxplima.uxmessentials.worlds.application.port.WorldRepository.class),
                mock(com.uxplima.uxmessentials.migration.PlayerWarpRecordWriter.class),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static ImportOptions options(ConflictPolicy onConflict) {
        return new ImportOptions(Path.of("."), ImportMode.LIVE, onConflict, BalancePolicy.SKIP_IF_PRESENT);
    }
}
