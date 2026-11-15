package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.holograms.adapter.outbound.EventDrivenNpcLocator;
import com.uxplima.uxmessentials.holograms.application.ScopedHologramView;
import com.uxplima.uxmessentials.holograms.application.SetHologramScope;
import com.uxplima.uxmessentials.holograms.application.port.HologramRepository;
import com.uxplima.uxmessentials.holograms.application.port.HologramView;
import com.uxplima.uxmessentials.holograms.application.port.LinkedNpcLocator;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramLine;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.npc.application.ScopedNpcView;
import com.uxplima.uxmessentials.npc.application.SetNpcScope;
import com.uxplima.uxmessentials.npc.application.port.NpcRepository;
import com.uxplima.uxmessentials.npc.application.port.NpcView;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.persistence.holograms.CachedHologramRepository;
import com.uxplima.uxmessentials.persistence.npc.CachedNpcRepository;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.LocalServer;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.ServerScope;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.shared.domain.message.MessageKey;
import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import org.junit.jupiter.api.Test;

/**
 * A hologram scoped on one backend leaves the peers outside the scope and stays on the ones inside it, and a hologram
 * riding an NPC follows the NPC when it is scoped on another backend.
 */
class HologramSyncTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final HologramName BOARD = HologramName.of("board");
    private static final PlayerRef ADMIN = new PlayerRef(UUID.randomUUID(), "Admin");
    private static final LinkedNpcLocator NO_NPCS = new LinkedNpcLocator() {
        @Override
        public Optional<Position> locate(String npcName) {
            return Optional.empty();
        }

        @Override
        public boolean shownHere(String npcName) {
            return true;
        }
    };

    private final SharedRows rows = new SharedRows();
    private final Frames bus = new Frames("lobby-1");

    /** One backend: its own cache over the shared rows and a renderer that records what it drew. */
    private final class Backend {
        final CachedHologramRepository cache = new CachedHologramRepository(rows, Duration.ofMinutes(5));
        final RecordingView drawn = new RecordingView();
        final HologramView view;
        final RemoteSyncListener listener;

        Backend(LocalServer local) {
            view = new ScopedHologramView(drawn, local, NO_NPCS);
            listener = HologramSync.listener(cache, view, new InlineScheduler());
            cache.all();
        }
    }

    @Test
    void aScopeSetOnOneBackendTakesTheHologramOffAPeerOutsideItAndKeepsItOnAPeerInside() {
        rows.save(board());
        LocalServer origin = new LocalServer("lobby-1", List.of("lobby"));
        Backend lobby2 = new Backend(new LocalServer("lobby-2", List.of("lobby")));
        Backend survival = new Backend(new LocalServer("survival", List.of()));
        SetHologramScope scope = new SetHologramScope(
                HologramSync.repository(new CachedHologramRepository(rows, Duration.ofMinutes(5)), bus),
                new ScopedHologramView(new RecordingView(), origin, NO_NPCS),
                quietNotifier(),
                origin);

        scope.set(ADMIN, BOARD, "set:lobby");
        bus.deliverTo(lobby2.listener, survival.listener);

        assertThat(survival.drawn.despawned).containsExactly("board");
        assertThat(survival.drawn.rendered).isEmpty();
        assertThat(lobby2.drawn.rendered).containsExactly("board");
        assertThat(lobby2.cache.find(BOARD).orElseThrow().scope()).hasToString("set:lobby");
    }

    @Test
    void clearingTheScopeBringsTheHologramBackOnThePeer() {
        rows.save(board().withScope(ServerScope.server("lobby-1")));
        LocalServer origin = new LocalServer("lobby-1", List.of());
        Backend survival = new Backend(new LocalServer("survival", List.of()));
        SetHologramScope scope = new SetHologramScope(
                HologramSync.repository(new CachedHologramRepository(rows, Duration.ofMinutes(5)), bus),
                new ScopedHologramView(new RecordingView(), origin, NO_NPCS),
                quietNotifier(),
                origin);

        scope.set(ADMIN, BOARD, "clear");
        bus.deliverTo(survival.listener);

        assertThat(survival.drawn.rendered).containsExactly("board");
    }

    @Test
    void anNpcScopedAwayOnOneBackendTakesTheHologramRidingItOffAPeerAndClearingBringsItBack() {
        SharedNpcs npcRows = new SharedNpcs();
        npcRows.save(Npc.create(NpcName.of("guide"), new Position(WORLD, 3, 64, 3, 0, 0), null, Instant.EPOCH));
        rows.save(board().linkedTo("guide"));
        // The peer, survival: its own copy of the NPCs, a locator over it, and the scoped view the holograms use.
        LocalServer survival = new LocalServer("survival", List.of());
        CachedHologramRepository peerHolograms = new CachedHologramRepository(rows, Duration.ofMinutes(5));
        RecordingView drawn = new RecordingView();
        HologramView[] view = new HologramView[1];
        CachedNpcRepository peerNpcs = new CachedNpcRepository(npcRows);
        EventDrivenNpcLocator locator = new EventDrivenNpcLocator(peerNpcs, survival, name -> {}, name -> {
            for (Hologram hologram : peerHolograms.all()) {
                if (name.equals(hologram.linkedNpcName())) {
                    view[0].render(hologram);
                }
            }
        });
        view[0] = new ScopedHologramView(drawn, survival, locator);
        RemoteSyncListener peer = HologramSync.npcListener(peerNpcs, locator, new InlineScheduler());
        // The origin, lobby-1, scopes the NPC through the npc module's broadcasting repository.
        LocalServer lobby = new LocalServer("lobby-1", List.of());
        SetNpcScope scope = new SetNpcScope(
                NpcSync.repository(new CachedNpcRepository(npcRows), bus),
                new ScopedNpcView(new NoNpcView(), lobby),
                quietNotifier(),
                event -> {},
                lobby);

        scope.set(ADMIN, NpcName.of("guide"), "server");
        bus.deliverTo(peer);
        assertThat(drawn.despawned).containsExactly("board");

        scope.set(ADMIN, NpcName.of("guide"), "clear");
        bus.deliverTo(peer);
        assertThat(drawn.rendered).containsExactly("board");
    }

    private static Hologram board() {
        return Hologram.create(
                BOARD, new Position(WORLD, 0, 70, 0, 0, 0), List.of(new HologramLine("Welcome")), Instant.EPOCH);
    }

    private static Notifier quietNotifier() {
        return new Notifier(
                (PlayerRef viewer, MessageKey key, Map<String, String> placeholders) -> key.key(),
                (viewer, text) -> {});
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

        void deliverTo(RemoteSyncListener... peers) {
            for (NetworkMessage message : published) {
                for (RemoteSyncListener peer : peers) {
                    peer.onRemoteChange(message);
                }
            }
            published.clear();
        }
    }

    /** The shared NPC rows every backend reads. */
    private static final class SharedNpcs implements NpcRepository {
        private final Map<String, Npc> rows = new LinkedHashMap<>();

        @Override
        public Optional<Npc> find(NpcName name) {
            return Optional.ofNullable(rows.get(name.value()));
        }

        @Override
        public List<Npc> all() {
            return List.copyOf(rows.values());
        }

        @Override
        public boolean exists(NpcName name) {
            return rows.containsKey(name.value());
        }

        @Override
        public void save(Npc npc) {
            rows.put(npc.name().value(), npc);
        }

        @Override
        public void delete(NpcName name) {
            rows.remove(name.value());
        }
    }

    private static final class NoNpcView implements NpcView {
        @Override
        public void render(Npc npc) {}

        @Override
        public void despawn(NpcName name) {}
    }

    /** The shared database every backend reads. */
    private static final class SharedRows implements HologramRepository {
        private final Map<String, Hologram> rows = new LinkedHashMap<>();

        @Override
        public Optional<Hologram> find(HologramName name) {
            return Optional.ofNullable(rows.get(name.value()));
        }

        @Override
        public List<Hologram> all() {
            return List.copyOf(rows.values());
        }

        @Override
        public boolean exists(HologramName name) {
            return rows.containsKey(name.value());
        }

        @Override
        public void save(Hologram hologram) {
            rows.put(hologram.name().value(), hologram);
        }

        @Override
        public void delete(HologramName name) {
            rows.remove(name.value());
        }

        @Override
        public Set<UUID> manualViewers(HologramName name) {
            return Set.of();
        }

        @Override
        public void showTo(HologramName name, UUID viewer) {}

        @Override
        public void hideFrom(HologramName name, UUID viewer) {}

        @Override
        public Set<UUID> blacklisted(HologramName name) {
            return Set.of();
        }

        @Override
        public void addToBlacklist(HologramName name, UUID viewer) {}

        @Override
        public void removeFromBlacklist(HologramName name, UUID viewer) {}
    }

    private static final class RecordingView implements HologramView {
        private final List<String> rendered = new ArrayList<>();
        private final List<String> despawned = new ArrayList<>();

        @Override
        public void render(Hologram hologram) {
            rendered.add(hologram.name().value());
        }

        @Override
        public void despawn(HologramName name) {
            despawned.add(name.value());
        }

        @Override
        public void applyManualViewer(HologramName name, UUID viewer, boolean visible) {}
    }

    /** Runs the async lane at once, so a delivered frame is applied before the assertions. */
    private static final class InlineScheduler implements Scheduler {
        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            throw new UnsupportedOperationException("the sync listener schedules nothing for later");
        }

        @Override
        public void onGlobal(Runnable task) {
            throw new UnsupportedOperationException("the sync listener never touches the global region");
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            throw new UnsupportedOperationException("the sync listener never touches a region");
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            throw new UnsupportedOperationException("the sync listener never touches an entity");
        }
    }
}
