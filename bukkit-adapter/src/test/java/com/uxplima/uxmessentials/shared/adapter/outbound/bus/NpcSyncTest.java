package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.npc.application.port.NpcRepository;
import com.uxplima.uxmessentials.npc.application.port.NpcView;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.persistence.npc.CachedNpcRepository;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.Test;

/** A resync after missed frames redraws exactly the NPCs a peer changed and removes the ones it deleted. */
class NpcSyncTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void aResyncRendersWhatAPeerChangedAndDespawnsWhatItDeleted() {
        SharedRows rows = new SharedRows();
        rows.put(npc("moved", 0));
        rows.put(npc("deleted", 0));
        rows.put(npc("untouched", 0));
        CachedNpcRepository cache = new CachedNpcRepository(rows);
        cache.all();
        // A peer writes the shared rows while the frames announcing it are lost.
        rows.put(npc("moved", 5));
        rows.remove("deleted");
        rows.put(npc("created", 0));
        RecordingView view = new RecordingView();

        NpcSync.resync(cache, view).run();

        assertThat(view.rendered).containsExactlyInAnyOrder("moved", "created");
        assertThat(view.despawned).containsExactly("deleted");
        assertThat(cache.find(NpcName.of("moved"))).contains(npc("moved", 5));
        assertThat(cache.exists(NpcName.of("deleted"))).isFalse();
    }

    private static Npc npc(String name, double x) {
        return Npc.create(NpcName.of(name), new Position(WORLD, x, 64, 0, 0, 0), null, CREATED);
    }

    private static final class SharedRows implements NpcRepository {
        private final Map<String, Npc> rows = new LinkedHashMap<>();

        void put(Npc npc) {
            rows.put(npc.name().value(), npc);
        }

        void remove(String name) {
            rows.remove(name);
        }

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
            put(npc);
        }

        @Override
        public void delete(NpcName name) {
            rows.remove(name.value());
        }
    }

    private static final class RecordingView implements NpcView {
        private final List<String> rendered = new ArrayList<>();
        private final List<String> despawned = new ArrayList<>();

        @Override
        public void render(Npc npc) {
            rendered.add(npc.name().value());
        }

        @Override
        public void despawn(NpcName name) {
            despawned.add(name.value());
        }
    }
}
