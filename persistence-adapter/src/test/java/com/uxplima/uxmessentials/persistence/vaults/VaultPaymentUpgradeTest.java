package com.uxplima.uxmessentials.persistence.vaults;

import static com.uxplima.uxmessentials.persistence.jooq.tables.Vaults.VAULTS;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.DatabaseBackend;
import com.uxplima.uxmessentials.persistence.runtime.DslContextProvider;
import com.uxplima.uxmessentials.persistence.runtime.FlywayMigrationRunner;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vaults.application.port.VaultRepository.Columns;
import com.uxplima.uxmessentials.vaults.domain.Vault;
import com.uxplima.uxmessentials.vaults.domain.VaultContents;
import com.uxplima.uxmessentials.vaults.domain.VaultId;
import com.uxplima.uxmessentials.vaults.domain.VaultSize;
import com.uxplima.uxmessentials.vaults.domain.VaultStamp;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.SQLiteDataSource;

/** A vault the release before vaults recorded their payment wrote, read after the upgrade. */
class VaultPaymentUpgradeTest {

    private static final PlayerRef ALEX =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-00000000a1e7"), "Alex");
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

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
    void aVaultFromBeforeTheUpgradeStartsAtVersionZeroAndEveryItemWriteRaisesIt() {
        Flyway.configure()
                .dataSource(data)
                .locations("classpath:db/migration")
                .placeholders(Map.of("prefix", ""))
                .target("100")
                .load()
                .migrate();
        dsl.insertInto(VAULTS)
                .set(VAULTS.OWNER, ALEX.uuid().toString())
                .set(VAULTS.IDX, 1)
                .set(VAULTS.SIZE, (short) 1)
                .set(VAULTS.LAST_TOUCHED, NOW.toEpochMilli())
                .execute();
        upgrade();

        Vault old = stored(1);
        assertThat(old.stamp()).isEqualTo(VaultStamp.UNRECORDED);

        VaultStamp first = vaults.writeContents(
                        old.withContents(ALEX, VaultContents.of(new byte[] {1}), NOW)
                                .result(),
                        "close",
                        null)
                .orElseThrow();
        VaultStamp second = vaults.save(
                old.withContents(ALEX, VaultContents.of(new byte[] {2}), NOW).result(), "staff");

        assertThat(first).isEqualTo(new VaultStamp(null, 1L));
        assertThat(second).isEqualTo(new VaultStamp(null, 2L));
        assertThat(stored(1).stamp()).isEqualTo(second);
        assertThat(old.stamp().before(second)).isTrue();
        assertThat(vaults.update(old.renamedTo("loot"), Columns.PRESENTATION)).isTrue();
        assertThat(stored(1).stamp()).isEqualTo(second);
    }
}
