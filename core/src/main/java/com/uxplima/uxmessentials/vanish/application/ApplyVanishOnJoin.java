package com.uxplima.uxmessentials.vanish.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.shared.application.port.NetworkPresence;
import com.uxplima.uxmessentials.shared.domain.OnlinePlayer;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vanish.application.port.VanishBus;
import com.uxplima.uxmessentials.vanish.application.port.VanishLevelResolver;
import com.uxplima.uxmessentials.vanish.application.port.VanishStore;
import com.uxplima.uxmessentials.vanish.application.port.VanishView;
import com.uxplima.uxmessentials.vanish.domain.StoredVanish;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;

/**
 * Settles a joining player's vanish before anyone else sees them.
 *
 * <p>A vanish ends when the player leaves the network unless they may persist it. The kept state carries over only when
 * {@link StoredVanish#restores} says so. On a server switch the proxy keeps the old connection until the new login
 * succeeds, so the login read here sees no departure from the origin yet; that counts as a switch when
 * {@link NetworkPresence} still sees the player on another backend, or when the row shows their last backend confirmed
 * them within the hop window. That sighting lives in the database, so it holds whether or not the transport reaches this
 * backend, and it goes stale once the backend that wrote it stops, so a crash cannot keep a player hidden for long. A
 * peer that is hiding the player right now, or let them go within the hop window, as the network view reports, counts
 * too. A kept vanish that does not carry over is cleared, so it cannot come back later.
 *
 * <p>Once seeded, a joiner who may rejoin vanished is hidden if they are not already, and otherwise a vanished joiner
 * has their level re-derived from this backend's permissions, which also re-applies the hide and the buffs and tells
 * the peers where they now are; this backend then claims the kept row.
 *
 * <p>The kept state is normally read during login. When that read did not finish in time, a joiner who may vanish is
 * hidden for now and their join line stays quiet, so a vanished player never flashes into view; {@link #lateLoad}
 * then decides, and reveals them if the kept state does not carry over, telling the peers too in case this backend
 * announced them meanwhile.
 */
public final class ApplyVanishOnJoin {

    private final JoinVanishReconciler reconciler;
    private final ToggleVanish toggleVanish;
    private final SetVanishLevel setVanishLevel;
    private final RememberVanish remember;
    private final VanishStore store;
    private final VanishView view;
    private final VanishLevelResolver levels;
    private final VanishBus bus;
    private final NetworkPresence presence;
    private final Clock clock;
    private final Duration hopWindow;
    private final Set<UUID> provisional = ConcurrentHashMap.newKeySet();

    public ApplyVanishOnJoin(
            JoinVanishReconciler reconciler,
            ToggleVanish toggleVanish,
            SetVanishLevel setVanishLevel,
            RememberVanish remember,
            VanishStore store,
            VanishView view,
            VanishLevelResolver levels,
            VanishBus bus,
            NetworkPresence presence,
            Clock clock,
            Duration hopWindow) {
        this.reconciler = Objects.requireNonNull(reconciler, "reconciler");
        this.toggleVanish = Objects.requireNonNull(toggleVanish, "toggleVanish");
        this.setVanishLevel = Objects.requireNonNull(setVanishLevel, "setVanishLevel");
        this.remember = Objects.requireNonNull(remember, "remember");
        this.store = Objects.requireNonNull(store, "store");
        this.view = Objects.requireNonNull(view, "view");
        this.levels = Objects.requireNonNull(levels, "levels");
        this.bus = Objects.requireNonNull(bus, "bus");
        this.presence = Objects.requireNonNull(presence, "presence");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.hopWindow = Objects.requireNonNull(hopWindow, "hopWindow");
    }

    /**
     * Settle {@code joiner}'s vanish from the state the login read at {@code readAt} ({@code stored}, empty when they
     * have none). The origin's sighting is judged at {@code readAt}, when the player was still on it, so a slow join
     * cannot age a switch out. {@code persist} says whether they may keep their vanish across leaving the network, and
     * {@code joinVanished} whether they rejoin vanished every time. Returns whether they ended up vanished.
     */
    public boolean join(
            PlayerRef joiner, Optional<StoredVanish> stored, Instant readAt, boolean persist, boolean joinVanished) {
        Objects.requireNonNull(joiner, "joiner");
        Objects.requireNonNull(stored, "stored");
        Objects.requireNonNull(readAt, "readAt");
        provisional.remove(joiner.uuid());
        reconciler.reconcile(joiner.uuid(), carried(joiner.uuid(), stored, readAt, persist));
        settle(joiner, joinVanished);
        boolean vanished = store.isVanished(joiner.uuid());
        afterDecision(joiner, stored, vanished);
        return vanished;
    }

