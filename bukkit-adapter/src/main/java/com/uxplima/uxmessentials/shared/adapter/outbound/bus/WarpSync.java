package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.persistence.warps.CachedWarpRepository;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.network.WarpChanged;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import org.jspecify.annotations.NullMarked;

/**
 * The warps context's cross-server sync seam, the same shape as {@link HomeSync} and {@link WalletSync},
 * applied to the server-wide warp set:
 *
 * <ul>
 *   <li><b>Outbound</b>: {@link #repository(CachedWarpRepository, BusPublisher)} wraps the cached repository
 *       so every local warp write ({@code /setwarp}, {@code /delwarp}, move) publishes a {@link WarpChanged}
 *       frame after the durable write commits: peers learn the warp set changed.
 *   <li><b>Inbound</b>: {@link #listener(CachedWarpRepository, Scheduler)} returns a {@link RemoteSyncListener}
 *       that, on a remote {@code WarpChanged}, loads the whole set again on the async lane and swaps it in.
 * </ul>
 *
 * <p>Warps are server-wide rather than per-owner, so the whole set is the reload unit, and the listener uses
 * {@link CachedWarpRepository#reloadAll()}, as the periodic resync does, rather than dropping the set: readers keep
 * the old set until the fresh one is in, so no {@code /warp} or tab complete loads the warps on the tick. The frame
 * still carries the changed name for audit context. The decorator wraps the <em>same</em> cache the warp commands
 * read, so the loop closes: a write here emits a frame, the peer's listener reloads its set there.
 */
@NullMarked
public final class WarpSync {

    private WarpSync() {}

    /** A {@link WarpRepository} that broadcasts a {@link WarpChanged} after every local write. */
    public static WarpRepository repository(CachedWarpRepository delegate, BusPublisher bus) {
        Objects.requireNonNull(delegate, "delegate");
        Objects.requireNonNull(bus, "bus");
        return new Broadcasting(delegate, bus);
    }

    /** A listener that reloads the cached warp set on the async lane on a remote {@link WarpChanged}. */
    public static RemoteSyncListener listener(CachedWarpRepository cache, Scheduler scheduler) {
        Objects.requireNonNull(cache, "cache");
        Objects.requireNonNull(scheduler, "scheduler");
        return message -> {
            if (message instanceof WarpChanged) {
                scheduler.async(() -> cache.reloadAll());
            }
        };
    }

    /** Forwards every call to the cached delegate, then announces each mutation on the bus. */
    private static final class Broadcasting implements WarpRepository {

        private final WarpRepository delegate;
        private final BusPublisher bus;

        Broadcasting(WarpRepository delegate, BusPublisher bus) {
            this.delegate = delegate;
            this.bus = bus;
        }

        @Override
        public Optional<Warp> find(WarpName name) {
            return delegate.find(name);
        }

        @Override
        public List<Warp> all() {
            return delegate.all();
        }

        @Override
        public boolean exists(WarpName name) {
            return delegate.exists(name);
        }

        @Override
        public void save(Warp warp) {
            delegate.save(warp);
            announce(warp.name());
        }

        @Override
        public void delete(WarpName name) {
            delegate.delete(name);
            announce(name);
        }

        @Override
        public void rate(WarpName name, java.util.UUID player, double rating) {
            delegate.rate(name, player, rating);
        }

        @Override
        public double averageRating(WarpName name) {
            return delegate.averageRating(name);
        }

        private void announce(WarpName name) {
            bus.publish(new WarpChanged(bus.serverId(), name.value()));
        }
    }
}
