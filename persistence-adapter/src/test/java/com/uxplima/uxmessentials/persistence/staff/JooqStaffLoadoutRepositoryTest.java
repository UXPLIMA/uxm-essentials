package com.uxplima.uxmessentials.persistence.staff;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.staff.domain.LoadoutBlob;
import com.uxplima.uxmessentials.staff.domain.SavedLoadout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end coverage of {@link JooqStaffLoadoutRepository} against the default embedded SQLite backend with the
 * Flyway V29 staff_loadout table applied — the tested default of the backend-parity matrix. It proves the
 * round-trip (save → load): all four opaque item/effect regions survive the base64 TEXT columns byte-for-byte
 * and all five scalars reconstruct equal, so a saved {@link SavedLoadout} equals the loaded one; that a re-save
 * upserts on the {@code player} key rather than inserting a second row (the second save wins); that load on a
 * player with no row is empty; and that delete removes exactly the one row.
 */
class JooqStaffLoadoutRepositoryTest {

    private Persistence persistence;
    private JooqStaffLoadoutRepository repository;
    private UUID owner;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        persistence = Persistence.open(new SqliteConfig(), dataFolder, List.of("db/migration"), new NoopLogger());
        repository = new JooqStaffLoadoutRepository(
                persistence.dsl(), Clock.fixed(Instant.ofEpochMilli(123_456), ZoneOffset.UTC));
        owner = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @Test
    void savesAndLoadsRoundTrippingEveryBlobAndScalar() {
        SavedLoadout loadout = new SavedLoadout(
                LoadoutBlob.of(new byte[] {0, 1, 2, 3, (byte) 200, (byte) 255}),
                LoadoutBlob.of(new byte[] {10, 11, 12}),
                LoadoutBlob.of(new byte[] {42}),
                4,
                30,
                0.75f,
                "CREATIVE",
                true,
                LoadoutBlob.of(new byte[] {7, 8, 9, (byte) 250}),
                true);

        repository.save(owner, loadout);

        SavedLoadout loaded = repository.load(owner).orElseThrow();
        assertThat(loaded).isEqualTo(loadout);
        // The pre-mode vanish flag survives the SMALLINT column round-trip.
        assertThat(loaded.vanishedBefore()).isTrue();
    }

    @Test
    void aNotVanishedBeforeFlagRoundTripsBackToFalse() {
        SavedLoadout loadout = new SavedLoadout(
                LoadoutBlob.of(new byte[] {1}),
                LoadoutBlob.empty(),
                LoadoutBlob.empty(),
                0,
                0,
                0f,
                "SURVIVAL",
                false,
                LoadoutBlob.empty(),
                false);

        repository.save(owner, loadout);

        SavedLoadout loaded = repository.load(owner).orElseThrow();
        assertThat(loaded.vanishedBefore()).isFalse();
        assertThat(loaded).isEqualTo(loadout);
    }

    @Test
    void anEmptyRegionRoundTripsBackToEmpty() {
        SavedLoadout loadout = new SavedLoadout(
                LoadoutBlob.empty(),
                LoadoutBlob.empty(),
                LoadoutBlob.empty(),
                0,
                0,
                0.0f,
                "SURVIVAL",
                false,
                LoadoutBlob.empty(),
                false);

        repository.save(owner, loadout);

        SavedLoadout loaded = repository.load(owner).orElseThrow();
        assertThat(loaded).isEqualTo(loadout);
        assertThat(loaded.inventory().isEmpty()).isTrue();
        assertThat(loaded.potionEffects().isEmpty()).isTrue();
    }

    @Test
    void saveUpsertsOnThePlayerKeyRatherThanInserting() {
        SavedLoadout first = loadoutWith(LoadoutBlob.of(new byte[] {1}), 1, "SURVIVAL", false);
        SavedLoadout second = loadoutWith(LoadoutBlob.of(new byte[] {9, 9}), 7, "SPECTATOR", true);

        repository.save(owner, first);
        repository.save(owner, second); // same owner — a re-save

        SavedLoadout loaded = repository.load(owner).orElseThrow();
        assertThat(loaded).isEqualTo(second);
    }

    @Test
    void loadIsEmptyForAPlayerWithNoSavedLoadout() {
        assertThat(repository.load(UUID.randomUUID())).isEmpty();
    }

    @Test
    void deleteRemovesTheRow() {
        repository.save(owner, loadoutWith(LoadoutBlob.of(new byte[] {1, 2, 3}), 2, "SURVIVAL", false));

        repository.delete(owner);

        assertThat(repository.load(owner)).isEmpty();
    }

    @Test
    void deletingAPlayerWithNoLoadoutIsANoOp() {
        repository.delete(owner); // never entered staff mode — no row to remove

        assertThat(repository.load(owner)).isEmpty();
    }

    private static SavedLoadout loadoutWith(LoadoutBlob inventory, int heldSlot, String gameMode, boolean flying) {
        return new SavedLoadout(
                inventory,
                LoadoutBlob.of(new byte[] {5}),
                LoadoutBlob.of(new byte[] {6}),
                heldSlot,
                15,
                0.5f,
                gameMode,
                flying,
                LoadoutBlob.of(new byte[] {7}),
                flying);
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
