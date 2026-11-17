package com.uxplima.uxmessentials.migration.convert.litebans;

import static com.uxplima.uxmessentials.migration.Fixtures.only;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.migration.FixtureDatabases;
import com.uxplima.uxmessentials.migration.Fixtures;
import com.uxplima.uxmessentials.migration.ImportRecord;
import com.uxplima.uxmessentials.migration.RecordingLogger;
import com.uxplima.uxmessentials.migration.convert.litebans.parse.LiteBansTables;
import com.uxplima.uxmessentials.moderation.domain.IpBan;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.moderation.domain.Warn;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A LiteBans H2 database where LiteBans keeps it, read by the source the import runs: only the punishments LiteBans
 * still enforces on a real account come out, each with the issuer, reason and times LiteBans recorded.
 */
class LiteBansImportTest {

    private static final Instant ISSUED = Instant.ofEpochMilli(1_700_000_000_000L);
    private static final UUID GRIEFER = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID SPAMMER = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID ALT = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID PANEL_TARGET = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final Issuer MODERATOR =
            Issuer.stored(Optional.of(UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")), "Moderator");
    private static final Issuer CONSOLE = Issuer.console("Console");

    @TempDir
    Path plugins;

    private final RecordingLogger log = new RecordingLogger();

    private List<ImportRecord> importFrom(String script) {
        FixtureDatabases.h2(script, plugins.resolve("LiteBans").resolve("litebans.mv.db"));
        LiteBansConvert source = new LiteBansConvert(
                new LiteBansConfig(Optional.empty(), "", "", "litebans_", Optional.of(plugins)), log);
        return Fixtures.records(source, plugins);
    }

    @Test
    void aPermanentBanIsTheFarOffTempbanALiveBanWritesAndATempBanKeepsItsExpiry() {
        List<ImportRecord.BanRecord> bans = only(importFrom("/litebans/litebans.sql"), ImportRecord.BanRecord.class);

        assertThat(bans)
                .extracting(ImportRecord.BanRecord::target, ImportRecord.BanRecord::ban)
                .contains(
                        tuple(
                                new PlayerRef(GRIEFER, "Griefer"),
                                TempbanState.active(
                                        ISSUED.plus(Duration.ofDays(365_000L)),
                                        MODERATOR,
                                        Optional.of("griefing"),
                                        ISSUED)),
                        tuple(
                                new PlayerRef(SPAMMER, ".Spammer"),
                                TempbanState.active(
                                        Instant.ofEpochMilli(1_800_000_000_000L),
                                        CONSOLE,
                                        Optional.of("spam"),
                                        ISSUED)));
    }

    @Test
    void everyAccountTheLoginHistoryKnowsIsRecordedUnderItsLatestNameAndWhatIsNoNameIsLeftOut() {
        assertThat(only(importFrom("/litebans/litebans.sql"), ImportRecord.AccountRecord.class))
                .extracting(
                        account -> account.account().uuid(),
                        account -> account.account().name())
                .containsExactlyInAnyOrder(tuple(GRIEFER, "Griefer"), tuple(SPAMMER, ".Spammer"));
    }

    @Test
    void aLiftedBanAndABanOnNoRealAccountAreLeftOut() {
        List<ImportRecord.BanRecord> bans = only(importFrom("/litebans/litebans.sql"), ImportRecord.BanRecord.class);

        assertThat(bans)
                .extracting(ban -> ban.target().uuid())
                .containsExactlyInAnyOrder(GRIEFER, SPAMMER, PANEL_TARGET);
    }

    @Test
    void anIssuerThatIsNeitherTheConsoleNorAnAccountIsKeptByNameAndABlankReasonIsNoReason() {
        ImportRecord.BanRecord panel = only(importFrom("/litebans/litebans.sql"), ImportRecord.BanRecord.class).stream()
                .filter(ban -> ban.target().uuid().equals(PANEL_TARGET))
                .findFirst()
                .orElseThrow();

        assertThat(panel.ban())
                .isEqualTo(TempbanState.active(
                        Instant.ofEpochMilli(1_800_000_000_000L),
                        Issuer.console("WebPanel"),
                        Optional.empty(),
                        ISSUED));
    }

    @Test
    void anIpBanIsKeyedByItsAddressAndAWildcardRangeIsLeftOut() {
        assertThat(only(importFrom("/litebans/litebans.sql"), ImportRecord.IpBanRecord.class))
                .extracting(ImportRecord.IpBanRecord::ban)
                .containsExactly(new IpBan(
                        "10.0.0.5",
                        Optional.empty(),
                        Optional.of("alts"),
                        Optional.of(ALT),
                        Issuer.console("LiteBans"),
                        ISSUED));
    }

    @Test
    void onlyTheMuteLiteBansStillEnforcesIsImported() {
        assertThat(only(importFrom("/litebans/litebans.sql"), ImportRecord.ModerationRecord.class))
                .singleElement()
                .satisfies(rec -> {
                    assertThat(rec.moderation().hasMute()).isTrue();
                    assertThat(rec.moderation().hasJail()).isFalse();
                    assertThat(rec.moderation().profile().target().uuid()).isEqualTo(SPAMMER);
                    assertThat(rec.moderation().profile().mute())
                            .isEqualTo(MuteState.permanent(
                                    MODERATOR, Optional.of("toxicity"), Instant.ofEpochMilli(1_700_000_100_000L)));
                });
    }

    @Test
    void aWarningStandsOrLapsesAsLiteBansHadIt() {
        assertThat(only(importFrom("/litebans/litebans.sql"), ImportRecord.WarnRecord.class))
                .extracting(ImportRecord.WarnRecord::warn)
                .containsExactly(
                        Warn.standing(CONSOLE, Optional.of("language"), Instant.ofEpochMilli(1_700_000_200_000L)),
                        Warn.timed(
                                CONSOLE,
                                Optional.of("language again"),
                                Instant.ofEpochMilli(1_700_000_300_000L),
                                Instant.ofEpochMilli(1_700_086_700_000L)));
    }

    @Test
    void aMissingTableIsReportedAndTheOtherTablesStillImport() {
        List<ImportRecord> records = importFrom("/litebans/litebans-no-warnings.sql");

        assertThat(only(records, ImportRecord.BanRecord.class)).hasSize(1);
        assertThat(only(records, ImportRecord.WarnRecord.class)).isEmpty();
        assertThat(log.warnings()).anySatisfy(line -> assertThat(line).contains("LiteBans query failed"));
    }

    @Test
    void withNoDatabaseConfiguredOrFoundNothingIsImportedAndTheOperatorIsTold() {
        LiteBansConvert source =
                new LiteBansConvert(new LiteBansConfig(Optional.empty(), "", "", "", Optional.of(plugins)), log);

        assertThat(source.detect(plugins)).isFalse();
        assertThat(Fixtures.records(source, plugins)).isEmpty();
        assertThat(log.warnings()).anySatisfy(line -> assertThat(line).contains("no jdbc-url is configured"));
    }

    @Test
    void aTablePrefixThatCouldCarrySqlIsRefusedBeforeAnyQuery() {
        assertThatThrownBy(() -> new LiteBansTables("litebans_; DROP TABLE x; --"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new LiteBansTables(" custom_ ").selectBans()).endsWith("FROM custom_bans");
    }
}
