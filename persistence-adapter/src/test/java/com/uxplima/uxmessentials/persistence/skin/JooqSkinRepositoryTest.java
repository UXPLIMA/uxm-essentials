package com.uxplima.uxmessentials.persistence.skin;

import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerSkins.PLAYER_SKINS;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.SkinTexture;
import com.uxplima.uxmessentials.skin.domain.PlayerSkin;
import com.uxplima.uxmessentials.skin.domain.SkinModel;
import com.uxplima.uxmessentials.skin.domain.SkinSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end coverage of {@link JooqSkinRepository} against the default embedded SQLite backend with the Flyway
 * V84 {@code player_skins} table applied. It proves the round-trip (save then find), that every source type
 * survives the type/value column pair, that a second save for the same player replaces the row rather than adding
 * one, that an unsigned texture stays unsigned, and that a delete is idempotent.
 */
class JooqSkinRepositoryTest {

    private static final SkinTexture SIGNED = new SkinTexture("dmFsdWU=", "c2ln");

    private Persistence persistence;
    private JooqSkinRepository repository;
    private PlayerRef owner;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        persistence = Persistence.open(new SqliteConfig(), dataFolder, List.of("db/migration"), new NoopLogger());
        repository = new JooqSkinRepository(persistence.dsl());
        owner = new PlayerRef(UUID.randomUUID(), "Steve");
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @Test
    void savingThenLoadingRoundTripsTheSourceTheTextureAndTheModel() {
        PlayerSkin skin = new PlayerSkin(
                owner,
                new SkinSource.ByUrl("https://example.invalid/s.png"),
                SIGNED,
                SkinModel.SLIM,
                Instant.ofEpochMilli(1_700_000_000_000L));

        repository.save(skin);

        assertThat(repository.find(owner.uuid())).contains(skin);
    }

    @Test
    void everySourceTypeSurvivesTheRow() {
        List<SkinSource> sources = List.of(
                new SkinSource.ByName("Notch"),
                new SkinSource.ByUrl("https://example.invalid/s.png"),
                new SkinSource.ByFile("pirate"),
                new SkinSource.Bedrock("2535000000000000"),
                new SkinSource.Fallback("Alex"));

        for (SkinSource source : sources) {
            repository.save(new PlayerSkin(owner, source, SIGNED, SkinModel.CLASSIC, Instant.EPOCH));

            assertThat(repository.find(owner.uuid()).orElseThrow().source()).isEqualTo(source);
        }
    }

    @Test
    void savingTwiceForTheSamePlayerReplacesTheRow() {
        // A player wears one skin, so the second save is an update rather than a second row.
        repository.save(
                new PlayerSkin(owner, new SkinSource.ByName("Notch"), SIGNED, SkinModel.CLASSIC, Instant.EPOCH));
        repository.save(
                new PlayerSkin(owner, new SkinSource.ByName("Herobrine"), SIGNED, SkinModel.SLIM, Instant.EPOCH));

        assertThat(repository.find(owner.uuid()).orElseThrow().source()).isEqualTo(new SkinSource.ByName("Herobrine"));
        assertThat(persistence.dsl().fetchCount(PLAYER_SKINS)).isEqualTo(1);
    }

    @Test
    void anUnsignedTextureRoundTripsAsUnsigned() {
        repository.save(new PlayerSkin(
                owner,
                new SkinSource.ByName("Notch"),
                new SkinTexture("dmFsdWU=", null),
                SkinModel.CLASSIC,
                Instant.EPOCH));

        assertThat(repository.find(owner.uuid()).orElseThrow().texture().signature())
                .isNull();
    }

    @Test
    void deletingLeavesNothingAndIsIdempotent() {
        repository.save(
                new PlayerSkin(owner, new SkinSource.ByName("Notch"), SIGNED, SkinModel.CLASSIC, Instant.EPOCH));

        repository.delete(owner.uuid());
        repository.delete(owner.uuid());

        assertThat(repository.find(owner.uuid())).isEmpty();
    }

    @Test
    void aPlayerWhoChoseNothingHasNoRow() {
        assertThat(repository.find(UUID.randomUUID())).isEmpty();
    }

    /** A config that selects the embedded SQLite backend with every default: no network coordinates. */
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
