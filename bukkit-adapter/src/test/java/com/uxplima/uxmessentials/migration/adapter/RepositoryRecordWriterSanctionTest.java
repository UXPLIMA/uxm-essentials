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

import com.uxplima.uxmessentials.economy.application.port.WalletRepository;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
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
import org.junit.jupiter.api.Test;

/**
 * The live writer's handling of the imported ban / IP ban / warning kinds (docs/12-migration §6). Each
 * write goes through the same {@code ModerationRepository} a live {@code /ban}, {@code /banip} or
 * {@code /warn} uses and is a keyed upsert (the warning history append-only and content-deduped), so a
 * re-run leaves exactly one sanction per key. SKIP keeps the existing sanction; a far-future tempban
 * carries a permanent ban.
 */
class RepositoryRecordWriterSanctionTest {

    private static final UUID GRIEFER = new UUID(0L, 7L);
    private static final PlayerRef GRIEFER_REF = new PlayerRef(GRIEFER, "griefer");
    private static final Instant NOW = Instant.parse("2026-06-14T00:00:00Z");
    private static final Issuer ADMIN = Issuer.console("admin");
    // The far-future span /ban writes for a permanent ban; mirrored here so the importer expresses a permanent ban.
    private static final Duration PERMANENT_SPAN = Duration.ofDays(365_000L);

    private final FakeModerationRepository moderation = new FakeModerationRepository();

    @Test
    void writesAPermanentBanAsAFarFutureTempbanRow() {
        RecordWriter writer = writer();
        TempbanState ban = TempbanState.active(NOW.plus(PERMANENT_SPAN), ADMIN, Optional.of("griefing"), NOW);

        RecordOutcome outcome =
                writer.write(new ImportRecord.BanRecord(GRIEFER_REF, ban), options(ConflictPolicy.OVERWRITE));

        assertThat(outcome).isEqualTo(RecordOutcome.WRITTEN);
        assertThat(moderation.ensured).contains(GRIEFER);
        TempbanState stored = moderation.tempbans.get(GRIEFER);
        assertThat(stored).isInstanceOf(TempbanState.Active.class);
        assertThat(stored.isActiveAt(NOW.plus(Duration.ofDays(3650)))).isTrue(); // still barred a decade out
    }

    @Test
    void writesATempBanWithItsWallClockExpiry() {
        RecordWriter writer = writer();
        Instant until = NOW.plus(Duration.ofDays(7));
        TempbanState ban = TempbanState.active(until, ADMIN, Optional.of("spam"), NOW);

        writer.write(new ImportRecord.BanRecord(GRIEFER_REF, ban), options(ConflictPolicy.OVERWRITE));

        TempbanState stored = moderation.tempbans.get(GRIEFER);
        assertThat(stored.expiry()).contains(until);
    }

    @Test
    void skipsAnExistingBanUnderTheSkipPolicy() {
        moderation.tempbans.put(
                GRIEFER, TempbanState.active(NOW.plus(Duration.ofDays(1)), ADMIN, Optional.empty(), NOW));
        RecordWriter writer = writer();
        TempbanState replacement = TempbanState.active(NOW.plus(Duration.ofDays(30)), ADMIN, Optional.empty(), NOW);

        RecordOutcome outcome =
                writer.write(new ImportRecord.BanRecord(GRIEFER_REF, replacement), options(ConflictPolicy.SKIP));

        assertThat(outcome).isEqualTo(RecordOutcome.SKIPPED);
        assertThat(moderation.tempbans.get(GRIEFER).expiry()).contains(NOW.plus(Duration.ofDays(1))); // untouched
    }

    @Test
    void writesAnIpBan() {
        RecordWriter writer = writer();
        IpBan ban =
                new IpBan("203.0.113.7", Optional.empty(), Optional.of("evasion"), Optional.of(GRIEFER), ADMIN, NOW);

        RecordOutcome outcome = writer.write(new ImportRecord.IpBanRecord(ban), options(ConflictPolicy.OVERWRITE));

        assertThat(outcome).isEqualTo(RecordOutcome.WRITTEN);
        assertThat(moderation.ipBans.get("203.0.113.7")).isEqualTo(ban);
    }

    @Test
    void skipsAnExistingIpBanUnderTheSkipPolicy() {
        IpBan existing = new IpBan("203.0.113.7", Optional.empty(), Optional.of("first"), Optional.empty(), ADMIN, NOW);
        moderation.ipBans.put("203.0.113.7", existing);
        RecordWriter writer = writer();
        IpBan replacement =
                new IpBan("203.0.113.7", Optional.empty(), Optional.of("second"), Optional.empty(), ADMIN, NOW);

        RecordOutcome outcome = writer.write(new ImportRecord.IpBanRecord(replacement), options(ConflictPolicy.SKIP));

        assertThat(outcome).isEqualTo(RecordOutcome.SKIPPED);
        assertThat(moderation.ipBans.get("203.0.113.7")).isEqualTo(existing); // untouched
    }

    @Test
    void appendsAWarning() {
        RecordWriter writer = writer();
        Warn warn = Warn.standing(ADMIN, Optional.of("rule 1"), NOW);

        RecordOutcome outcome =
                writer.write(new ImportRecord.WarnRecord(GRIEFER_REF, warn), options(ConflictPolicy.SKIP));

        assertThat(outcome).isEqualTo(RecordOutcome.WRITTEN);
        assertThat(moderation.warns.get(GRIEFER)).containsExactly(warn);
        assertThat(moderation.ensured).contains(GRIEFER);
    }

    @Test
    void doesNotDuplicateAnAlreadyImportedWarning() {
        RecordWriter writer = writer();
        Warn warn = Warn.standing(ADMIN, Optional.of("rule 1"), NOW);
        ImportRecord.WarnRecord record = new ImportRecord.WarnRecord(GRIEFER_REF, warn);

        writer.write(record, options(ConflictPolicy.SKIP));
        RecordOutcome second = writer.write(record, options(ConflictPolicy.OVERWRITE));

        assertThat(second).isEqualTo(RecordOutcome.SKIPPED);
        assertThat(moderation.warns.get(GRIEFER)).hasSize(1); // append-only, content-deduped
    }

    @Test
    void reRunningABanLeavesExactlyOneRow() {
        RecordWriter writer = writer();
        TempbanState ban = TempbanState.active(NOW.plus(Duration.ofDays(7)), ADMIN, Optional.empty(), NOW);
        ImportRecord.BanRecord record = new ImportRecord.BanRecord(GRIEFER_REF, ban);

        writer.write(record, options(ConflictPolicy.OVERWRITE));
        writer.write(record, options(ConflictPolicy.OVERWRITE));

        assertThat(moderation.tempbans).containsOnlyKeys(GRIEFER);
    }

    private RecordWriter writer() {
        return new RepositoryRecordWriter(
                mock(HomeRepository.class),
                mock(com.uxplima.uxmessentials.warps.application.port.WarpRepository.class),
                mock(WalletRepository.class),
                moderation,
                mock(KitRepository.class),
                mock(com.uxplima.uxmessentials.holograms.application.port.HologramRepository.class),
                mock(Currency.class),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static ImportOptions options(ConflictPolicy onConflict) {
        return new ImportOptions(Path.of("."), ImportMode.LIVE, onConflict, BalancePolicy.SKIP_IF_PRESENT);
    }
}
