package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import java.util.Objects;

import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeName;
import com.uxplima.uxmessentials.homes.domain.HomeSet;
import com.uxplima.uxmessentials.persistence.homes.CachedHomeRepository;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.network.HomeChanged;
import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import org.jspecify.annotations.NullMarked;

/**
 * The homes context's cross-server sync seam — one of the two representative wirings (the other is
 * {@link WalletSync}). It does two things, both keyed by the same {@link CachedHomeRepository}:
 *
 * <ul>
 *   <li><b>Outbound</b>: {@link #repository(CachedHomeRepository, BusPublisher)} wraps the cached repository
 *       so every local home write ({@code /sethome}, {@code /delhome}, rename, move) publishes a
 *       {@link HomeChanged} frame after the durable write commits — peers learn the owner's homes changed.
 *   <li><b>Inbound</b>: {@link #listener(CachedHomeRepository)} returns a {@link RemoteSyncListener} that, on
 *       a remote {@code HomeChanged}, invalidates exactly that owner's cached set so the next {@code /home}
 *       on this backend resolves the fresh location from the shared DB.
 * </ul>
 *
 * <p>The decorator wraps the <em>same</em> cache the homes commands read, so the loop closes: a write here
 * emits a frame, the peer's listener drops the matching owner there. Warps and vaults follow this exact shape
 * (a write decorator + an invalidation listener over their own cached repository) — adding a context is
 * copying this class, not changing the bus.
 */
@NullMarked
public final class HomeSync {

    private HomeSync() {}

    /** A {@link HomeRepository} that broadcasts a {@link HomeChanged} after every local write. */
    public static HomeRepository repository(CachedHomeRepository delegate, BusPublisher bus) {
        Objects.requireNonNull(delegate, "delegate");
        Objects.requireNonNull(bus, "bus");
        return new Broadcasting(delegate, bus);
    }

    /** A listener that invalidates the affected owner's cached set on a remote {@link HomeChanged}. */
    public static RemoteSyncListener listener(CachedHomeRepository cache) {
        Objects.requireNonNull(cache, "cache");
        return message -> {
            if (message instanceof HomeChanged changed) {
                cache.invalidateOwner(changed.owner());
            }
        };
    }

    /** Forwards every call to the cached delegate, then announces each mutation on the bus. */
    private static final class Broadcasting implements HomeRepository {

        private final HomeRepository delegate;
        private final BusPublisher bus;

        Broadcasting(HomeRepository delegate, BusPublisher bus) {
            this.delegate = delegate;
            this.bus = bus;
        }

        @Override
        public HomeSet load(PlayerRef owner) {
            return delegate.load(owner);
        }

        @Override
        public int count(PlayerRef owner) {
            return delegate.count(owner);
        }

        @Override
        public void save(Home home) {
            delegate.save(home);
            announce(home.owner());
        }

        @Override
        public void rename(PlayerRef owner, HomeName from, HomeName to) {
            delegate.rename(owner, from, to);
            announce(owner);
        }

        @Override
        public void delete(PlayerRef owner, HomeName name) {
            delegate.delete(owner, name);
            announce(owner);
        }

        private void announce(PlayerRef owner) {
            NetworkMessage frame = new HomeChanged(bus.serverId(), owner.uuid());
            bus.publish(frame);
        }
    }
}
