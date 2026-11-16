package com.uxplima.uxmessentials.teleport.application;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.application.port.WorldLookup;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.application.port.JailGate;
import com.uxplima.uxmessentials.teleport.application.port.LogoutPositions;
import com.uxplima.uxmessentials.teleport.application.port.LogoutPositions.SavedSpot;
import com.uxplima.uxmessentials.teleport.application.port.PendingRelocations;
import com.uxplima.uxmessentials.teleport.application.port.RequestVisibility;
import com.uxplima.uxmessentials.teleport.application.port.TeleportExecutor;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.PendingRelocation;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import com.uxplima.uxmessentials.teleport.domain.TeleportMessageKey;

/**
 * {@code /tpoffline} and {@code /tpofflinehere}, for a player who is not on this server.
 *
 * <p>{@code /tpoffline} reads the position this server saved in the player's data when they left it, on the async
 * lane, and moves the staff member on their own thread. {@code /tpofflinehere} never touches that data: it stores a
 * relocation for this backend, which {@link #arrive} applies once the player has joined here, after the server has
 * loaded and placed them. Applying it claims the stored row first, so it happens once however quickly the player
 * joins again. A hop that misses (the player left again, or another plugin cancelled the teleport) puts the row back
 * for the next join, up to {@link #MAX_MISSES} times, and then drops it.
 *
 * <p>A jailed player is never moved: the relocation waits, untouched, for a join after their release, through the
 * same jail gate an accepted request asks.
 *
 * <p>A player online here whom the staff member can see is refused: their saved data is stale while they play, and
 * they can be moved directly. A player the staff member may not see because of vanish is handled exactly as if they
 * were offline, so no answer gives them away.
 */
public final class OfflineTeleport {

    /** How many joins may miss a relocation before it is dropped. */
    public static final int MAX_MISSES = 3;

    private final LogoutPositions positions;
    private final PendingRelocations relocations;
    private final PlayerLookup players;
    private final WorldLookup worlds;
    private final RequestVisibility visibility;
    private final JailGate jail;
    private final TeleportExecutor executor;
    private final Scheduler scheduler;
    private final Notifier notifier;
    private final Clock clock;
    private final String server;
    private final Logger log;

    public OfflineTeleport(
            LogoutPositions positions,
            PendingRelocations relocations,
            PlayerLookup players,
            WorldLookup worlds,
            RequestVisibility visibility,
            JailGate jail,
            TeleportExecutor executor,
            Scheduler scheduler,
            Notifier notifier,
            Clock clock,
            String server,
            Logger log) {
        this.positions = Objects.requireNonNull(positions, "positions");
        this.relocations = Objects.requireNonNull(relocations, "relocations");
        this.players = Objects.requireNonNull(players, "players");
        this.worlds = Objects.requireNonNull(worlds, "worlds");
        this.visibility = Objects.requireNonNull(visibility, "visibility");
        this.jail = Objects.requireNonNull(jail, "jail");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.server = Objects.requireNonNull(server, "server");
        this.log = Objects.requireNonNull(log, "log");
    }

    /** Send {@code actor} to where the player named {@code name} logged out of this server. */
    public void goTo(PlayerRef actor, String name) {
        Objects.requireNonNull(actor, "actor");
        offline(actor, name)
                .ifPresent(target -> scheduler.async(() -> {
                    Optional<SavedSpot> saved;
                    try {
                        saved = positions.read(target.uuid());
                    } catch (RuntimeException unreadable) {
                        // The reader has logged why; the staff member hears that it could not be read, not that it is
                        // missing.
                        scheduler.onEntity(
                                actor, () -> tell(actor, TeleportMessageKey.TPOFFLINE_FAILED, target.name()));
                        return;
                    }
                    scheduler.onEntity(actor, () -> land(actor, target, saved));
                }));
    }

