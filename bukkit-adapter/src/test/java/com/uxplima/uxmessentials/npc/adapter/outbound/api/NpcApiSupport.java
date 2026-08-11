package com.uxplima.uxmessentials.npc.adapter.outbound.api;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.npc.application.port.NpcRepository;
import com.uxplima.uxmessentials.npc.application.port.NpcView;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.shared.application.port.SkinTextures;
import com.uxplima.uxmessentials.shared.domain.SkinTexture;

/** The fakes both published-NPC-surface tests build on: a map-backed store, a renderer that draws nothing. */
final class NpcApiSupport {

    private NpcApiSupport() {}

    /** Keeps whole NPCs in a map, which is what the read and the write both need. */
    static final class FakeRepository implements NpcRepository {

        private final Map<String, Npc> byName = new LinkedHashMap<>();

        @Override
        public Optional<Npc> find(NpcName name) {
            return Optional.ofNullable(byName.get(name.value()));
        }

        @Override
        public List<Npc> all() {
            return List.copyOf(byName.values());
        }

        @Override
        public boolean exists(NpcName name) {
            return byName.containsKey(name.value());
        }

        @Override
        public void save(Npc npc) {
            byName.put(npc.name().value(), npc);
        }

        @Override
        public void delete(NpcName name) {
            byName.remove(name.value());
        }
    }

    /** Counts what the renderer was asked to do, so a test can tell a stored edit from a drawn one. */
    static final class RecordingView implements NpcView {

        private int renders;
        private int despawns;

        @Override
        public void render(Npc npc) {
            renders++;
        }

        @Override
        public void despawn(NpcName name) {
            despawns++;
        }

        int renders() {
            return renders;
        }

        int despawns() {
            return despawns;
        }
    }

    /** One account has a skin here; every other name resolves to nothing, the way a real miss does. */
    static final class OneKnownSkin implements SkinTextures {

        static final String OWNER = "Notch";
        static final SkinTexture TEXTURE = new SkinTexture("dGV4dHVyZQ==", "signature");

        @Override
        public CompletableFuture<Optional<SkinTexture>> byName(String username) {
            return CompletableFuture.completedFuture(fetchNow(username));
        }

        @Override
        public Optional<SkinTexture> fetchNow(String username) {
            return OWNER.equals(username) ? Optional.of(TEXTURE) : Optional.empty();
        }
    }
}
