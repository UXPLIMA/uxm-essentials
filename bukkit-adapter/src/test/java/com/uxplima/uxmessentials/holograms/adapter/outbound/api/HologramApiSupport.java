package com.uxplima.uxmessentials.holograms.adapter.outbound.api;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.holograms.application.port.HologramRepository;
import com.uxplima.uxmessentials.holograms.application.port.HologramView;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramName;

/** The fakes both published-hologram-surface tests build on: a map-backed store, a renderer that draws nothing. */
final class HologramApiSupport {

    private HologramApiSupport() {}

    /** Keeps whole holograms in a map, plus the per-hologram viewer sets the port also owns. */
    static final class FakeRepository implements HologramRepository {

        private final Map<String, Hologram> byName = new LinkedHashMap<>();
        private final Map<String, Set<UUID>> manualViewers = new LinkedHashMap<>();
        private final Map<String, Set<UUID>> blacklists = new LinkedHashMap<>();

        @Override
        public Optional<Hologram> find(HologramName name) {
            return Optional.ofNullable(byName.get(name.value()));
        }

        @Override
        public List<Hologram> all() {
            return List.copyOf(byName.values());
        }

        @Override
        public boolean exists(HologramName name) {
            return byName.containsKey(name.value());
        }

        @Override
        public void save(Hologram hologram) {
            byName.put(hologram.name().value(), hologram);
        }

        @Override
        public void delete(HologramName name) {
            byName.remove(name.value());
            manualViewers.remove(name.value());
            blacklists.remove(name.value());
        }

        @Override
        public Set<UUID> manualViewers(HologramName name) {
            return Set.copyOf(manualViewers.getOrDefault(name.value(), Set.of()));
        }

        @Override
        public void showTo(HologramName name, UUID viewer) {
            manualViewers
                    .computeIfAbsent(name.value(), key -> new LinkedHashSet<>())
                    .add(viewer);
        }

        @Override
        public void hideFrom(HologramName name, UUID viewer) {
            manualViewers.getOrDefault(name.value(), new LinkedHashSet<>()).remove(viewer);
        }

        @Override
        public Set<UUID> blacklisted(HologramName name) {
            return Set.copyOf(blacklists.getOrDefault(name.value(), Set.of()));
        }

        @Override
        public void addToBlacklist(HologramName name, UUID viewer) {
            blacklists
                    .computeIfAbsent(name.value(), key -> new LinkedHashSet<>())
                    .add(viewer);
        }

        @Override
        public void removeFromBlacklist(HologramName name, UUID viewer) {
            blacklists.getOrDefault(name.value(), new LinkedHashSet<>()).remove(viewer);
        }
    }

    /** Counts what the renderer was asked to do, so a test can tell a stored edit from a drawn one. */
    static final class RecordingView implements HologramView {

        private int renders;
        private int despawns;

        @Override
        public void render(Hologram hologram) {
            renders++;
        }

        @Override
        public void despawn(HologramName name) {
            despawns++;
        }

        @Override
        public void applyManualViewer(HologramName name, UUID viewer, boolean visible) {}

        int renders() {
            return renders;
        }

        int despawns() {
            return despawns;
        }
    }
}
