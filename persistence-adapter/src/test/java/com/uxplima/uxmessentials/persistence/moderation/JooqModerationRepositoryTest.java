package com.uxplima.uxmessentials.persistence.moderation;

import static com.uxplima.uxmessentials.persistence.jooq.tables.ModerationMutes.MODERATION_MUTES;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.domain.DrawnReason;
import com.uxplima.uxmessentials.moderation.domain.IpBan;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.ModerationMessageKey;
import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.persistence.runtime.DatabaseBackend;
import com.uxplima.uxmessentials.persistence.runtime.DslContextProvider;
import com.uxplima.uxmessentials.persistence.runtime.FlywayMigrationRunner;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

/**
 * A conditional ban, mute or IP ban write through the real migrations on a SQLite file: it lands only while the row
 * is still the one the caller read, so a sanction written by another server in between is never overwritten by a
 * decision made without it.
 */
class JooqModerationRepositoryTest {

    private static final PlayerRef ALEX =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-0000000000a1"), "Alex");
    private static final PlayerRef SAM = new PlayerRef(UUID.fromString("00000000-0000-0000-0000-0000000000a2"), "Sam");
    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");
    private static final String ADDRESS = "203.0.113.7";

    @TempDir
    Path folder;

    private ModerationRepository first;
    private ModerationRepository second;
    private DSLContext dsl;

    @BeforeEach
    void open() {
        dsl = openDsl();
        first = new JooqModerationRepository(dsl);
        second = new JooqModerationRepository(openDsl());
        first.ensureUserExists(SAM, NOW);
    }

    private static TempbanState.Active ban(Duration span, String reason, Instant at) {
        return (TempbanState.Active) TempbanState.active(at.plus(span), Issuer.of(ALEX), Optional.of(reason), at);
    }

    @Test
    void manyPlayersMutesAreReadAtOnceWithNoneForAPlayerWithoutOne() {
        first.ensureUserExists(ALEX, NOW);
        MuteState permanent = MuteState.permanent(Issuer.of(ALEX), Optional.of("slurs"), NOW);
        first.saveMute(SAM, permanent);

        assertThat(second.loadMutes(List.of(SAM, ALEX)))
                .isEqualTo(Map.of(SAM.uuid(), permanent, ALEX.uuid(), MuteState.none()));
    }

    @Test
    void aBanForAPlayerWithNoRowIsStored() {
        TempbanState.Active day = ban(Duration.ofDays(1), "grief", NOW);

        assertThat(first.replaceTempban(SAM, TempbanState.none(), day)).isTrue();

        assertThat(second.loadTempban(SAM)).isEqualTo(day);
    }

    @Test
    void aBanDecidedOnNoRowDoesNotLandOverOneWrittenSince() {
        TempbanState.Active permanent = ban(Duration.ofDays(365_000L), "cheating", NOW);
        second.saveTempban(SAM, permanent);

        assertThat(first.replaceTempban(SAM, TempbanState.none(), ban(Duration.ofDays(1), "spam", NOW)))
                .isFalse();

        assertThat(first.loadTempban(SAM)).isEqualTo(permanent);
    }

    @Test
    void aBanDecidedOnTheRowStillThereReplacesIt() {
        TempbanState.Active hour = ban(Duration.ofHours(1), "spam", NOW);
        first.saveTempban(SAM, hour);
        TempbanState.Active read = (TempbanState.Active) first.loadTempban(SAM);
        TempbanState.Active day = ban(Duration.ofDays(1), "spam again", NOW.plusSeconds(60));

        assertThat(first.replaceTempban(SAM, read, day)).isTrue();

        assertThat(second.loadTempban(SAM)).isEqualTo(day);
    }

    @Test
    void aBanDecidedOnARowThatChangedSinceDoesNotLand() {
        first.saveTempban(SAM, ban(Duration.ofHours(1), "spam", NOW));
        TempbanState.Active read = (TempbanState.Active) first.loadTempban(SAM);
        TempbanState.Active permanent = ban(Duration.ofDays(365_000L), "cheating", NOW.plusSeconds(1));
        second.saveTempban(SAM, permanent);

        assertThat(first.replaceTempban(SAM, read, ban(Duration.ofDays(1), "spam again", NOW.plusSeconds(2))))
                .isFalse();

        assertThat(first.loadTempban(SAM)).isEqualTo(permanent);
    }

