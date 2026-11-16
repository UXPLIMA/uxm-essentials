package com.uxplima.uxmessentials.teleport.application;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.application.port.WorldLookup;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.application.port.LogoutPositions;
import com.uxplima.uxmessentials.teleport.application.port.LogoutPositions.SavedSpot;
import com.uxplima.uxmessentials.teleport.application.port.TeleportExecutor;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import com.uxplima.uxmessentials.teleport.domain.TeleportMessageKey;

/**
 * {@code /tpoffline} and {@code /tpofflinehere}: reach an offline player through the position this server saved
 * when they left it. The saved data is read and written on the async lane; the staff member is moved and told on
 * their own thread. A player online on this server is refused, since their saved data is stale while they play and
 * any change to it would be overwritten when they leave.
 */
public final class OfflineTeleport {

    private final LogoutPositions positions;
    private final PlayerLookup players;
    private final WorldLookup worlds;
    private final TeleportExecutor executor;
    private final Scheduler scheduler;
    private final Notifier notifier;

    public OfflineTeleport(
            LogoutPositions positions,
            PlayerLookup players,
            WorldLookup worlds,
            TeleportExecutor executor,
            Scheduler scheduler,
            Notifier notifier) {
        this.positions = Objects.requireNonNull(positions, "positions");
        this.players = Objects.requireNonNull(players, "players");
        this.worlds = Objects.requireNonNull(worlds, "worlds");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Send {@code actor} to where the player named {@code name} logged out of this server. */
    public void goTo(PlayerRef actor, String name) {
        Objects.requireNonNull(actor, "actor");
        offline(actor, name)
                .ifPresent(target -> scheduler.async(() -> {
                    Optional<SavedSpot> saved = positions.read(target.uuid());
                    scheduler.onEntity(actor, () -> land(actor, target, saved));
                }));
    }

    /** Make the player named {@code name} log in to this server at {@code here}, where {@code actor} stands. */
    public void bring(PlayerRef actor, String name, Position here) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(here, "here");
        offline(actor, name)
                .ifPresent(target -> scheduler.async(() -> {
                    boolean moved = positions.write(target.uuid(), here);
                    TeleportMessageKey reply =
                            moved ? TeleportMessageKey.TPOFFLINE_MOVED : TeleportMessageKey.TPOFFLINE_UNKNOWN;
                    scheduler.onEntity(actor, () -> notifier.send(actor, reply, Map.of("player", target.name())));
                }));
    }

    /** The named player when they are known and not online here; otherwise the actor is told why not. */
    private Optional<PlayerRef> offline(PlayerRef actor, String name) {
        Objects.requireNonNull(name, "name");
        Optional<PlayerRef> target = players.findByName(name);
        if (target.isEmpty()) {
            notifier.send(actor, TeleportMessageKey.TPOFFLINE_UNKNOWN, Map.of("player", name));
            return Optional.empty();
        }
        if (players.isOnline(target.get().uuid())) {
            notifier.send(
                    actor,
                    TeleportMessageKey.TPOFFLINE_ONLINE,
                    Map.of("player", target.get().name()));
            return Optional.empty();
        }
        return target;
    }

    private void land(PlayerRef actor, PlayerRef target, Optional<SavedSpot> saved) {
        Map<String, String> who = Map.of("player", target.name());
        if (saved.isEmpty()) {
            notifier.send(actor, TeleportMessageKey.TPOFFLINE_UNKNOWN, who);
            return;
        }
        SavedSpot spot = saved.get();
        Optional<WorldRef> world = worlds.findByUid(spot.worldUid());
        if (world.isEmpty()) {
            notifier.send(actor, TeleportMessageKey.TPOFFLINE_WORLD_MISSING, who);
            return;
        }
        Position at = new Position(world.get(), spot.x(), spot.y(), spot.z(), spot.yaw(), spot.pitch());
        executor.teleport(actor, Destination.at(at), TeleportKind.ADMIN);
        notifier.send(actor, TeleportMessageKey.TP_DONE);
    }
}