    /**
     * Move the player named {@code name} to {@code here}, where {@code actor} stands on the ground, when they next join
     * this server. A later call replaces an earlier one.
     */
    public void bring(PlayerRef actor, String name, Position here) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(here, "here");
        offline(actor, name).ifPresent(target -> {
            PendingRelocation relocation = new PendingRelocation(
                    target.uuid(),
                    server,
                    here.world().uid(),
                    here.x(),
                    here.y(),
                    here.z(),
                    here.yaw(),
                    here.pitch(),
                    clock.instant(),
                    actor.uuid(),
                    0);
            scheduler.async(() -> {
                TeleportMessageKey reply = TeleportMessageKey.TPOFFLINE_MOVED;
                try {
                    relocations.save(relocation);
                } catch (RuntimeException failed) {
                    log.error("the relocation of " + target.name() + " could not be stored", failed);
                    reply = TeleportMessageKey.TPOFFLINE_FAILED;
                }
                TeleportMessageKey answer = reply;
                scheduler.onEntity(actor, () -> tell(actor, answer, target.name()));
            });
        });
    }

    /**
     * Apply the relocation waiting for {@code who} on this server, once they have joined. When none is applied (there
     * is none, it waits for a release from jail or for its world, or it could not be read), {@code otherwise} runs on
     * the player's thread instead: the join's own move, which a relocation replaces, so the two never race.
     */
    public void arrive(PlayerRef who, Runnable otherwise) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(otherwise, "otherwise");
        scheduler.async(() -> {
            Optional<PendingRelocation> waiting = waitingFor(who);
            if (waiting.isEmpty()) {
                scheduler.onEntity(who, otherwise);
                return;
            }
            if (jail.isJailed(who)) {
                log.info("event=relocation_held_jailed player={}", who.name());
                scheduler.onEntity(who, otherwise);
                return;
            }
            PendingRelocation relocation = waiting.get();
            scheduler.onEntity(who, () -> placeIfLoaded(who, relocation, otherwise));
        });
    }

    private Optional<PendingRelocation> waitingFor(PlayerRef who) {
        try {
            return relocations.find(who.uuid(), server);
        } catch (RuntimeException failed) {
            log.error("the relocation of " + who.name() + " could not be read", failed);
            return Optional.empty();
        }
    }

    /** On the player's thread: a world that is not loaded leaves the row for a later join, when it may be. */
    private void placeIfLoaded(PlayerRef who, PendingRelocation relocation, Runnable otherwise) {
        Optional<WorldRef> world = worlds.findByUid(relocation.world());
        if (world.isEmpty()) {
            log.info("event=relocation_world_missing player={} world={}", who.name(), relocation.world());
            otherwise.run();
            return;
        }
        Position at = new Position(
                world.get(), relocation.x(), relocation.y(), relocation.z(), relocation.yaw(), relocation.pitch());
        scheduler.async(() -> {
            if (!claimed(relocation)) {
                scheduler.onEntity(who, otherwise);
                return;
            }
            executor.teleport(
                    who, Destination.at(at), TeleportKind.ADMIN, OfflineTeleport::landed, () -> missed(relocation));
        });
    }

    private boolean claimed(PendingRelocation relocation) {
        try {
            return relocations.claim(relocation);
        } catch (RuntimeException failed) {
            log.error("the relocation of " + relocation.player() + " could not be claimed", failed);
            return false;
        }
    }

    /**
     * A hop that missed after the claim keeps the relocation for the next join, counting the miss; one that keeps
     * missing, as a teleport another plugin always cancels does, is dropped rather than tried on every join.
     */
    private void missed(PendingRelocation relocation) {
        PendingRelocation again = relocation.missedOnce();
        if (again.misses() >= MAX_MISSES) {
            log.warn(
                    "event=relocation_dropped player={} misses={} reason=the teleport kept missing",
                    relocation.player(),
                    again.misses());
            return;
        }
        log.warn("event=relocation_missed player={} misses={} of={}", relocation.player(), again.misses(), MAX_MISSES);
        scheduler.async(() -> {
            try {
                relocations.restore(again);
            } catch (RuntimeException failed) {
                log.error("the relocation of " + relocation.player() + " could not be put back", failed);
            }
        });
    }

    private static void landed() {
        // The player was away when it was set; the landing itself needs no notice.
    }

    /**
     * The named player when this server knows them and they are not online here as far as the actor can see;
     * otherwise the actor is told why not. A player online here whom the actor may not see is handled as offline.
     */
    private Optional<PlayerRef> offline(PlayerRef actor, String name) {
        Objects.requireNonNull(name, "name");
        Optional<PlayerRef> target = players.findByName(name);
        if (target.isEmpty()) {
            tell(actor, TeleportMessageKey.TPOFFLINE_UNKNOWN, name);
            return Optional.empty();
        }
        PlayerRef found = target.get();
        if (players.isOnline(found.uuid()) && !visibility.hides(found, actor, visibility.seeLevel(actor))) {
            tell(actor, TeleportMessageKey.TPOFFLINE_ONLINE, found.name());
            return Optional.empty();
        }
        return target;
    }

    private void land(PlayerRef actor, PlayerRef target, Optional<SavedSpot> saved) {
        if (saved.isEmpty()) {
            tell(actor, TeleportMessageKey.TPOFFLINE_UNKNOWN, target.name());
            return;
        }
        SavedSpot spot = saved.get();
        Optional<WorldRef> world = worldOf(spot);
        if (world.isEmpty()) {
            tell(actor, TeleportMessageKey.TPOFFLINE_WORLD_MISSING, target.name());
            return;
        }
        Position at = new Position(world.get(), spot.x(), spot.y(), spot.z(), spot.yaw(), spot.pitch());
        executor.teleport(actor, Destination.at(at), TeleportKind.ADMIN);
        notifier.send(actor, TeleportMessageKey.TP_DONE);
    }

    /** The world a saved position names: by uid when the data has one, otherwise by its dimension key. */
    private Optional<WorldRef> worldOf(SavedSpot spot) {
        java.util.UUID uid = spot.worldUid();
        if (uid != null) {
            return worlds.findByUid(uid);
        }
        String dimension = spot.dimension();
        return dimension == null ? Optional.empty() : worlds.findByKey(dimension);
    }

    private void tell(PlayerRef actor, TeleportMessageKey key, String player) {
        notifier.send(actor, key, Map.of("player", player));
    }
}
