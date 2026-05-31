package com.uxplima.uxmessentials.persistence.vaults;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vaults.domain.Vault;
import com.uxplima.uxmessentials.vaults.domain.VaultContents;
import com.uxplima.uxmessentials.vaults.domain.VaultId;
import com.uxplima.uxmessentials.vaults.domain.VaultSize;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end coverage of {@link JooqVaultRepository} against the default embedded SQLite backend with the
 * Flyway V6 vaults table applied — the tested default of the backend-parity matrix. It proves the round-trip
 * (save → find), that the opaque serialized contents survive the base64 TEXT column byte-for-byte, that an
 * empty vault stores a null {@code contents} cell and round-trips back to empty, that a re-save upserts on the
 * {@code (owner, idx)} key rather than inserting, that the per-owner index listing reads ascending, and that
 * the count the amount quota relies on reflects the rows.
 */
class JooqVaultRepositoryTest {

    private Persistence persistence;
    private JooqVaultRepository repository;
    private PlayerRef owner;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        persistence = Persistence.open(new SqliteConfig(), dataFolder, List.of("db/migration"), new NoopLogger());
        repository = new JooqVaultRepository(persistence.dsl());
        owner = new PlayerRef(UUID.randomUUID(), "Alice");
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @Test
    void savesAndLoadsAVaultRoundTrippingTheOpaqueContents() {
        byte[] payload = {0, 1, 2, 3, 4, (byte) 200, (byte) 255};
        repository.save(vault(1, 6, VaultContents.of(payload)));

        Vault loaded = repository.find(VaultId.of(owner, 1)).orElseThrow();

        assertThat(loaded.index()).isEqualTo(1);
        assertThat(loaded.size().rows()).isEqualTo(6);
        assertThat(loaded.contents().payload())
                .get()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.BYTE_ARRAY)
                .containsExactly(payload);
    }

    @Test
    void anEmptyVaultStoresNoBlobAndRoundTripsToEmpty() {
        repository.save(vault(2, 3, VaultContents.empty()));

        Vault loaded = repository.find(VaultId.of(owner, 2)).orElseThrow();

        assertThat(loaded.contents().isEmpty()).isTrue();
    }

    @Test
    void saveUpsertsOnTheOwnerIndexKeyRatherThanInserting() {
        repository.save(vault(1, 3, VaultContents.of(new byte[] {1})));
        repository.save(vault(1, 6, VaultContents.of(new byte[] {9, 9}))); // same (owner, idx) — a re-save

        assertThat(repository.count(owner)).isEqualTo(1);
        Vault reloaded = repository.find(VaultId.of(owner, 1)).orElseThrow();
        assertThat(reloaded.size().rows()).isEqualTo(6);
        assertThat(reloaded.contents().payload())
                .get()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.BYTE_ARRAY)
                .containsExactly(9, 9);
    }

    @Test
    void ownedIndicesAreReturnedAscending() {
        repository.save(vault(3, 6, VaultContents.empty()));
        repository.save(vault(1, 6, VaultContents.empty()));
        repository.save(vault(2, 6, VaultContents.empty()));

        assertThat(repository.ownedIndices(owner)).containsExactly(1, 2, 3);
        assertThat(repository.count(owner)).isEqualTo(3);
    }

    @Test
    void findIsEmptyForAnUnopenedVault() {
        assertThat(repository.find(VaultId.of(owner, 9))).isEmpty();
        assertThat(repository.ownedIndices(owner)).isEmpty();
    }

    private Vault vault(int index, int rows, VaultContents contents) {
        return Vault.of(VaultId.of(owner, index), new VaultSize(rows), contents, Instant.ofEpochMilli(1_000));
    }

    /** A config that selects the embedded SQLite backend with every default — no network coordinates. */
    private record SqliteConfig() implements ConfigStore {
        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return fallback;
        }
    }

    private static final class NoopLogger implements Logger {
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
