package com.uxplima.uxmessentials.skin.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.SkinTextures;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.SkinTexture;
import com.uxplima.uxmessentials.skin.application.port.BedrockSkins;
import com.uxplima.uxmessentials.skin.application.port.SkinRepository;
import com.uxplima.uxmessentials.skin.domain.PlayerSkin;
import com.uxplima.uxmessentials.skin.domain.SkinModel;
import com.uxplima.uxmessentials.skin.domain.SkinSource;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * The join order: the stored choice, then the player's real premium skin, then their Bedrock skin, then the
 * default pool. It stops at the first hit, and every step is fail-soft, because a skin lookup must never be the
 * reason a player cannot log in.
 */
class DressLoginTest {

    private static final UUID PLAYER = UUID.randomUUID();
    private static final String NAME = "Steve";
    private static final SkinTexture STORED = new SkinTexture("c3RvcmVk", "c2ln");
    private static final SkinTexture PREMIUM = new SkinTexture("cHJlbWl1bQ==", "c2ln");
    private static final SkinTexture BEDROCK = new SkinTexture("YmVkcm9jaw==", "c2ln");
    private static final SkinTexture POOL = new SkinTexture("cG9vbA==", "c2ln");

    private final FakeRepository repository = new FakeRepository();

    @Test
    void aStoredSkinWinsOverEverythingElse() {
        repository.stored = new PlayerSkin(
                new PlayerRef(PLAYER, NAME),
                new SkinSource.ByUrl("https://x.invalid/a.png"),
                STORED,
                SkinModel.SLIM,
                Instant.EPOCH);
        FakeTextures textures = new FakeTextures(Map.of(NAME, PREMIUM));
        FakeBedrock bedrock = new FakeBedrock(BEDROCK);

        DressLogin.Dressed dressed =
                resolve(SkinConfig.defaults(), textures, bedrock).orElseThrow();

        assertThat(dressed.texture()).isEqualTo(STORED);
        assertThat(dressed.model()).isEqualTo(SkinModel.SLIM);
        assertThat(textures.asked).isEmpty();
        assertThat(bedrock.asked).isEmpty();
    }

    @Test
    void aPremiumNameWithNoStoredSkinGetsItsRealSkin() {
        FakeTextures textures = new FakeTextures(Map.of(NAME, PREMIUM));

        DressLogin.Dressed dressed = resolve(SkinConfig.defaults(), textures, new FakeBedrock(BEDROCK))
                .orElseThrow();

        assertThat(dressed.texture()).isEqualTo(PREMIUM);
        assertThat(dressed.source()).isEqualTo(new SkinSource.ByName(NAME));
    }

    @Test
    void premiumLookupIsSkippedWhenTheOperatorTurnedItOff() {
        SkinConfig config = SkinConfig.from(new FixedConfig(Map.of("login.premium-skin", false)));
        FakeTextures textures = new FakeTextures(Map.of(NAME, PREMIUM));

        DressLogin.Dressed dressed =
                resolve(config, textures, new FakeBedrock(BEDROCK)).orElseThrow();

        assertThat(dressed.texture()).isEqualTo(BEDROCK);
        assertThat(textures.asked).isEmpty();
    }

    @Test
    void aBedrockPlayerGetsTheirBedrockSkinWhenNothingElseResolved() {
        DressLogin.Dressed dressed = resolve(
                        SkinConfig.defaults(), new FakeTextures(Map.of()), new FakeBedrock(BEDROCK))
                .orElseThrow();

        assertThat(dressed.texture()).isEqualTo(BEDROCK);
        assertThat(dressed.source()).isEqualTo(new SkinSource.Bedrock(PLAYER.toString()));
    }

    @Test
    void aResolvedBedrockSkinIsStoredSoTheNextJoinIsADatabaseRead() {
        resolve(SkinConfig.defaults(), new FakeTextures(Map.of()), new FakeBedrock(BEDROCK));

        assertThat(repository.stored).isNotNull();
        assertThat(repository.find(PLAYER).orElseThrow().texture()).isEqualTo(BEDROCK);
    }

    @Test
    void aStoredBedrockSkinIsReReadWhileRefreshOnJoinIsOn() {
        // The Bedrock side is the authority for a Bedrock player's face, so the stored copy is a cache, not a
        // choice: with the refresh on, a change made there shows on the next join.
        repository.stored = new PlayerSkin(
                new PlayerRef(PLAYER, NAME),
                new SkinSource.Bedrock(PLAYER.toString()),
                STORED,
                SkinModel.CLASSIC,
                Instant.EPOCH);
        SkinConfig config = SkinConfig.from(new FixedConfig(Map.of("login.premium-skin", false)));

        DressLogin.Dressed dressed = resolve(config, new FakeTextures(Map.of()), new FakeBedrock(BEDROCK))
                .orElseThrow();

        assertThat(dressed.texture()).isEqualTo(BEDROCK);
    }

