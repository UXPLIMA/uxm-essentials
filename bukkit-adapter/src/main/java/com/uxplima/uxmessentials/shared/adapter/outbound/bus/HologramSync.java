package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.holograms.adapter.outbound.EventDrivenNpcLocator;
import com.uxplima.uxmessentials.holograms.application.port.HologramRepository;
import com.uxplima.uxmessentials.holograms.application.port.HologramView;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.persistence.holograms.CachedHologramRepository;
import com.uxplima.uxmessentials.persistence.npc.CachedNpcRepository;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.network.HologramChanged;
import com.uxplima.uxmessentials.shared.network.NpcChanged;
import org.jspecify.annotations.NullMarked;

/**
 * The holograms context's cross-server sync seam. It is the same write-decorator-plus-listener shape as
 * {@link HomeSync} and {@link WarpSync}, but holograms are not merely a cached set: a hologram is its own
 * in-world display entity on every backend, so a peer must do more than drop a cache entry on a remote change
 * it must reload the named hologram from the shared DB and re-render the live display so the world matches.
 *
 * <ul>
 *   <li><b>Outbound</b>: {@link #repository(CachedHologramRepository, BusPublisher)} wraps the cached repository
 *       so every local write that changes a hologram, a {@code save} (a create, a move, a line edit, an
 *       appearance/visibility/model/page/leaderboard/action change all upsert the same row through {@code save}),
 *       a {@code delete}, a manual {@code showTo}/{@code hideFrom}, or a blacklist add/remove, publishes a
 *       {@link HologramChanged} naming that hologram after the durable write commits.
 *   <li><b>Inbound</b>: {@link #listener(CachedHologramRepository, HologramView, Scheduler)} returns a
 *       {@link RemoteSyncListener} that, on a remote {@code HologramChanged}, reloads exactly that hologram from
 *       the shared DB into the in-memory set and re-renders it: a hologram that now exists (created or edited on
 *       the peer) is rendered through {@link HologramView#render}, the same remove-then-spawn the local edit
 *       path runs, and one that no longer exists (deleted on the peer) is despawned through
 *       {@link HologramView#despawn}, exactly as the local delete path does.
 * </ul>
 *
 * <p>The reload is a synchronous SQLite read, so the listener hops onto the injected {@link Scheduler}'s async
 * thread first (a remote frame is delivered off-tick, but the async hop keeps the DB read off any tick thread
 * regardless of the transport's delivery thread). It then calls {@code render}/{@code despawn}, which the
 * renderer routes onto the hologram's own region thread internally (Folia). The same scheduling the local
 * {@code /hologram} edit path relies on, so the remote re-render and the local one converge to one in-world
 * result. The decorator wraps the <em>same</em> cache the {@code /hologram} commands and the renderer read, so
 * the loop closes: a write here emits a frame, the peer reloads and re-renders that hologram there.
 */
@NullMarked
public final class HologramSync {

    private HologramSync() {}

    /** A {@link HologramRepository} that broadcasts a {@link HologramChanged} after every local mutating write. */
    public static HologramRepository repository(CachedHologramRepository delegate, BusPublisher bus) {
        Objects.requireNonNull(delegate, "delegate");
        Objects.requireNonNull(bus, "bus");
        return new Broadcasting(delegate, bus);
    }

    /**
     * A listener that, on a remote {@link HologramChanged}, reloads the named hologram from the shared DB and
     * re-renders it (or despawns it when it was deleted), off the tick thread via {@code scheduler}.
     */
    public static RemoteSyncListener listener(
            CachedHologramRepository cache, HologramView renderer, Scheduler scheduler) {
        Objects.requireNonNull(cache, "cache");
        Objects.requireNonNull(renderer, "renderer");
        Objects.requireNonNull(scheduler, "scheduler");
        return message -> {
            if (!(message instanceof HologramChanged changed)) {
                return;
            }
            HologramName name = HologramName.of(changed.name());
            // Reload off the tick thread, the cache reload is a synchronous SQLite read, then re-render through
            // the renderer, which hops onto the hologram's own region thread itself (the local edit path's route).
            scheduler.async(() -> reRender(cache, renderer, name));
        };
    }