    @Test
    void aReasonTooLongForTheColumnIsFittedTheSameWayOnBothPaths() {
        String essay = "x".repeat(300);
        TempbanState.Active fresh = ban(Duration.ofHours(1), essay, NOW);
        assertThat(first.replaceTempban(SAM, TempbanState.none(), fresh)).isTrue();
        TempbanState.Active inserted = (TempbanState.Active) first.loadTempban(SAM);

        assertThat(first.replaceTempban(SAM, inserted, ban(Duration.ofDays(1), essay, NOW.plusSeconds(1))))
                .isTrue();
        TempbanState.Active updated = (TempbanState.Active) first.loadTempban(SAM);

        assertThat(inserted.reason()).contains("x".repeat(256));
        assertThat(updated.reason()).contains("x".repeat(256));
    }

    @Test
    void aMuteDecidedOnNoRowIsStoredAndDoesNotLandOverOneWrittenSince() {
        MuteState hour = MuteState.timed(NOW.plus(Duration.ofHours(1)), Issuer.of(ALEX), Optional.of("spam"), NOW);
        MuteState permanent = MuteState.permanent(Issuer.of(ALEX), Optional.of("slurs"), NOW);

        assertThat(first.replaceMute(SAM, MuteState.none(), permanent)).isTrue();
        assertThat(second.replaceMute(SAM, MuteState.none(), hour)).isFalse();

        assertThat(second.loadMute(SAM)).isEqualTo(permanent);
    }

    @Test
    void aMuteDecidedOnAPermanentRowStillThereReplacesIt() {
        first.saveMute(SAM, MuteState.permanent(Issuer.of(ALEX), Optional.empty(), NOW));
        MuteState read = first.loadMute(SAM);
        MuteState day =
                MuteState.timed(NOW.plus(Duration.ofDays(1)), Issuer.of(ALEX), Optional.of("spam"), NOW.plusSeconds(5));

        assertThat(first.replaceMute(SAM, read, day)).isTrue();

        assertThat(second.loadMute(SAM)).isEqualTo(day);
    }

    @Test
    void aMuteDecidedOnARowThatChangedSinceDoesNotLand() {
        first.saveMute(SAM, MuteState.timed(NOW.plus(Duration.ofHours(1)), Issuer.of(ALEX), Optional.empty(), NOW));
        MuteState read = first.loadMute(SAM);
        MuteState permanent = MuteState.permanent(Issuer.of(ALEX), Optional.of("slurs"), NOW.plusSeconds(1));
        second.saveMute(SAM, permanent);

        assertThat(first.replaceMute(
                        SAM,
                        read,
                        MuteState.timed(NOW.plus(Duration.ofDays(1)), Issuer.of(ALEX), Optional.empty(), NOW)))
                .isFalse();

        assertThat(first.loadMute(SAM)).isEqualTo(permanent);
    }

    @Test
    void aMuteDecidedOnAnExpiredRowReplacesIt() {
        MuteState expired =
                MuteState.timed(NOW.minusSeconds(60), Issuer.of(ALEX), Optional.of("old"), NOW.minusSeconds(3600));
        first.saveMute(SAM, expired);
        MuteState read = first.loadMute(SAM);
        MuteState fresh = MuteState.timed(NOW.plus(Duration.ofHours(1)), Issuer.of(ALEX), Optional.of("new"), NOW);

        assertThat(read.isActiveAt(NOW)).isFalse();
        assertThat(first.replaceMute(SAM, MuteState.none(), fresh)).isFalse();
        assertThat(first.replaceMute(SAM, read, fresh)).isTrue();

        assertThat(second.loadMute(SAM)).isEqualTo(fresh);
    }

    @Test
    void anIpBanDecidedOnNoRowIsStoredAndDoesNotLandOverOneWrittenSince() {
        IpBan permanent = ipBan(Optional.empty(), "alts", NOW);

        assertThat(first.replaceIpBan(Optional.empty(), permanent)).isTrue();
        assertThat(second.replaceIpBan(Optional.empty(), ipBan(Optional.of(NOW.plusSeconds(60)), "spam", NOW)))
                .isFalse();

        assertThat(second.loadIpBan(ADDRESS)).contains(permanent);
    }

    @Test
    void anIpBanDecidedOnAPermanentRowStillThereReplacesIt() {
        first.saveIpBan(ipBan(Optional.empty(), "alts", NOW));
        Optional<IpBan> read = first.loadIpBan(ADDRESS);
        IpBan again = ipBan(Optional.empty(), "alts again", NOW.plusSeconds(5));

        assertThat(first.replaceIpBan(read, again)).isTrue();

        assertThat(second.loadIpBan(ADDRESS)).contains(again);
    }

