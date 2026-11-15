package com.uxplima.uxmessentials.persistence.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.DatabaseBackend;
import com.uxplima.uxmessentials.persistence.runtime.DslContextProvider;
import com.uxplima.uxmessentials.persistence.runtime.FlywayMigrationRunner;
import com.uxplima.uxmessentials.shared.application.port.PlayerClaimLedger.Claims;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

/** The shared kit claims, cooldowns and daily reward stamps through the real migrations on a SQLite file. */
class JooqClaimLedgersTest {

    private static final UUID ALEX = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID SAM = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final long DAY = 86_400_000L;

    @TempDir
    Path folder;

    private JooqPlayerClaimLedger claims;
    private JooqDailyRewardLedger daily;

    @BeforeEach
    void open() {
        SQLiteDataSource data = new SQLiteDataSource();
        data.setUrl("jdbc:sqlite:" + folder.resolve("data.db"));
        new FlywayMigrationRunner(data, DatabaseBackend.SQLITE, "").migrate(List.of());
        DSLContext dsl = DslContextProvider.create(data, DatabaseBackend.SQLITE.dialect(), "");
        claims = new JooqPlayerClaimLedger(dsl);
        daily = new JooqDailyRewardLedger(dsl);
    }

    @Test
    void aKitClaimIsWonOnlyByTheFirstInsert() {
        assertThat(claims.claimKit(ALEX, "starter", 1_000L)).isTrue();
        assertThat(claims.claimKit(ALEX, "starter", 2_000L)).isFalse();
        assertThat(claims.claimKit(SAM, "starter", 2_000L)).isTrue();

        assertThat(claims.load(ALEX, 0L).kits()).containsExactly("starter");
    }

    @Test
    void aReleasedKitCanBeClaimedAgain() {
        claims.claimKit(ALEX, "starter", 1_000L);
        claims.claimKit(ALEX, "tools", 1_000L);

        claims.releaseKit(ALEX, "starter");
        assertThat(claims.claimKit(ALEX, "starter", 2_000L)).isTrue();

        claims.releaseAllKits(ALEX);
        assertThat(claims.load(ALEX, 0L).kits()).isEmpty();
    }

    @Test
    void theLastCooldownWriteWinsAndARunOutOneIsNotRead() {
        claims.saveCooldown(ALEX, "kit.starter", 5_000L);
        claims.saveCooldown(ALEX, "kit.starter", 3_000L);
        claims.saveCooldown(ALEX, "tp", 1_000L);

        Claims read = claims.load(ALEX, 2_000L);

        assertThat(read).isEqualTo(new Claims(Set.of(), Map.of("kit.starter", 3_000L)));
        assertThat(claims.load(ALEX, 0L).cooldowns()).doesNotContainKey("tp");
    }

    @Test
    void theDailyRewardIsTakenOncePerWindow() {
        assertThat(daily.claimIfDue(ALEX, 10 * DAY, DAY)).isTrue();
        assertThat(daily.claimIfDue(ALEX, 10 * DAY + 1, DAY)).isFalse();
        assertThat(daily.claimIfDue(ALEX, 11 * DAY, DAY)).isTrue();
        assertThat(daily.claimIfDue(SAM, 11 * DAY, DAY)).isTrue();
    }

    @Test
    void aCarriedDailyStampKeepsTheLaterOne() {
        daily.keepLatest(ALEX, 10 * DAY);
        daily.keepLatest(ALEX, 5 * DAY);

        assertThat(daily.claimIfDue(ALEX, 10 * DAY + 1, DAY)).isFalse();
        assertThat(daily.claimIfDue(ALEX, 11 * DAY, DAY)).isTrue();
    }
}
