package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.RecentIds;
import com.uxplima.uxmessentials.shared.network.WorldChanged;
import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.NetworkWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldName;
import org.jspecify.annotations.NullMarked;

/**
 * The worlds context's cross-server sync seam, the same shape as {@link WarpSync}:
 *
 * <ul>
 *   <li><b>Outbound</b>: {@link #repository} wraps the cached repository so every local world write (create, import,
 *       adopt, a setting, unregister, delete) publishes a {@link WorldChanged} after the durable write commits.
 *   <li><b>Inbound</b>: {@link #listener} reloads the cached set once per change a peer reports. With
 *       {@code network.transport = both} each frame arrives twice, once per transport, so a change id already acted
 *       on is dropped.
 * </ul>
 *
 * <p>The periodic resync reloads the same set, so a frame lost on the way only delays a peer until then.
 */
@NullMarked
public final class WorldSync {

    private WorldSync() {}

    /** A {@link WorldRepository} that broadcasts a {@link WorldChanged} after every local write. */
    public static WorldRepository repository(WorldRepository delegate, BusPublisher bus) {
        Objects.requireNonNull(delegate, "delegate");
        Objects.requireNonNull(bus, "bus");
        return new Broadcasting(delegate, bus);
    }

    /** Announce a change made outside the repository, such as a claim, under a fresh change id. */
    public static void announce(BusPublisher bus, String world) {
        Objects.requireNonNull(bus, "bus");
        Objects.requireNonNull(world, "world");
        bus.publish(new WorldChanged(bus.serverId(), UUID.randomUUID(), bus.serverId(), world));
    }

    /**
     * A listener that runs {@code reload} once for each change a peer reports. {@code reload} is run on the bus's
     * dispatch thread, so it should hand the database read to the async lane itself.
     */
    public static RemoteSyncListener listener(Runnable reload) {
        Objects.requireNonNull(reload, "reload");
        RecentIds seen = new RecentIds();
        return message -> {
            if (message instanceof WorldChanged changed && seen.firstTime(changed.changeId())) {
                reload.run();
            }
        };
    }

    /** Forwards every call to the cached delegate, then announces each mutation on the bus. */
    private static final class Broadcasting implements WorldRepository {

        private final WorldRepository delegate;
        private final BusPublisher bus;

        Broadcasting(WorldRepository delegate, BusPublisher bus) {
            this.delegate = delegate;
            this.bus = bus;
        }

        @Override
        public Optional<ManagedWorld> find(WorldName name) {
            return delegate.find(name);
        }

        @Override
        public List<ManagedWorld> all() {
            return delegate.all();
        }

        @Override
        public boolean exists(WorldName name) {
            return delegate.exists(name);
        }

        @Override
        public void save(ManagedWorld world) {
            delegate.save(world);
            announce(bus, world.name().value());
        }

        @Override
        public void delete(WorldName name) {
            delegate.delete(name);
            announce(bus, name.value());
        }

        @Override
        public List<NetworkWorld> network() {
            return delegate.network();
        }
    }
}