    /**
     * Settle {@code joiner}'s vanish when the login read did not finish in time. The network view and the join-vanished
     * rule apply at once; a joiner who may vanish ({@code canVanish}) and is still visible is hidden until
     * {@link #lateLoad} decides. Returns whether they are hidden now.
     */
    public boolean joinUnread(PlayerRef joiner, boolean joinVanished, boolean canVanish) {
        Objects.requireNonNull(joiner, "joiner");
        provisional.remove(joiner.uuid());
        reconciler.reconcile(joiner.uuid(), Optional.empty());
        if (canVanish && !joinVanished && !store.isVanished(joiner.uuid())) {
            VanishLevel level = levels.useLevel(joiner);
            store.vanish(joiner.uuid(), level);
            view.hide(joiner, level);
            provisional.add(joiner.uuid());
            return true;
        }
        settle(joiner, joinVanished);
        return store.isVanished(joiner.uuid());
    }

    /**
     * Apply a kept state that was read after {@code joiner} had already joined. Returns whether they are vanished
     * afterwards. A change the player made on this backend since joining is newer than anything the read returned, so
     * it stands; otherwise the kept state decides, and a joiner hidden only until now is revealed when it does not
     * carry over.
     */
    public boolean lateLoad(PlayerRef joiner, Optional<StoredVanish> stored, boolean persist) {
        Objects.requireNonNull(joiner, "joiner");
        Objects.requireNonNull(stored, "stored");
        boolean wasProvisional = provisional.remove(joiner.uuid());
        if (remember.changedHere(joiner.uuid()) || (store.isVanished(joiner.uuid()) && !wasProvisional)) {
            return store.isVanished(joiner.uuid());
        }
        Optional<StoredVanish> carried = carried(joiner.uuid(), stored, clock.instant(), persist);
        if (carried.isPresent()) {
            reconciler.reconcile(joiner.uuid(), carried);
            setVanishLevel.reapply(joiner);
        } else if (wasProvisional) {
            store.reveal(joiner.uuid());
            view.reveal(joiner);
            bus.publish(VanishSync.revealed(joiner));
        }
        boolean vanished = store.isVanished(joiner.uuid());
        afterDecision(joiner, stored, vanished);
        return vanished;
    }

    /**
     * The kept state could not be read at all. A joiner hidden only until now stays hidden, the safe side for a player
     * who may vanish, and their vanish is settled properly; they reveal themselves with {@code /vanish}.
     */
    public void readFailed(PlayerRef joiner) {
        Objects.requireNonNull(joiner, "joiner");
        if (provisional.remove(joiner.uuid()) && !remember.changedHere(joiner.uuid())) {
            setVanishLevel.reapply(joiner);
        }
    }

    private void settle(PlayerRef joiner, boolean joinVanished) {
        if (joinVanished && !store.isVanished(joiner.uuid())) {
            toggleVanish.setVanished(joiner, true);
        } else {
            setVanishLevel.reapply(joiner);
        }
    }

    private Optional<StoredVanish> carried(
            UUID player, Optional<StoredVanish> stored, Instant readAt, boolean persist) {
        return stored.filter(
                state -> state.restores(persist, switching(player, state, readAt), clock.instant(), hopWindow));
    }

    /** Whether the player was still on their way from another backend when the row was read at {@code readAt}. */
    private boolean switching(UUID player, StoredVanish state, Instant readAt) {
        String here = presence.localServer();
        Optional<OnlinePlayer> where = presence.find(player);
        if (where.isPresent() && !where.get().server().equals(here)) {
            return true;
        }
        boolean fromElsewhere =
                state.server().filter(server -> !server.equals(here)).isPresent();
        return fromElsewhere && state.seenWithin(readAt, hopWindow);
    }

    private void afterDecision(PlayerRef joiner, Optional<StoredVanish> stored, boolean vanished) {
        if (vanished) {
            remember.arrived(joiner.uuid());
        } else if (stored.filter(StoredVanish::vanished).isPresent()) {
            remember.clearStored(joiner.uuid());
        }
    }
}