    @Test
    void anIpBanDecidedOnARowThatChangedSinceDoesNotLand() {
        first.saveIpBan(ipBan(Optional.of(NOW.plusSeconds(3600)), "spam", NOW));
        Optional<IpBan> read = first.loadIpBan(ADDRESS);
        IpBan permanent = ipBan(Optional.empty(), "alts", NOW.plusSeconds(1));
        second.saveIpBan(permanent);

        assertThat(first.replaceIpBan(read, ipBan(Optional.of(NOW.plusSeconds(86_400)), "spam", NOW)))
                .isFalse();

        assertThat(first.loadIpBan(ADDRESS)).contains(permanent);
    }

    @Test
    void aLapsedIpBanIsStillTheRowAWriteIsDecidedOn() {
        IpBan lapsed = ipBan(Optional.of(NOW.minusSeconds(60)), "old", NOW.minusSeconds(3600));
        first.saveIpBan(lapsed);

        assertThat(first.activeIpBan(ADDRESS, NOW)).isEmpty();
        assertThat(first.loadIpBan(ADDRESS)).contains(lapsed);
        assertThat(first.replaceIpBan(Optional.of(lapsed), ipBan(Optional.empty(), "new", NOW)))
                .isTrue();
    }

    @Test
    void aReasonDrawnFromACatalogLineIsStoredWithItsLineOnMutesBansAndIpBans() {
        DrawnReason drawn = new DrawnReason(
                ModerationMessageKey.MOD_WARN_ESCALATION_REASON,
                Map.of("count", "3", "reason", "a=b & çok kötü %20 dil"));
        MuteState mute = MuteState.timed(
                NOW.plusSeconds(3600), Issuer.of(ALEX), Optional.of("3 uyarıya ulaştı"), NOW, Optional.of(drawn));
        TempbanState ban = TempbanState.active(
                NOW.plusSeconds(86_400), Issuer.of(ALEX), Optional.of("3 uyarıya ulaştı"), NOW, Optional.of(drawn));
        IpBan ipBan = new IpBan(
                ADDRESS,
                Optional.empty(),
                Optional.of("3 uyarıya ulaştı"),
                Optional.of(SAM.uuid()),
                Issuer.of(ALEX),
                NOW,
                Optional.of(drawn));

        first.saveMute(SAM, mute);
        first.saveTempban(SAM, ban);
        first.saveIpBan(ipBan);

        assertThat(second.loadMute(SAM)).isEqualTo(mute);
        assertThat(second.loadTempban(SAM)).isEqualTo(ban);
        assertThat(second.loadIpBan(ADDRESS)).contains(ipBan);
        assertThat(second.activeBans(NOW, 10))
                .singleElement()
                .satisfies(entry -> assertThat(entry.drawnReason()).contains(drawn));
        assertThat(second.activeMutes(NOW, 10))
                .singleElement()
                .satisfies(entry -> assertThat(entry.drawnReason()).contains(drawn));
    }

    @Test
    void aReplacedBanKeepsTheLineOfTheBanThatReplacedIt() {
        DrawnReason drawn = new DrawnReason(ModerationMessageKey.MOD_WARN_ESCALATION_REASON_BARE, Map.of("count", "5"));
        TempbanState.Active hour = ban(Duration.ofHours(1), "spam", NOW);
        first.saveTempban(SAM, hour);
        TempbanState.Active ladder = (TempbanState.Active) TempbanState.active(
                NOW.plus(Duration.ofDays(1)), Issuer.of(ALEX), Optional.of("5"), NOW, Optional.of(drawn));

        assertThat(first.replaceTempban(SAM, hour, ladder)).isTrue();

        assertThat(second.loadTempban(SAM)).isEqualTo(ladder);
    }

    @Test
    void aStoredLineThisReleaseDoesNotKnowReadsAsTheKeptText() {
        first.saveMute(SAM, MuteState.permanent(Issuer.of(ALEX), Optional.of("kept"), NOW));
        dsl.update(MODERATION_MUTES)
                .set(MODERATION_MUTES.REASON_KEY, "moderation.no-such-line")
                .set(MODERATION_MUTES.REASON_ARGS, "count=3")
                .execute();

        assertThat(second.loadMute(SAM)).isEqualTo(MuteState.permanent(Issuer.of(ALEX), Optional.of("kept"), NOW));
    }

    private static IpBan ipBan(Optional<Instant> until, String reason, Instant at) {
        return new IpBan(ADDRESS, until, Optional.of(reason), Optional.empty(), Issuer.of(ALEX), at);
    }

    private DSLContext openDsl() {
        SQLiteDataSource data = new SQLiteDataSource();
        data.setUrl("jdbc:sqlite:" + folder.resolve("data.db"));
        new FlywayMigrationRunner(data, DatabaseBackend.SQLITE, "").migrate(List.of());
        return DslContextProvider.create(data, DatabaseBackend.SQLITE.dialect(), "");
    }
}
