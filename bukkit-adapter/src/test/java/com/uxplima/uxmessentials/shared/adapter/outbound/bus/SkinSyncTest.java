package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.persistence.skin.CachedSkinRepository;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.application.port.SkinTextures;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.SkinTexture;
import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import com.uxplima.uxmessentials.skin.application.ClearSkin;
import com.uxplima.uxmessentials.skin.application.DressLogin;
import com.uxplima.uxmessentials.skin.application.DropSkin;
import com.uxplima.uxmessentials.skin.application.SkinConfig;
import com.uxplima.uxmessentials.skin.application.port.BedrockSkins;
import com.uxplima.uxmessentials.skin.application.port.SkinRepository;
import com.uxplima.uxmessentials.skin.application.port.SkinView;
import com.uxplima.uxmessentials.skin.domain.PlayerSkin;
import com.uxplima.uxmessentials.skin.domain.SkinModel;
import com.uxplima.uxmessentials.skin.domain.SkinSource;
import org.junit.jupiter.api.Test;

/**
 * A skin changed on one backend is forgotten by every peer's cache and put on the player by the backend they are on.
 */
class SkinSyncTest {

    private static final PlayerRef ADA = new PlayerRef(UUID.fromString("00000000-0000-0000-0000-00000000a0a0"), "Ada");
    private static final SkinTexture OLD = new SkinTexture("old", "sig-old");
    private static final SkinTexture CHOSEN = new SkinTexture("chosen", "sig-chosen");
    private static final SkinTexture PREMIUM = new SkinTexture("premium", "sig-premium");

    private final SharedRows rows = new SharedRows();
    private final Frames bus = new Frames("lobby-1");
    private final SkinTextures mojang = new Accounts(Map.of("Ada", PREMIUM));

    /** One backend: its own cache over the shared rows, who is online there, and what it put on whom. */
    private final class Backend {
        final CachedSkinRepository cache = new CachedSkinRepository(rows, 100, Duration.ofMinutes(10));
        final RecordingView view = new RecordingView();
        final Online online = new Online();
        final DressLogin dressLogin =
                new DressLogin(cache, mojang, BedrockSkins.none(), SkinConfig.defaults(), new QuietLog());
        final RemoteSyncListener listener =
                SkinSync.listener(cache, dressLogin, view, online, new InlineScheduler(), () -> true);
    }

    /** The origin: the broadcasting store over its own cache, and a view that dresses nobody since Ada is elsewhere. */
    private final SkinRepository origin =
            SkinSync.repository(new CachedSkinRepository(rows, 100, Duration.ofMinutes(10)), bus, new QuietLog());

    @Test
    void aSkinChosenOnOneBackendIsWornAtOnceOnTheBackendThePlayerIsOn() {
        rows.save(stored(OLD));
        Backend survival = new Backend();
        survival.online.add(ADA);
        assertThat(survival.cache.find(ADA.uuid())).map(PlayerSkin::texture).contains(OLD);

        origin.save(stored(CHOSEN));
        bus.deliverTo(survival.listener);

        assertThat(survival.cache.find(ADA.uuid())).map(PlayerSkin::texture).contains(CHOSEN);
        assertThat(survival.view.dressed).containsExactly("Ada:chosen");
    }

    @Test
    void aClearOnOneBackendDressesThePlayerInTheJoinOrderSkinWhereTheyAre() {
        rows.save(stored(OLD));
        Backend survival = new Backend();
        survival.online.add(ADA);
        survival.cache.find(ADA.uuid());
        ClearSkin clear = new ClearSkin(
                origin,
                new DressLogin(origin, mojang, BedrockSkins.none(), SkinConfig.defaults(), new QuietLog()),
                new RecordingView(),
                event -> {},
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

        assertThat(clear.clear(ADA)).isEqualTo(ClearSkin.Outcome.CLEARED);
        bus.deliverTo(survival.listener);

        assertThat(survival.cache.find(ADA.uuid())).isEmpty();
        assertThat(survival.view.dressed).containsExactly("Ada:premium");
    }

    @Test
    void aDropForAPlayerOnNoPeerOnlyLeavesThePeersReadingTheRowAsGone() {
        rows.save(stored(OLD));
        Backend survival = new Backend();
        survival.cache.find(ADA.uuid());

        DropSkin drop = new DropSkin(
                origin,
                new DressLogin(origin, mojang, BedrockSkins.none(), SkinConfig.defaults(), new QuietLog()),
                new RecordingView(),
                new Online());

        assertThat(drop.drop(ADA)).isEqualTo(DropSkin.Outcome.DROPPED);
        bus.deliverTo(survival.listener);

        assertThat(survival.cache.find(ADA.uuid())).isEmpty();
        assertThat(survival.view.dressed).isEmpty();
    }

    private static PlayerSkin stored(SkinTexture texture) {
        return new PlayerSkin(ADA, new SkinSource.ByName("Steve"), texture, SkinModel.CLASSIC, Instant.EPOCH);
    }

    /** The bus: keeps what the origin published until the test hands it to the peers. */
    private static final class Frames implements BusPublisher {
        private final String serverId;
        private final List<NetworkMessage> published = new ArrayList<>();

        Frames(String serverId) {
            this.serverId = serverId;
        }

        @Override
        public void publish(NetworkMessage message) {
            published.add(message);
        }

        @Override
        public String serverId() {
            return serverId;
        }

        void deliverTo(RemoteSyncListener peer) {
            for (NetworkMessage message : published) {
                peer.onRemoteChange(message);
            }
            published.clear();
        }
    }

    /** The shared database every backend reads. */
    private static final class SharedRows implements SkinRepository {
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

    /** Mojang: the skin each paid account wears. */
    private record Accounts(Map<String, SkinTexture> skins) implements SkinTextures {
        @Override
        public CompletableFuture<Optional<SkinTexture>> byName(String username) {
            return CompletableFuture.completedFuture(fetchNow(username));
        }

        @Override
        public Optional<SkinTexture> fetchNow(String username) {
            return Optional.ofNullable(skins.get(username));
        }
    }

    /** The players connected to one backend. */
    private static final class Online implements PlayerLookup {
        private final Map<UUID, PlayerRef> players = new HashMap<>();

        void add(PlayerRef player) {
            players.put(player.uuid(), player);
        }

        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            return players.values().stream()
                    .filter(player -> player.name().equals(name))
                    .findFirst();
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            return Optional.ofNullable(players.get(uuid));
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return players.containsKey(uuid);
        }
    }

    private static final class RecordingView implements SkinView {
        private final List<String> dressed = new ArrayList<>();

        @Override
        public void apply(PlayerRef who, SkinTexture texture, SkinModel model) {
            dressed.add(who.name() + ":" + texture.value());
        }
    }

    private static final class QuietLog implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }

    /** Runs the async lane at once, so a delivered frame is applied before the assertions. */
    private static final class InlineScheduler implements Scheduler {
        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            throw new UnsupportedOperationException("the skin listener schedules nothing for later");
        }

        @Override
        public void onGlobal(Runnable task) {
            throw new UnsupportedOperationException("the skin listener never touches the global region");
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            throw new UnsupportedOperationException("the skin listener never touches a region");
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            throw new UnsupportedOperationException("the skin listener dresses through the view");
        }
    }
}
