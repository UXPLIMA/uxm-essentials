package com.uxplima.uxmessentials.persistence.holograms;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.holograms.domain.Appearance;
import com.uxplima.uxmessentials.holograms.domain.Billboard;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramLine;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.holograms.domain.Visibility;
import com.uxplima.uxmessentials.persistence.jooq.tables.HologramLines;
import com.uxplima.uxmessentials.persistence.jooq.tables.Holograms;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end coverage of {@link JooqHologramRepository} against the default embedded SQLite backend with the
 * Flyway V13 baseline applied. It proves the round-trip (save → find) of the name row and its ordered lines,
 * the name-key upsert (a re-anchor / line edit overwrites in place and rewrites the lines rather than leaving
 * stale rows), the delete removing both the name row and its lines, the {@code exists} check, and the
 * creation-order list.
 */
class JooqHologramRepositoryTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");

    private Persistence persistence;
    private JooqHologramRepository repository;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        persistence = Persistence.open(new SqliteConfig(), dataFolder, List.of("db/migration"), new NoopLogger());
        repository = new JooqHologramRepository(persistence.dsl());
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @Test
    void savesAndFindsAHologramWithItsOrderedLines() {
        repository.save(hologram("spawn", 10, 64, 20, "one", "two", "three"));

        Optional<Hologram> loaded = repository.find(HologramName.of("spawn"));

        assertThat(loaded).isPresent();
        Hologram reloaded = loaded.orElseThrow();
        assertThat(reloaded.location().blockX()).isEqualTo(10);
        assertThat(reloaded.location().blockZ()).isEqualTo(20);
        assertThat(reloaded.location().world().name()).isEqualTo("world");
        assertThat(reloaded.lines()).map(HologramLine::value).containsExactly("one", "two", "three");
    }

    @Test
    void saveUpsertsOnTheNameKeyAndRewritesTheLines() {
        repository.save(hologram("spawn", 0, 0, 0, "old-a", "old-b"));
        repository.save(hologram("spawn", 100, 70, 100, "new")); // same name — a re-anchor with fewer lines

        assertThat(repository.all()).hasSize(1);
        Hologram reanchored = repository.find(HologramName.of("spawn")).orElseThrow();
        assertThat(reanchored.location().blockX()).isEqualTo(100);
        assertThat(reanchored.lines()).map(HologramLine::value).containsExactly("new"); // no stale line rows
    }

    @Test
    void existsReflectsWhetherAHologramIsStored() {
        assertThat(repository.exists(HologramName.of("spawn"))).isFalse();

        repository.save(hologram("spawn", 0, 0, 0, "line"));

        assertThat(repository.exists(HologramName.of("spawn"))).isTrue();
    }

    @Test
    void deleteRemovesTheNameRowAndItsLines() {
        repository.save(hologram("spawn", 0, 0, 0, "a", "b"));
        repository.save(hologram("pvp", 1, 1, 1, "c"));

        repository.delete(HologramName.of("spawn"));

        assertThat(repository.exists(HologramName.of("spawn"))).isFalse();
        assertThat(repository.all()).hasSize(1);
        // A re-created hologram under the freed name starts with only its own lines, proving no orphans.
        repository.save(hologram("spawn", 5, 5, 5, "fresh"));
        assertThat(repository.find(HologramName.of("spawn")).orElseThrow().lines())
                .map(HologramLine::value)
                .containsExactly("fresh");
    }

    @Test
    void allPreservesCreationOrder() {
        repository.save(hologramAt("first", Instant.ofEpochMilli(1_000)));
        repository.save(hologramAt("second", Instant.ofEpochMilli(2_000)));
        repository.save(hologramAt("third", Instant.ofEpochMilli(3_000)));

        assertThat(repository.all().stream().map(h -> h.name().value())).containsExactly("first", "second", "third");
    }

    @Test
    void roundTripsAppearanceAndRefreshInterval() {
        Appearance styled = Appearance.defaults()
                .withBillboard(Billboard.VERTICAL)
                .withBackgroundArgb(0x80112233)
                .withTextShadow(true)
                .withBrightness(15, 7)
                .withScale(2.5f)
                .withLineWidth(120)
                .withViewRange(3.0f);
        repository.save(
                hologram("spawn", 1, 64, 1, "line").withAppearance(styled).withRefreshIntervalTicks(40));

        Hologram loaded = repository.find(HologramName.of("spawn")).orElseThrow();

        assertThat(loaded.appearance()).isEqualTo(styled);
        assertThat(loaded.refreshIntervalTicks()).isEqualTo(40);
    }

    @Test
    void roundTripsPermissionVisibilityAndDistance() {
        Visibility gated =
                Visibility.restrictedTo("uxmessentials.hologram.see.vip").withDistance(48);
        repository.save(hologram("spawn", 1, 64, 1, "line").withVisibility(gated));

        Hologram loaded = repository.find(HologramName.of("spawn")).orElseThrow();

        assertThat(loaded.visibility()).isEqualTo(gated);
        assertThat(loaded.visibility().mode()).isEqualTo(Visibility.Mode.PERMISSION);
        assertThat(loaded.visibility().permission()).isEqualTo("uxmessentials.hologram.see.vip");
        assertThat(loaded.visibility().distance()).isEqualTo(48);
    }

    @Test
    void aRowWithNoVisibilityColumnsReadsBackAsEveryone() {
        // A pre-V36 row: insert the name row directly leaving every visibility column NULL, plus one line.
        Holograms holograms = Holograms.HOLOGRAMS;
        HologramLines lines = HologramLines.HOLOGRAM_LINES;
        persistence.dsl().transaction(config -> {
            config.dsl()
                    .insertInto(holograms)
                    .set(holograms.NAME, "legacy")
                    .set(holograms.WORLD, WORLD.uid().toString())
                    .set(holograms.WORLD_NAME, "world")
                    .set(holograms.X, 0.0)
                    .set(holograms.Y, 64.0)
                    .set(holograms.Z, 0.0)
                    .set(holograms.YAW, 0.0f)
                    .set(holograms.PITCH, 0.0f)
                    .set(holograms.CREATED_AT, 1_000L)
                    .execute();
            config.dsl()
                    .insertInto(lines)
                    .set(lines.HOLOGRAM, "legacy")
                    .set(lines.IDX, 0)
                    .set(lines.TEXT, "line")
                    .execute();
        });

        Hologram loaded = repository.find(HologramName.of("legacy")).orElseThrow();

        assertThat(loaded.visibility()).isEqualTo(Visibility.everyone());
    }

    @Test
    void aRowWithNoAppearanceColumnsReadsBackAsDefaults() {
        // A pre-V35 row: insert the name row directly leaving every appearance column NULL, plus one line. The
        // insert runs in a transaction so it commits exactly as the repository's own save path does.
        Holograms holograms = Holograms.HOLOGRAMS;
        HologramLines lines = HologramLines.HOLOGRAM_LINES;
        persistence.dsl().transaction(config -> {
            config.dsl()
                    .insertInto(holograms)
                    .set(holograms.NAME, "legacy")
                    .set(holograms.WORLD, WORLD.uid().toString())
                    .set(holograms.WORLD_NAME, "world")
                    .set(holograms.X, 0.0)
                    .set(holograms.Y, 64.0)
                    .set(holograms.Z, 0.0)
                    .set(holograms.YAW, 0.0f)
                    .set(holograms.PITCH, 0.0f)
                    .set(holograms.CREATED_AT, 1_000L)
                    .execute();
            config.dsl()
                    .insertInto(lines)
                    .set(lines.HOLOGRAM, "legacy")
                    .set(lines.IDX, 0)
                    .set(lines.TEXT, "line")
                    .execute();
        });

        Hologram loaded = repository.find(HologramName.of("legacy")).orElseThrow();

        assertThat(loaded.appearance()).isEqualTo(Appearance.defaults());
        assertThat(loaded.refreshIntervalTicks()).isZero();
    }

    private Hologram hologram(String name, double x, double y, double z, String... lines) {
        return Hologram.create(
                HologramName.of(name),
                Position.of(WORLD, x, y, z),
                List.of(lines).stream().map(HologramLine::new).toList(),
                Instant.ofEpochMilli(1_000));
    }

    private Hologram hologramAt(String name, Instant createdAt) {
        return Hologram.create(
                HologramName.of(name), Position.of(WORLD, 0, 64, 0), List.of(new HologramLine("line")), createdAt);
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
