package com.uxplima.uxmessentials.persistence.economy;

import static com.uxplima.uxmessentials.persistence.economy.EconomyTestSupport.COINS;
import static com.uxplima.uxmessentials.persistence.economy.EconomyTestSupport.CURRENCIES;
import static com.uxplima.uxmessentials.persistence.economy.EconomyTestSupport.baselineMigrations;
import static com.uxplima.uxmessentials.persistence.economy.EconomyTestSupport.coins;
import static com.uxplima.uxmessentials.persistence.economy.EconomyTestSupport.randomPlayer;
import static com.uxplima.uxmessentials.persistence.jooq.tables.EconomyCreditScores.ECONOMY_CREDIT_SCORES;
import static com.uxplima.uxmessentials.persistence.jooq.tables.Transactions.TRANSACTIONS;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Coverage of {@link JooqEconomyMaintenance} against the embedded SQLite backend with every economy migration
 * applied: telemetry trims by cutoff, an inactive owner's wallet and identity are purged in one FK-ordered
 * transaction, and an owner the economy still references (here, by a credit score) is reported as protected so
 * the task never hands them to the destructive step.
 */
class JooqEconomyMaintenanceTest {

    private Persistence persistence;
    private JooqEconomyMaintenance maintenance;
    private JooqWalletRepository wallets;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        persistence = Persistence.open(
                new EconomyTestSupport.SqliteConfig(),
                dataFolder,
                baselineMigrations(),
                new EconomyTestSupport.NoopLogger());
        maintenance = new JooqEconomyMaintenance(persistence.dsl());
        wallets = new JooqWalletRepository(persistence.dsl(), CURRENCIES, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @Test
    void prunesTelemetryOlderThanTheCutoff() {
        insertTransaction(1L, 100L);
        insertTransaction(2L, 200L);
        insertTransaction(3L, 300L);

        assertThat(maintenance.countTransactionsBefore(250L)).isEqualTo(2);
        assertThat(maintenance.deleteTransactionsBefore(250L)).isEqualTo(2);
        assertThat(maintenance.countTransactionsBefore(Long.MAX_VALUE)).isEqualTo(1); // the ts=300 row survives
    }

    @Test
    void purgesAnInactiveOwnerButReportsAReferencedOneAsProtected() {
        PlayerRef alice = randomPlayer(); // unreferenced
        PlayerRef bob = randomPlayer(); // protected by a credit score
        wallets.credit(alice, coins(100));
        wallets.credit(bob, coins(100));
        insertCreditScore(bob);

        assertThat(maintenance.allOwners()).extracting(PlayerRef::uuid).contains(alice.uuid(), bob.uuid());
        assertThat(maintenance.protectedOwners()).contains(bob.uuid()).doesNotContain(alice.uuid());

        int removed = maintenance.purgeOwners(List.of(alice.uuid()));

        assertThat(removed).isEqualTo(1);
        assertThat(wallets.findByOwner(alice)).isEmpty(); // wallet + identity gone
        assertThat(wallets.findByOwner(bob).orElseThrow().balanceOf(COINS)).isEqualTo(coins(100)); // untouched
        assertThat(maintenance.allOwners()).extracting(PlayerRef::uuid).doesNotContain(alice.uuid());
    }

    @Test
    void purgingAnEmptySetIsANoOp() {
        assertThat(maintenance.purgeOwners(List.of())).isZero();
    }

    private void insertTransaction(long id, long ts) {
        persistence
                .dsl()
                .transaction(cfg -> cfg.dsl()
                        .insertInto(TRANSACTIONS)
                        .set(TRANSACTIONS.ID, id)
                        .set(TRANSACTIONS.TS, ts)
                        .set(TRANSACTIONS.CURRENCY, "coins")
                        .set(TRANSACTIONS.AMOUNT, BigDecimal.ONE)
                        .set(TRANSACTIONS.TYPE, "CREDIT")
                        .set(TRANSACTIONS.REASON, "PAY")
                        .execute());
    }

    private void insertCreditScore(PlayerRef player) {
        persistence
                .dsl()
                .transaction(cfg -> cfg.dsl()
                        .insertInto(ECONOMY_CREDIT_SCORES)
                        .set(ECONOMY_CREDIT_SCORES.PLAYER_UUID, player.uuid().toString())
                        .set(ECONOMY_CREDIT_SCORES.SCORE, 500)
                        .set(ECONOMY_CREDIT_SCORES.LAST_UPDATED, 0L)
                        .execute());
    }
}
