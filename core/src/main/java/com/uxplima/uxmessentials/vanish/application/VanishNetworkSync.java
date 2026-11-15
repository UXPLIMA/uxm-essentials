package com.uxplima.uxmessentials.vanish.application;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vanish.application.port.NetworkVanishStore;
import com.uxplima.uxmessentials.vanish.application.port.VanishBus;
import com.uxplima.uxmessentials.vanish.application.port.VanishStore;
import com.uxplima.uxmessentials.vanish.application.port.VanishView;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;

/**
 * Applies what the peer backends send to this backend's vanish state: changes and departures go into the
 * {@link NetworkVanishStore}, a change about a player who happens to be online here also settles their local
 * visibility, and a peer heard from for the first time, or started again, is sent every player this backend is hiding,
 * since its view of the network may have begun empty.
 *
 * <p>The bus calls this on its dispatch thread. The network view is a lock-free map, so it is written inline; any work
 * on a player hops to that player's entity thread, and the announce to a restarted peer runs on the global thread, where
 * the online roster is coherent under Folia.
 */
public final class VanishNetworkSync implements VanishBus.Listener {

    private final NetworkVanishStore network;
    private final VanishStore store;
    private final VanishView view;
    private final VanishBus bus;
    private final PlayerLookup players;
    private final Scheduler scheduler;

    public VanishNetworkSync(
            NetworkVanishStore network,
            VanishStore store,
            VanishView view,
            VanishBus bus,
            PlayerLookup players,
            Scheduler scheduler) {
        this.network = Objects.requireNonNull(network, "network");
        this.store = Objects.requireNonNull(store, "store");
        this.view = Objects.requireNonNull(view, "view");
        this.bus = Objects.requireNonNull(bus, "bus");
        this.players = Objects.requireNonNull(players, "players");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public void changed(String origin, long run, VanishSync change) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(change, "change");
        network.apply(origin, run, change);
        PlayerRef target = new PlayerRef(change.player(), change.playerName());
        // The scheduler drops the task when the player is not online here, which is the usual case.
        scheduler.onEntity(target, () -> reconcileOnline(target, change));
    }

    @Override
    public void departed(String origin, UUID player) {
        network.depart(origin, player);
    }

    @Override
    public void pinged(String origin, long run, VanishBus.PeerRun kind) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(kind, "kind");
        network.heartbeat(origin, run, kind == VanishBus.PeerRun.NEW_RUN);
        if (kind != VanishBus.PeerRun.SAME_RUN) {
            scheduler.onGlobal(this::announceLocal);
        }
    }

    private void reconcileOnline(PlayerRef target, VanishSync change) {
        if (change.vanished()) {
            store.vanish(target.uuid(), change.level());
            view.hide(target, change.level());
        } else {
            store.reveal(target.uuid());
            view.reveal(target);
        }
    }

    private void announceLocal() {
        for (UUID hidden : store.vanished()) {
            Optional<VanishLevel> level = store.levelOf(hidden);
            Optional<PlayerRef> who = players.findByUuid(hidden);
            if (level.isPresent() && who.isPresent()) {
                bus.publish(VanishSync.vanished(who.get(), level.get()));
            }
        }
    }
}
