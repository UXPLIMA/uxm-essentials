package com.uxplima.uxmessentials.skin.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.net.InetAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import io.papermc.paper.connection.PlayerLoginConnection;

import net.kyori.adventure.text.Component;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.application.port.SkinTextures;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.SkinTexture;
import com.uxplima.uxmessentials.skin.adapter.inbound.listener.SkinLoginListener;
import com.uxplima.uxmessentials.skin.adapter.outbound.PaperSkinView;
import com.uxplima.uxmessentials.skin.application.DressLogin;
import com.uxplima.uxmessentials.skin.application.SkinConfig;
import com.uxplima.uxmessentials.skin.application.port.BedrockSkins;
import com.uxplima.uxmessentials.skin.application.port.SkinRepository;
import com.uxplima.uxmessentials.skin.domain.PlayerSkin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

/**
 * The login path: a player is dressed on the way in, and nothing that goes wrong out there is allowed to cost them
 * the login. The pre-login event is fired by hand, as the server does, and what the profile carries afterwards is
 * the whole assertion.
 */
class SkinLoginListenerTest {

    private static final UUID PLAYER = UUID.fromString("0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f0");
    private static final String NAME = "Wearer";
    private static final SkinTexture TEXTURE = new SkinTexture("dressed-value", "dressed-signature");

    private ServerMock server;
    private Repository repository;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        repository = new Repository();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aJoiningPlayerIsDressedBeforeTheySpawn() {
        AsyncPlayerPreLoginEvent event = preLogin();

        listener(textures(Map.of(NAME, TEXTURE)), Duration.ofSeconds(3), true).onPreLogin(event);

        assertThat(texturesOf(event.getPlayerProfile()))
                .containsExactly(new ProfileProperty(PaperSkinView.TEXTURES, TEXTURE.value(), TEXTURE.signature()));
    }

    @Test
    void aLookupThatOverrunsTheTimeoutLetsThePlayerInUndressed() throws InterruptedException {
        CountDownLatch release = new CountDownLatch(1);
        SkinTextures slow = new SlowTextures(release);
        AsyncPlayerPreLoginEvent event = preLogin();

        listener(slow, Duration.ofMillis(50L), true).onPreLogin(event);

        // The login is neither refused nor held: the profile simply arrives as it was.
        assertThat(event.getLoginResult()).isEqualTo(AsyncPlayerPreLoginEvent.Result.ALLOWED);
        assertThat(texturesOf(event.getPlayerProfile())).isEmpty();
        release.countDown();
    }

    @Test
    void aLookupThatThrowsIsSwallowedRatherThanFailingTheLogin() {
        AsyncPlayerPreLoginEvent event = preLogin();

        listener(new BrokenTextures(), Duration.ofSeconds(3), true).onPreLogin(event);

        assertThat(event.getLoginResult()).isEqualTo(AsyncPlayerPreLoginEvent.Result.ALLOWED);
        assertThat(texturesOf(event.getPlayerProfile())).isEmpty();
    }

    @Test
    void nothingHappensWhenNoSkinResolves() {
        AsyncPlayerPreLoginEvent event = preLogin();

        listener(textures(Map.of()), Duration.ofSeconds(3), true).onPreLogin(event);

        assertThat(texturesOf(event.getPlayerProfile())).isEmpty();
    }

    @Test
    void aStoppedModuleDressesNobody() {
        AsyncPlayerPreLoginEvent event = preLogin();

        listener(textures(Map.of(NAME, TEXTURE)), Duration.ofSeconds(3), false).onPreLogin(event);

        assertThat(texturesOf(event.getPlayerProfile())).isEmpty();
    }

    @Test
    void aConnectionSomebodyElseAlreadyRefusedIsLeftAlone() {
        AsyncPlayerPreLoginEvent event = preLogin();
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, Component.text("banned"));

        listener(textures(Map.of(NAME, TEXTURE)), Duration.ofSeconds(3), true).onPreLogin(event);

        assertThat(texturesOf(event.getPlayerProfile())).isEmpty();
    }

    private SkinLoginListener listener(SkinTextures skins, Duration timeout, boolean active) {
        DressLogin dressLogin =
                new DressLogin(repository, skins, BedrockSkins.none(), SkinConfig.defaults(), new NoopLogger());
        return new SkinLoginListener(dressLogin, new PoolScheduler(), new NoopLogger(), timeout, () -> active);
    }

    private AsyncPlayerPreLoginEvent preLogin() {
        PlayerProfile profile = server.createProfile(PLAYER, NAME);
        InetAddress address = InetAddress.getLoopbackAddress();
        // The current constructor carries the login connection; nothing under test reads it, so it is a stub.
        return new AsyncPlayerPreLoginEvent(
                NAME, address, address, PLAYER, false, profile, "localhost", mock(PlayerLoginConnection.class));
    }

    private static List<ProfileProperty> texturesOf(PlayerProfile profile) {
        return profile.getProperties().stream()
                .filter(property -> PaperSkinView.TEXTURES.equals(property.getName()))
                .toList();
    }

    private static SkinTextures textures(Map<String, SkinTexture> known) {
        return new FixedTextures(known);
    }

    /** A store nobody has written to: every login resolves from the premium lookup or not at all. */
    private static final class Repository implements SkinRepository {
        private final Map<UUID, PlayerSkin> rows = new HashMap<>();

        @Override
        public Optional<PlayerSkin> find(UUID player) {
            return Optional.ofNullable(rows.get(player));
        }

        @Override
        public void save(PlayerSkin skin) {
            rows.put(skin.owner().uuid(), skin);
        }

        @Override
        public void delete(UUID player) {
            rows.remove(player);
        }
    }

    /** A name lookup answering from a fixed map. */
    private record FixedTextures(Map<String, SkinTexture> known) implements SkinTextures {
        @Override
        public CompletableFuture<Optional<SkinTexture>> byName(String username) {
            return CompletableFuture.completedFuture(fetchNow(username));
        }

        @Override
        public Optional<SkinTexture> fetchNow(String username) {
            return Optional.ofNullable(known.get(username));
        }
    }

    /** A lookup that never answers within the login's patience. */
    private record SlowTextures(CountDownLatch release) implements SkinTextures {
        @Override
        public CompletableFuture<Optional<SkinTexture>> byName(String username) {
            return CompletableFuture.completedFuture(fetchNow(username));
        }

        @Override
        public Optional<SkinTexture> fetchNow(String username) {
            try {
                release.await(5L, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            return Optional.empty();
        }
    }

    /** A lookup whose failure is an exception rather than an empty answer. */
    private static final class BrokenTextures implements SkinTextures {
        @Override
        public CompletableFuture<Optional<SkinTexture>> byName(String username) {
            return CompletableFuture.completedFuture(fetchNow(username));
        }

        @Override
        public Optional<SkinTexture> fetchNow(String username) {
            throw new IllegalStateException("the session service is down");
        }
    }

    /** Runs async work on a real pool, so the login's bounded wait is a real wait. */
    private static final class PoolScheduler implements Scheduler {
        private final ExecutorService pool = Executors.newCachedThreadPool();

        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            pool.execute(task);
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            pool.execute(task);
        }
    }

    private static final class NoopLogger implements Logger {
        private final List<String> warnings = new ArrayList<>();

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {
            warnings.add(message);
        }

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}
