package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.persistence.playerwarps.CachedPlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import com.uxplima.uxmessentials.shared.network.PlayerWarpChanged;
import org.jspecify.annotations.NullMarked;

/**
 * The player-warps context's cross-server sync seam — the same shape as {@link HomeSync}, keyed by the same
 * {@link CachedPlayerWarpRepository} the {@code /pwarp} commands read. It does two things:
 *
 * <ul>
 *   <li><b>Outbound</b>: {@link #repository(CachedPlayerWarpRepository, BusPublisher)} wraps the cached
 *       repository so every local player-warp write (a {@code save} — a {@code /setpwarp}, a move/relocate, or
 *       a visibility flip all upsert the same row — or a {@code delete}) publishes a {@link PlayerWarpChanged}
 *       frame after the durable write commits, so peers learn the owner's warps changed.
 *   <li><b>Inbound</b>: {@link #listener(CachedPlayerWarpRepository)} returns a {@link RemoteSyncListener}
 *       that, on a remote {@code PlayerWarpChanged}, invalidates exactly that owner's cached set so the next
 *       {@code /pwarp} on this backend resolves the fresh warp from the shared DB.
 * </ul>
 *
 * <p>The decorator wraps the <em>same</em> cache the player-warp commands read, so the loop closes: a write
 * here emits a frame, the peer's listener drops the matching owner there. This is {@link HomeSync} copied for
 * the per-owner player-warp set — a write decorator plus an invalidation listener over its own cached
 * repository.
 */
@NullMarked
public final class PlayerWarpSync {

    private PlayerWarpSync() {}

    /** A {@link PlayerWarpRepository} that broadcasts a {@link PlayerWarpChanged} after every local write. */
    public static PlayerWarpRepository repository(CachedPlayerWarpRepository delegate, BusPublisher bus) {
        Objects.requireNonNull(delegate, "delegate");
        Objects.requireNonNull(bus, "bus");
        return new Broadcasting(delegate, bus);
    }

    /** A listener that invalidates the affected owner's cached set on a remote {@link PlayerWarpChanged}. */
    public static RemoteSyncListener listener(CachedPlayerWarpRepository cache) {
        Objects.requireNonNull(cache, "cache");
        return message -> {
            if (message instanceof PlayerWarpChanged changed) {
                cache.invalidateOwner(changed.owner());
            }
        };
    }

    /** Forwards every call to the cached delegate, then announces each mutation on the bus. */
    private static final class Broadcasting implements PlayerWarpRepository {

        private final PlayerWarpRepository delegate;
        private final BusPublisher bus;

        Broadcasting(PlayerWarpRepository delegate, BusPublisher bus) {
            this.delegate = delegate;
            this.bus = bus;
        }

        @Override
        public Optional<PlayerWarp> find(PlayerRef owner, PlayerWarpName name) {
            return delegate.find(owner, name);
        }

        @Override
        public List<PlayerWarp> ownedBy(PlayerRef owner) {
            return delegate.ownedBy(owner);
        }

        @Override
        public List<PlayerWarp> all() {
            return delegate.all();
        }

        @Override
        public List<PlayerWarp> publicOf(PlayerRef owner) {
            return delegate.publicOf(owner);
        }

        @Override
        public int count(PlayerRef owner) {
            return delegate.count(owner);
        }

        @Override
        public boolean exists(PlayerRef owner, PlayerWarpName name) {
            return delegate.exists(owner, name);
        }

        @Override
        public void save(PlayerWarp warp) {
            delegate.save(warp);
            announce(warp.owner());
        }

        @Override
        public void delete(PlayerRef owner, PlayerWarpName name) {
            delegate.delete(owner, name);
            announce(owner);
        }

        @Override
        public Optional<List<PlayerWarp>> peekOwned(PlayerRef owner) {
            return delegate.peekOwned(owner);
        }

        @Override
        public void recordVisit(PlayerRef owner, PlayerWarpName name) {
            // A visit count is high-frequency, eventually-consistent data; letting peers drift a few visits
            // behind until the owner's next real change is fine, and not worth a cluster-wide invalidation per
            // teleport, so this forwards to the delegate without announcing.
            delegate.recordVisit(owner, name);
        }

        @Override
        public void rate(PlayerRef owner, PlayerWarpName name, java.util.UUID player, double rating) {
            // A rating is not part of the cached owner set (the cache reads averageRating from the delegate, not
            // the cached map), so the cached repository does not invalidate on rate — there is nothing for a peer
            // to drop, so this write does not announce.
            delegate.rate(owner, name, player, rating);
        }

        @Override
        public double averageRating(PlayerRef owner, PlayerWarpName name) {
            return delegate.averageRating(owner, name);
        }

        private void announce(PlayerRef owner) {
            NetworkMessage frame = new PlayerWarpChanged(bus.serverId(), owner.uuid());
            bus.publish(frame);
        }
    }
}