    /**
     * A resync for {@link SharedCacheRefresh}: reload the whole set from the shared DB without emptying it, then
     * render each hologram a missed frame left stale and despawn each one deleted meanwhile. A missed show, hide or
     * blacklist edit leaves the hologram row unchanged, so the viewer lists held in memory are read again as well and
     * a hologram whose lists changed is redrawn for its new viewers. Holograms that did not change are not redrawn.
     * It runs on the async lane, as the listener's reload does.
     */
    public static Runnable resync(CachedHologramRepository cache, HologramView renderer) {
        Objects.requireNonNull(cache, "cache");
        Objects.requireNonNull(renderer, "renderer");
        return () -> {
            Set<String> redrawn = new HashSet<>();
            cache.reloadAll().ifPresent(swap -> {
                for (String key : swap.changedKeys()) {
                    redrawn.add(key);
                    Hologram fresh = swap.after().get(key);
                    if (fresh != null) {
                        renderer.render(fresh);
                    } else {
                        renderer.despawn(HologramName.of(key));
                    }
                }
            });
            for (String name : cache.refreshViewerLists()) {
                if (!redrawn.contains(name)) {
                    cache.find(HologramName.of(name)).ifPresent(renderer::render);
                }
            }
        };
    }

    /**
     * A listener that, on a remote {@link NpcChanged}, reloads the named NPC into the holograms module's own copy
     * of the NPCs and hands it to {@code locator}, so a hologram riding it here follows a move, a rescope or a delete
     * made on another server. The reload reads the database, so it runs on the async lane.
     */
    public static RemoteSyncListener npcListener(
            CachedNpcRepository npcs, EventDrivenNpcLocator locator, Scheduler scheduler) {
        Objects.requireNonNull(npcs, "npcs");
        Objects.requireNonNull(locator, "locator");
        Objects.requireNonNull(scheduler, "scheduler");
        return message -> {
            if (!(message instanceof NpcChanged changed)) {
                return;
            }
            NpcName name = NpcName.of(changed.name());
            scheduler.async(() -> locator.refresh(name.value(), npcs.reload(name)));
        };
    }

    /**
     * A resync for {@link SharedCacheRefresh}: reload the holograms module's copy of the NPCs and hand each NPC a
     * missed frame left stale to {@code locator}. It runs on the async lane, as the listener's reload does.
     */
    public static Runnable npcResync(CachedNpcRepository npcs, EventDrivenNpcLocator locator) {
        Objects.requireNonNull(npcs, "npcs");
        Objects.requireNonNull(locator, "locator");
        return () -> npcs.reloadAll().ifPresent(swap -> {
            for (String key : swap.changedKeys()) {
                locator.refresh(key, Optional.ofNullable(swap.after().get(key)));
            }
        });
    }

    /** Reload the named hologram from the shared DB and render it if present, despawn it if it was deleted. */
    private static void reRender(CachedHologramRepository cache, HologramView renderer, HologramName name) {
        Optional<Hologram> fresh = cache.reload(name);
        if (fresh.isPresent()) {
            renderer.render(fresh.get());
        } else {
            renderer.despawn(name);
        }
    }

    /** Forwards every call to the cached delegate, then announces each mutation on the bus. */
    private static final class Broadcasting implements HologramRepository {

        private final HologramRepository delegate;
        private final BusPublisher bus;

        Broadcasting(HologramRepository delegate, BusPublisher bus) {
            this.delegate = delegate;
            this.bus = bus;
        }

        @Override
        public Optional<Hologram> find(HologramName name) {
            return delegate.find(name);
        }

        @Override
        public List<Hologram> all() {
            return delegate.all();
        }

        @Override
        public boolean exists(HologramName name) {
            return delegate.exists(name);
        }

        @Override
        public void save(Hologram hologram) {
            delegate.save(hologram);
            announce(hologram.name());
        }

        @Override
        public void delete(HologramName name) {
            delegate.delete(name);
            announce(name);
        }

        @Override
        public Set<UUID> manualViewers(HologramName name) {
            return delegate.manualViewers(name);
        }

        @Override
        public void showTo(HologramName name, UUID viewer) {
            delegate.showTo(name, viewer);
            announce(name);
        }

        @Override
        public void hideFrom(HologramName name, UUID viewer) {
            delegate.hideFrom(name, viewer);
            announce(name);
        }

        @Override
        public Set<UUID> blacklisted(HologramName name) {
            return delegate.blacklisted(name);
        }

        @Override
        public void addToBlacklist(HologramName name, UUID viewer) {
            delegate.addToBlacklist(name, viewer);
            announce(name);
        }

        @Override
        public void removeFromBlacklist(HologramName name, UUID viewer) {
            delegate.removeFromBlacklist(name, viewer);
            announce(name);
        }

        private void announce(HologramName name) {
            bus.publish(new HologramChanged(bus.serverId(), name.value()));
        }
    }
}
