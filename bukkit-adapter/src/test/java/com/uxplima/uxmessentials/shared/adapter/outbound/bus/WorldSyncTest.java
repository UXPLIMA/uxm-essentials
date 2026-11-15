package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import com.uxplima.uxmessentials.persistence.worlds.CachedWorldRepository;
import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import com.uxplima.uxmessentials.shared.network.WorldChanged;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.NetworkWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldSpec;
import org.junit.jupiter.api.Test;

/** A world change on one backend reaches a peer's cached view of the network once, whatever the transport. */
class WorldSyncTest {

    private static final WorldName ARENA = WorldName.of("arena");

    private final Map<String, Map<WorldName, ManagedWorld>> table = new LinkedHashMap<>();
    private final List<NetworkMessage> published = new ArrayList<>();
    private final BusPublisher survivalBus = new BusPublisher() {
        @Override
        public void publish(NetworkMessage message) {
            published.add(message);
        }

        @Override
        public String serverId() {
            return "survival";
        }
    };

    @Test
    void aWorldSavedOnOneServerRefreshesAPeerOnceEvenWhenTheFrameArrivesTwice() {
        CachedWorldRepository lobby = new CachedWorldRepository(new Shared("lobby"), "lobby");
        assertThat(lobby.network()).isEmpty();
        AtomicInteger reloads = new AtomicInteger();
        RemoteSyncListener listener = WorldSync.listener(() -> {
            reloads.incrementAndGet();
            lobby.reloadAll();
        });
        WorldRepository survival =
                WorldSync.repository(new CachedWorldRepository(new Shared("survival"), "survival"), survivalBus);

        survival.save(ManagedWorld.created(ARENA, WorldSpec.normal(), true, Optional.empty(), Instant.EPOCH));
        // With network.transport = both the frame comes over the proxy and over Redis.
        published.forEach(listener::onRemoteChange);
        published.forEach(listener::onRemoteChange);

        assertThat(published).hasSize(1);
        assertThat(published.get(0)).isInstanceOfSatisfying(WorldChanged.class, frame -> {
            assertThat(frame.server()).isEqualTo("survival");
            assertThat(frame.world()).isEqualTo("arena");
        });
        assertThat(reloads).hasValue(1);
        assertThat(lobby.network()).extracting(NetworkWorld::server).containsExactly("survival");
        assertThat(lobby.find(ARENA)).isEmpty();
    }

    @Test
    void twoChangesRefreshAPeerTwice() {
        AtomicInteger reloads = new AtomicInteger();
        RemoteSyncListener listener = WorldSync.listener(reloads::incrementAndGet);
        WorldRepository survival =
                WorldSync.repository(new CachedWorldRepository(new Shared("survival"), "survival"), survivalBus);

        survival.save(ManagedWorld.created(ARENA, WorldSpec.normal(), true, Optional.empty(), Instant.EPOCH));
        survival.delete(ARENA);
        published.forEach(listener::onRemoteChange);

        assertThat(reloads).hasValue(2);
    }

    /** One backend's view of the world table every backend shares. */
    private final class Shared implements WorldRepository {

        private final String server;

        Shared(String server) {
            this.server = server;
        }

        private Map<WorldName, ManagedWorld> mine() {
            return table.computeIfAbsent(server, ignored -> new LinkedHashMap<>());
        }

        @Override
        public Optional<ManagedWorld> find(WorldName name) {
            return Optional.ofNullable(mine().get(name));
        }

        @Override
        public List<ManagedWorld> all() {
            return List.copyOf(mine().values());
        }

        @Override
        public boolean exists(WorldName name) {
            return mine().containsKey(name);
        }

        @Override
        public void save(ManagedWorld world) {
            mine().put(world.name(), world);
        }

        @Override
        public void delete(WorldName name) {
            mine().remove(name);
        }

        @Override
        public List<NetworkWorld> network() {
            List<NetworkWorld> worlds = new ArrayList<>();
            table.forEach((owner, rows) -> rows.values().forEach(world -> worlds.add(new NetworkWorld(owner, world))));
            return worlds;
        }
    }
}
