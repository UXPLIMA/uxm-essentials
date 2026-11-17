package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import com.uxplima.uxmessentials.persistence.skin.CachedSkinRepository;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.network.PlayerSkinChanged;
import com.uxplima.uxmessentials.skin.application.DressLogin;
import com.uxplima.uxmessentials.skin.application.port.SkinRepository;
import com.uxplima.uxmessentials.skin.application.port.SkinView;
import com.uxplima.uxmessentials.skin.domain.PlayerSkin;
import org.jspecify.annotations.NullMarked;

/**
 * The skin context's cross-server sync seam, the write-decorator-plus-listener shape of {@link HologramSync}. A skin
 * is both a cached row and something a player wears in the world, so a peer does more than drop its cache entry: the
 * backend the player is on dresses them again.
 *
 * <ul>
 *   <li><b>Outbound</b>: {@link #repository} wraps the cached repository so every save and delete (a set, an update,
 *       a clear, a drop, a Bedrock skin written at login) announces a {@link PlayerSkinChanged} after the write.
 *   <li><b>Inbound</b>: {@link #listener} forgets the player's cached row on every backend, and on the one the player
 *       is online on resolves their skin again through {@link DressLogin}, the stored row first and the join order
 *       when the row is gone, and puts it on them through the {@link SkinView}.
 * </ul>
 */
@NullMarked
public final class SkinSync {

    private SkinSync() {}

    /** A {@link SkinRepository} that broadcasts a {@link PlayerSkinChanged} after every local save and delete. */
    public static SkinRepository repository(CachedSkinRepository delegate, BusPublisher bus, Logger log) {
        Objects.requireNonNull(delegate, "delegate");
        Objects.requireNonNull(bus, "bus");
        Objects.requireNonNull(log, "log");
        return new Broadcasting(delegate, bus, log);
    }

    /**
     * A listener that, on a remote {@link PlayerSkinChanged}, forgets the cached row and, when the player is online
     * here and {@code active} holds, dresses them again. The resolution reads the database and may ask Mojang, so it
     * runs on the async lane; the view hops onto the player's own thread itself.
     */
    public static RemoteSyncListener listener(
            CachedSkinRepository cache,
            DressLogin dressLogin,
            SkinView view,
            PlayerLookup players,
            Scheduler scheduler,
            BooleanSupplier active) {
        Objects.requireNonNull(cache, "cache");
        Objects.requireNonNull(dressLogin, "dressLogin");
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(players, "players");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(active, "active");
        return message -> {
            if (!(message instanceof PlayerSkinChanged changed)) {
                return;
            }
            UUID player = changed.player();
            cache.invalidate(player);
            if (!active.getAsBoolean() || !players.isOnline(player)) {
                return;
            }
            scheduler.async(() -> players.findByUuid(player)
                    .ifPresent(who -> dressLogin
                            .resolve(who.uuid(), who.name())
                            .ifPresent(dressed -> view.apply(who, dressed.texture(), dressed.model()))));
        };
    }

    /** Forwards every call to the cached delegate, then announces each write on the bus. */
    private static final class Broadcasting implements SkinRepository {

        private final SkinRepository delegate;
        private final BusPublisher bus;
        private final Logger log;

        Broadcasting(SkinRepository delegate, BusPublisher bus, Logger log) {
            this.delegate = delegate;
            this.bus = bus;
            this.log = log;
        }

        @Override
        public Optional<PlayerSkin> find(UUID player) {
            return delegate.find(player);
        }

        @Override
        public void save(PlayerSkin skin) {
            delegate.save(skin);
            announce(skin.owner().uuid());
        }

        @Override
        public void delete(UUID player) {
            delegate.delete(player);
            announce(player);
        }

        private void announce(UUID player) {
            // The row is committed by now; a lost announcement must not read as a failed change. Peers still read the
            // fresh row when the player next joins them.
            try {
                bus.publish(new PlayerSkinChanged(bus.serverId(), player));
            } catch (RuntimeException failure) {
                log.error("failed to announce the skin change of player " + player, failure);
            }
        }
    }
}
