package com.uxplima.uxmessentials.shared.adapter.outbound.bus;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.invrollback.application.port.PendingRestores;
import com.uxplima.uxmessentials.invrollback.domain.Snapshot;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.network.InventoryRestoreKept;
import org.jspecify.annotations.NullMarked;

/**
 * The invrollback context's cross-server seam for kept restores, the same shape as the kept rank actions in
 * {@link RankSync}: a store decorator that announces each kept restore as an {@link InventoryRestoreKept} frame, and a
 * listener that, on that frame, puts the kept restore on the player when they are online here. Taking a kept restore
 * removes it, so the backend the player is on and a later join never both apply it.
 */
@NullMarked
public final class RestoreSync {

    private RestoreSync() {}

    /** A {@link PendingRestores} store that announces each kept restore, so the backend the player is on applies it. */
    public static PendingRestores pendingRestores(PendingRestores delegate, BusPublisher bus, Logger log) {
        Objects.requireNonNull(delegate, "delegate");
        Objects.requireNonNull(bus, "bus");
        Objects.requireNonNull(log, "log");
        return new Announcing(delegate, bus, log);
    }

    /**
     * A listener that, on a remote {@link InventoryRestoreKept} for a player online here, hands that player to
     * {@code applyKept}, the same apply a join runs.
     */
    public static RemoteSyncListener listener(Consumer<PlayerRef> applyKept, PlayerLookup players) {
        Objects.requireNonNull(applyKept, "applyKept");
        Objects.requireNonNull(players, "players");
        return message -> {
            if (message instanceof InventoryRestoreKept kept && players.isOnline(kept.player())) {
                players.findByUuid(kept.player()).ifPresent(applyKept);
            }
        };
    }

    /** Forwards to the store, then announces each kept restore on the bus. */
    private static final class Announcing implements PendingRestores {

        private final PendingRestores delegate;
        private final BusPublisher bus;
        private final Logger log;

        Announcing(PendingRestores delegate, BusPublisher bus, Logger log) {
            this.delegate = delegate;
            this.bus = bus;
            this.log = log;
        }

        @Override
        public void keep(UUID player, Snapshot snapshot) {
            delegate.keep(player, snapshot);
            // The row is committed by now. A lost announcement only means the restore waits for the player's next join.
            try {
                bus.publish(new InventoryRestoreKept(bus.serverId(), player));
            } catch (RuntimeException failure) {
                log.error("failed to announce the kept inventory restore of player " + player, failure);
            }
        }

        @Override
        public List<Snapshot> take(UUID player) {
            return delegate.take(player);
        }
    }
}