    @Test
    void aStoredBedrockSkinIsLeftAloneOnceTheRefreshIsTurnedOff() {
        repository.stored = new PlayerSkin(
                new PlayerRef(PLAYER, NAME),
                new SkinSource.Bedrock(PLAYER.toString()),
                STORED,
                SkinModel.CLASSIC,
                Instant.EPOCH);
        SkinConfig config = SkinConfig.from(new FixedConfig(Map.of("bedrock.refresh-on-join", false)));
        FakeBedrock bedrock = new FakeBedrock(BEDROCK);

        DressLogin.Dressed dressed =
                resolve(config, new FakeTextures(Map.of()), bedrock).orElseThrow();

        assertThat(dressed.texture()).isEqualTo(STORED);
        assertThat(bedrock.asked).isEmpty();
    }

    @Test
    void aPlayerWithNothingAtAllGetsTheStablePoolEntry() {
        SkinConfig config = SkinConfig.defaults().withDefaultPool(List.of("Alex"));
        FakeTextures textures = new FakeTextures(Map.of("Alex", POOL));

        DressLogin.Dressed dressed =
                resolve(config, textures, FakeBedrock.knowingNobody()).orElseThrow();

        assertThat(dressed.texture()).isEqualTo(POOL);
        assertThat(dressed.source()).isEqualTo(new SkinSource.Fallback("Alex"));
    }

    @Test
    void aPlayerWithNothingAndAnEmptyPoolIsLeftAlone() {
        assertThat(resolve(SkinConfig.defaults(), new FakeTextures(Map.of()), FakeBedrock.knowingNobody()))
                .isEmpty();
    }

    @Test
    void aFailingLookupFallsThroughInsteadOfThrowing() {
        // A rate-limited Mojang must not stop a Bedrock player being dressed, and must never reach the listener.
        SkinTextures throwing = new SkinTextures() {
            @Override
            public CompletableFuture<Optional<SkinTexture>> byName(String username) {
                return CompletableFuture.completedFuture(Optional.empty());
            }

            @Override
            public Optional<SkinTexture> fetchNow(String username) {
                throw new IllegalStateException("rate limited");
            }
        };

        DressLogin.Dressed dressed = resolve(SkinConfig.defaults(), throwing, new FakeBedrock(BEDROCK))
                .orElseThrow();

        assertThat(dressed.texture()).isEqualTo(BEDROCK);
    }

    private Optional<DressLogin.Dressed> resolve(SkinConfig config, SkinTextures textures, BedrockSkins bedrock) {
        return new DressLogin(repository, textures, bedrock, config, new NoopLogger()).resolve(PLAYER, NAME);
    }

    private static final class FakeRepository implements SkinRepository {

        private @Nullable PlayerSkin stored;

        @Override
        public Optional<PlayerSkin> find(UUID player) {
            return Optional.ofNullable(stored);
        }

        @Override
        public void save(PlayerSkin skin) {
            stored = skin;
        }

        @Override
        public void delete(UUID player) {
            stored = null;
        }
    }

    private static final class FakeTextures implements SkinTextures {

        private final Map<String, SkinTexture> byName;
        private final List<String> asked = new ArrayList<>();

        private FakeTextures(Map<String, SkinTexture> byName) {
            this.byName = byName;
        }

        @Override
        public CompletableFuture<Optional<SkinTexture>> byName(String username) {
            return CompletableFuture.completedFuture(fetchNow(username));
        }

        @Override
        public Optional<SkinTexture> fetchNow(String username) {
            asked.add(username);
            return Optional.ofNullable(byName.get(username));
        }
    }

    private static final class FakeBedrock implements BedrockSkins {

        private final Optional<SkinTexture> texture;
        private final List<UUID> asked = new ArrayList<>();

        private FakeBedrock(SkinTexture texture) {
            this.texture = Optional.of(texture);
        }

        private FakeBedrock() {
            this.texture = Optional.empty();
        }

        /** A Floodgate that is installed but knows nothing about this player: a plain Java login. */
        static FakeBedrock knowingNobody() {
            return new FakeBedrock();
        }

        @Override
        public Optional<SkinTexture> byPlayer(UUID player) {
            asked.add(player);
            return texture;
        }

        @Override
        public boolean available() {
            return true;
        }
    }

    private record FixedConfig(Map<String, Object> values)
            implements com.uxplima.uxmessentials.shared.application.port.ConfigStore {
        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return values.get(path) instanceof Boolean value ? value : fallback;
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
