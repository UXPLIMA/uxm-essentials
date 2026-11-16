package com.uxplima.uxmessentials.persistence.economy;

import static com.uxplima.uxmessentials.persistence.jooq.tables.Transactions.TRANSACTIONS;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.uxplima.uxmessentials.persistence.runtime.DatabaseBackend;
import com.uxplima.uxmessentials.persistence.runtime.DslContextProvider;
import com.uxplima.uxmessentials.persistence.runtime.FlywayMigrationRunner;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

/** A ledger written by the release that counted ids itself keeps its rows, and new ids start after them. */
class LedgerIdUpgradeTest {

    @TempDir
    Path folder;

    @Test
    void theFirstIdAfterTheUpgradeFollowsTheHighestOneWritten() {
        SQLiteDataSource data = new SQLiteDataSource();
        data.setUrl("jdbc:sqlite:" + folder.resolve("data.db"));
        Flyway.configure()
                .dataSource(data)
                .locations("classpath:db/migration")
                .placeholders(Map.of("prefix", ""))
                .target("106")
                .load()
                .migrate();
        DSLContext dsl = DslContextProvider.create(data, DatabaseBackend.SQLITE.dialect(), "");
        for (long id : List.of(7L, 41L)) {
            dsl.insertInto(TRANSACTIONS)
                    .set(TRANSACTIONS.ID, id)
                    .set(TRANSACTIONS.TS, 1L)
                    .set(TRANSACTIONS.TO_OWNER, "00000000-0000-0000-0000-000000000001")
                    .set(TRANSACTIONS.CURRENCY, "coins")
                    .set(TRANSACTIONS.AMOUNT, BigDecimal.ONE)
                    .set(TRANSACTIONS.TYPE, "CREDIT")
                    .set(TRANSACTIONS.REASON, "ADMIN_GIVE")
                    .execute();
        }

        new FlywayMigrationRunner(data, DatabaseBackend.SQLITE, "").migrate(List.of());

        TransactionTelemetry telemetry =
                new TransactionTelemetry(dsl, new IdleScheduler(), new SilentLogger(), Duration.ofHours(1));
        assertThat(telemetry.reserveIds(5)).isEqualTo(42L);
        assertThat(telemetry.reserveIds(1)).isEqualTo(47L);
    }

    private static final class IdleScheduler implements Scheduler {

        @Override
        public void onGlobal(Runnable task) {}

        @Override
        public void onRegion(Position position, Runnable task) {}

        @Override
        public void onEntity(PlayerRef player, Runnable task) {}

        @Override
        public void async(Runnable task) {}

        @Override
        public void asyncAfter(Duration delay, Runnable task) {}
    }

    private static final class SilentLogger implements Logger {

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
