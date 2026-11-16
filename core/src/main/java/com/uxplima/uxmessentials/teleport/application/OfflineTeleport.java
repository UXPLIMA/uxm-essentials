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
 * joins again, and a hop that misses puts the row back for the next join.
 *
 * <p>A player online here is refused: their saved data is stale while they play, and they can be moved directly. A
 * player the staff member may not see because of vanish gets the same answer as a player this server knows nothing
 * of, so the refusal does not give them away.
 */
public final class OfflineTeleport {

    private final LogoutPositions positions;
    private final PendingRelocations relocations;
    private final PlayerLookup players;
    private final WorldLookup worlds;
    private final RequestVisibility visibility;
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
                        log.warn(
                                "the saved position of {} could not be read: {}", target.name(), unreadable.toString());
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
                    actor.uuid());
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

    /** Apply the relocation waiting for {@code who} on this server, if any. Called once they have joined. */
    public void arrive(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        scheduler.async(() -> {
            Optional<PendingRelocation> waiting;
            try {
                waiting = relocations.find(who.uuid(), server);
            } catch (RuntimeException failed) {
                log.error("the relocation of " + who.name() + " could not be read", failed);
                return;
            }
            waiting.ifPresent(relocation -> scheduler.onEntity(who, () -> placeIfLoaded(who, relocation)));
        });
    }

    /** On the player's thread: a world that is not loaded leaves the row for a later join, when it may be. */
    private void placeIfLoaded(PlayerRef who, PendingRelocation relocation) {
        Optional<WorldRef> world = worlds.findByUid(relocation.world());
        if (world.isEmpty()) {
            return;
        }
        Position at = new Position(
                world.get(), relocation.x(), relocation.y(), relocation.z(), relocation.yaw(), relocation.pitch());
        scheduler.async(() -> {
            if (!claimed(relocation)) {
                return;
            }
            executor.teleport(
                    who, Destination.at(at), TeleportKind.ADMIN, OfflineTeleport::landed, () -> putBack(relocation));
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

    /** A hop that missed after the claim (the player left again) keeps the relocation for their next join. */
    private void putBack(PendingRelocation relocation) {
        scheduler.async(() -> {
            try {
                relocations.restore(relocation);
            } catch (RuntimeException failed) {
                log.error("the relocation of " + relocation.player() + " could not be put back", failed);
            }
        });
    }

    private static void landed() {
        // The player was away when it was set; the landing itself needs no notice.
    }

    /**
     * The named player when this server knows them and they are not online here; otherwise the actor is told why
     * not. A player online here whom the actor may not see reads as unknown.
     */
    private Optional<PlayerRef> offline(PlayerRef actor, String name) {
        Objects.requireNonNull(name, "name");
        Optional<PlayerRef> target = players.findByName(name);
        if (target.isEmpty()) {
            tell(actor, TeleportMessageKey.TPOFFLINE_UNKNOWN, name);
            return Optional.empty();
        }
        PlayerRef found = target.get();
        if (players.isOnline(found.uuid())) {
            if (visibility.hides(found, actor, visibility.seeLevel(actor))) {
                tell(actor, TeleportMessageKey.TPOFFLINE_UNKNOWN, name);
            } else {
                tell(actor, TeleportMessageKey.TPOFFLINE_ONLINE, found.name());
            }
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
        Optional<WorldRef> world = worlds.findByUid(spot.worldUid());
        if (world.isEmpty()) {
            tell(actor, TeleportMessageKey.TPOFFLINE_WORLD_MISSING, target.name());
            return;
        }
        Position at = new Position(world.get(), spot.x(), spot.y(), spot.z(), spot.yaw(), spot.pitch());
        executor.teleport(actor, Destination.at(at), TeleportKind.ADMIN);
        notifier.send(actor, TeleportMessageKey.TP_DONE);
    }

    private void tell(PlayerRef actor, TeleportMessageKey key, String player) {
        notifier.send(actor, key, Map.of("player", player));
    }
}
