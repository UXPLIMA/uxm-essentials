package com.uxplima.uxmessentials.migration.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.holograms.application.port.HologramRepository;
import com.uxplima.uxmessentials.holograms.application.port.HologramView;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramLine;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.persistence.holograms.CachedHologramRepository;
import com.uxplima.uxmessentials.persistence.warps.CachedWarpRepository;
import com.uxplima.uxmessentials.persistence.worlds.CachedWorldRepository;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.warps.application.SetWarp;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import com.uxplima.uxmessentials.worlds.domain.WorldSpec;
import org.junit.jupiter.api.Test;

/**
 * An import lands in the caches the running modules serve players from, so an imported row is there without a
 * restart and a later command on the same name sees it. Each module's cache is the real decorator over a shared
 * fake database, warmed as the module warms it on enable; the importer's fallback store is a second cache over
 * that same database, which is what the importer used to write through.
 */
class ImportTargetsTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final WorldRef WORLD =
            new WorldRef(UUID.fromString("00000000-0000-0000-0000-000000000001"), "world");
    private static final PlayerRef IMPORTED_OWNER =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-0000000000aa"), "Founder");
    private static final PlayerRef STAFF =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-0000000000bb"), "Staff");

    private final ImportTargets targets = new ImportTargets();

    @Test
    void anImportedWarpIsServedByTheRunningWarpsModuleWithoutARestart() {
        WarpDatabase database = new WarpDatabase();
        CachedWarpRepository live = warmed(new CachedWarpRepository(database));
        targets.warps(live);
        Warp imported = Warp.create(WarpName.of("market"), Position.of(WORLD, 10, 64, 10), IMPORTED_OWNER, NOW);

        importWarp(targets.warpsOr(() -> new CachedWarpRepository(database)), imported);

        assertThat(live.find(WarpName.of("market"))).contains(imported);
        assertThat(live.all()).containsExactly(imported);
    }

    @Test
    void setwarpOnAnImportedNameMovesTheImportedWarpInsteadOfCreatingOneOverIt() {
        WarpDatabase database = new WarpDatabase();
        CachedWarpRepository live = warmed(new CachedWarpRepository(database));
        targets.warps(live);
        Warp imported = Warp.create(WarpName.of("market"), Position.of(WORLD, 10, 64, 10), IMPORTED_OWNER, NOW);
        importWarp(targets.warpsOr(() -> new CachedWarpRepository(database)), imported);
        RecordingEvents events = new RecordingEvents();
        SetWarp setWarp = new SetWarp(
                live, silentNotifier(), events, Clock.fixed(NOW.plusSeconds(60), ZoneOffset.UTC), List.of());
        Position moved = Position.of(WORLD, 50, 70, 50);

        setWarp.set(STAFF, WarpName.of("market"), moved);

        Warp after = live.find(WarpName.of("market")).orElseThrow();
        assertThat(after.location()).isEqualTo(moved);
        assertThat(after.owner()).isEqualTo(IMPORTED_OWNER);
        assertThat(after.createdAt()).isEqualTo(NOW);
        assertThat(events.published)
                .as("an existing warp is moved, not created")
                .isEmpty();
    }

    @Test
    void anImportedHologramIsServedAndDrawnByTheRunningHologramsModuleWithoutARestart() {
        HologramDatabase database = new HologramDatabase();
        CachedHologramRepository live = new CachedHologramRepository(database);
        live.all();
        RecordingView view = new RecordingView();
        targets.holograms(live, view);
        Hologram imported = Hologram.create(
                HologramName.of("welcome"), Position.of(WORLD, 0, 70, 0), List.of(HologramLine.of("Welcome")), NOW);

        HologramRepository importer = targets.hologramsOr(() -> new CachedHologramRepository(database));
        if (!importer.exists(imported.name())) {
            importer.save(imported);
        }

        assertThat(live.find(HologramName.of("welcome"))).contains(imported);
        assertThat(live.exists(HologramName.of("welcome"))).isTrue();
        assertThat(view.rendered).containsExactly(imported);
    }

    @Test
    void anImportedWorldIsListedByTheRunningWorldsModuleWithoutARestart() {
        WorldDatabase database = new WorldDatabase();
        CachedWorldRepository live = new CachedWorldRepository(database);
        live.all();
        targets.worlds(live);
        ManagedWorld imported =
                ManagedWorld.created(WorldName.of("resource"), WorldSpec.normal(), true, Optional.empty(), NOW);

        WorldRepository importer = targets.worldsOr(() -> new CachedWorldRepository(database));
        if (!importer.exists(imported.name())) {
            importer.save(imported);
        }

        assertThat(live.find(WorldName.of("resource"))).contains(imported);
        assertThat(live.all()).containsExactly(imported);
    }

    @Test
    void aModuleThatIsNotRunningFallsBackToTheStoreOverTheDatabase() {
        WarpDatabase database = new WarpDatabase();
        Warp imported = Warp.create(WarpName.of("market"), Position.of(WORLD, 10, 64, 10), IMPORTED_OWNER, NOW);

        importWarp(targets.warpsOr(() -> new CachedWarpRepository(database)), imported);

        assertThat(database.find(WarpName.of("market"))).contains(imported);
    }

    /** What the importer does with one mapped warp: skip a name it already holds, else save it. */
    private static void importWarp(WarpRepository importer, Warp warp) {
        if (!importer.exists(warp.name())) {
            importer.save(warp);
        }
    }

    /** The module reads its whole set once on enable and never reads the database again. */
    private static CachedWarpRepository warmed(CachedWarpRepository live) {
        live.all();
        return live;
    }

    private static Notifier silentNotifier() {
        return new Notifier((viewer, key, placeholders) -> key.key(), (viewer, text) -> {});
    }

    private static final class RecordingEvents implements DomainEventPublisher {

        private final List<DomainEvent> published = new ArrayList<>();

        @Override
        public void publish(DomainEvent event) {
            published.add(event);
        }
    }

    private static final class RecordingView implements HologramView {

        private final List<Hologram> rendered = new ArrayList<>();

        @Override
        public void render(Hologram hologram) {
            rendered.add(hologram);
        }

        @Override
        public void despawn(HologramName name) {}

        @Override
        public void applyManualViewer(HologramName name, UUID viewer, boolean visible) {}
    }

    /** The warps table. */
    private static final class WarpDatabase implements WarpRepository {

        private final Map<String, Warp> rows = new LinkedHashMap<>();

        @Override
        public Optional<Warp> find(WarpName name) {
            return Optional.ofNullable(rows.get(name.value()));
        }

        @Override
        public List<Warp> all() {
            return List.copyOf(rows.values());
        }

        @Override
        public boolean exists(WarpName name) {
            return rows.containsKey(name.value());
        }

        @Override
        public void save(Warp warp) {
            rows.put(warp.name().value(), warp);
        }

        @Override
        public void delete(WarpName name) {
            rows.remove(name.value());
        }

        @Override
        public void rate(WarpName name, UUID player, double rating) {}

        @Override
        public double averageRating(WarpName name) {
            return 0;
        }
    }

    /** The holograms table, with its viewer and blacklist rows. */
    private static final class HologramDatabase implements HologramRepository {

        private final Map<String, Hologram> rows = new LinkedHashMap<>();
        private final Map<String, Set<UUID>> viewers = new LinkedHashMap<>();
        private final Map<String, Set<UUID>> blacklist = new LinkedHashMap<>();

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
            return Set.copyOf(viewers.getOrDefault(name.value(), Set.of()));
        }

        @Override
        public void showTo(HologramName name, UUID viewer) {
            viewers.computeIfAbsent(name.value(), ignored -> new HashSet<>()).add(viewer);
        }

        @Override
        public void hideFrom(HologramName name, UUID viewer) {
            viewers.getOrDefault(name.value(), new HashSet<>()).remove(viewer);
        }

        @Override
        public Set<UUID> blacklisted(HologramName name) {
            return Set.copyOf(blacklist.getOrDefault(name.value(), Set.of()));
        }

        @Override
        public void addToBlacklist(HologramName name, UUID viewer) {
            blacklist.computeIfAbsent(name.value(), ignored -> new HashSet<>()).add(viewer);
        }

        @Override
        public void removeFromBlacklist(HologramName name, UUID viewer) {
            blacklist.getOrDefault(name.value(), new HashSet<>()).remove(viewer);
        }
    }

    /** The managed worlds table. */
    private static final class WorldDatabase implements WorldRepository {

        private final Map<WorldName, ManagedWorld> rows = new LinkedHashMap<>();

        @Override
        public Optional<ManagedWorld> find(WorldName name) {
            return Optional.ofNullable(rows.get(name));
        }

        @Override
        public List<ManagedWorld> all() {
            return List.copyOf(rows.values());
        }

        @Override
        public boolean exists(WorldName name) {
            return rows.containsKey(name);
        }

        @Override
        public void save(ManagedWorld world) {
            rows.put(world.name(), world);
        }

        @Override
        public void delete(WorldName name) {
            rows.remove(name);
        }
    }
}
