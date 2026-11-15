package com.uxplima.uxmessentials.persistence.vaults;

import static com.uxplima.uxmessentials.persistence.jooq.tables.Vaults.VAULTS;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.DatabaseBackend;
import com.uxplima.uxmessentials.persistence.runtime.DslContextProvider;
import com.uxplima.uxmessentials.persistence.runtime.FlywayMigrationRunner;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vaults.domain.Vault;
import com.uxplima.uxmessentials.vaults.domain.VaultContents;
import com.uxplima.uxmessentials.vaults.domain.VaultId;
import com.uxplima.uxmessentials.vaults.domain.VaultPayment;
import com.uxplima.uxmessentials.vaults.domain.VaultSize;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

/** What a vault cost to create, stored on a database the previous release left, through the real migrations. */
class VaultPaymentStoreTest {

    private static final PlayerRef ALEX =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-00000000a1e7"), "Alex");
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final VaultPayment FIVE_COINS = new VaultPayment(new BigDecimal("5"), "coins");

    @TempDir
    Path folder;

    private SQLiteDataSource data;
    private DSLContext dsl;
    private JooqVaultRepository vaults;

    /** A database as the release before vaults recorded their payment left it. */
    @BeforeEach
    void open() {
        data = new SQLiteDataSource();
        data.setUrl("jdbc:sqlite:" + folder.resolve("data.db"));
        Flyway.configure()
                .dataSource(data)
                .locations("classpath:db/migration")
                .placeholders(Map.of("prefix", ""))
                .target("97")
                .load()
                .migrate();
        dsl = DslContextProvider.create(data, DatabaseBackend.SQLITE.dialect(), "");
        vaults = new JooqVaultRepository(dsl);
    }

    private void upgrade() {
        new FlywayMigrationRunner(data, DatabaseBackend.SQLITE, "").migrate(List.of());
    }

    private static VaultId vault(int index) {
        return VaultId.of(ALEX, index);
    }

    private Vault stored(int index) {
        return vaults.find(vault(index)).orElseThrow();
    }

    @Test
    void aVaultFromBeforeTheUpgradeHasNoRecordedPayment() {
        dsl.insertInto(VAULTS)
                .set(VAULTS.OWNER, ALEX.uuid().toString())
                .set(VAULTS.IDX, 1)
                .set(VAULTS.SIZE, (short) 3)
                .set(VAULTS.LAST_TOUCHED, NOW.toEpochMilli())
                .execute();
        upgrade();

        assertThat(stored(1).paid()).isEmpty();
        assertThat(stored(1).size()).isEqualTo(new VaultSize(3));
    }

    @Test
    void aCreatedVaultKeepsWhatItCostAndTheCurrency() {
        upgrade();

        vaults.create(Vault.allocate(vault(1), new VaultSize(1), NOW, FIVE_COINS));
        vaults.create(Vault.allocate(vault(2), new VaultSize(1), NOW));

        assertThat(stored(1).paid()).hasValueSatisfying(paid -> {
            assertThat(paid.amount()).isEqualByComparingTo("5");
            assertThat(paid.currencyId()).isEqualTo("coins");
        });
        assertThat(stored(2).paid()).isEmpty();
    }

    @Test
    void savingAVaultNeverChangesWhatItCost() {
        upgrade();
        Vault created = Vault.allocate(vault(1), new VaultSize(1), NOW, FIVE_COINS);
        vaults.create(created);

        vaults.save(created.withContents(ALEX, VaultContents.of(new byte[] {1}), NOW)
                .result());
        vaults.save(Vault.allocate(vault(1), new VaultSize(2), NOW, new VaultPayment(new BigDecimal("99"), "gems")));

        assertThat(stored(1).paid()).hasValueSatisfying(paid -> {
            assertThat(paid.amount()).isEqualByComparingTo("5");
            assertThat(paid.currencyId()).isEqualTo("coins");
        });
        assertThat(stored(1).size()).isEqualTo(new VaultSize(2));
    }

    @Test
    void aSaveThatBringsBackADeletedVaultCarriesNoPayment() {
        upgrade();
        Vault created = Vault.allocate(vault(1), new VaultSize(1), NOW, FIVE_COINS);
        vaults.create(created);

        assertThat(vaults.delete(vault(1))).isTrue();
        assertThat(vaults.delete(vault(1))).isFalse();
        vaults.save(created.withContents(ALEX, VaultContents.of(new byte[] {1}), NOW)
                .result());

        assertThat(stored(1).paid()).isEmpty();
    }

    @Test
    void creatingOverAnExistingVaultKeepsTheVaultThatWasThere() {
        upgrade();
        vaults.create(Vault.allocate(vault(1), new VaultSize(1), NOW, FIVE_COINS));
        vaults.save(stored(1)
                .withContents(ALEX, VaultContents.of(new byte[] {7}), NOW)
                .result());

        vaults.create(Vault.allocate(vault(1), new VaultSize(1), NOW, new VaultPayment(new BigDecimal("50"), "coins")));

        assertThat(stored(1).paid().orElseThrow().amount()).isEqualByComparingTo("5");
        assertThat(stored(1).contents()).isEqualTo(VaultContents.of(new byte[] {7}));
    }

    @Test
    void aPaymentFinerThanTheColumnIsStoredRoundedDown() {
        upgrade();

        vaults.create(
                Vault.allocate(vault(1), new VaultSize(1), NOW, new VaultPayment(new BigDecimal("1.23459"), "coins")));

        assertThat(stored(1).paid().orElseThrow().amount()).isEqualByComparingTo("1.2345");
    }
}
