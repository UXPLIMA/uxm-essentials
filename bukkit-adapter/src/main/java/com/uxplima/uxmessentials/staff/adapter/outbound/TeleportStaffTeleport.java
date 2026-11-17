package com.uxplima.uxmessentials.staff.adapter.outbound;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.bukkit.Server;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.teleport.application.CrossServerStaffTeleport;
import com.uxplima.uxmessentials.teleport.application.TeleportEngine;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import org.jspecify.annotations.NullMarked;

/**
 * The teleport-backed {@link com.uxplima.uxmessentials.staff.application.port.StaffTeleport}: the COMPASS gadget
 * and {@code /stafflist} route through the existing teleport {@link TeleportEngine} as an
 * {@link TeleportKind#ADMIN} hop (no cooldown, warmup, or request prompt), so staff mode never moves the player
 * itself.
 *
 * <p>The target's live {@code Location} is read on the target's own thread and the launch runs on the staff member's.
 * On Folia the two are different regions whenever the players are apart, and a location read from another region's
 * thread is not allowed. Either player leaving before their step runs answers {@code false}.
 *
 * <p>Bound only when the teleport module is enabled; with teleport off the wiring binds
 * {@link com.uxplima.uxmessentials.staff.application.port.StaffTeleport#NONE}, so the gadget degrades to
 * "teleport could not be started" rather than failing.
 */
@NullMarked
public final class TeleportStaffTeleport implements com.uxplima.uxmessentials.staff.application.port.StaffTeleport {

    private final TeleportEngine engine;
    private final CrossServerStaffTeleport crossServer;
    private final Server server;
    private final Scheduler scheduler;

    public TeleportStaffTeleport(
            TeleportEngine engine, CrossServerStaffTeleport crossServer, Server server, Scheduler scheduler) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.crossServer = Objects.requireNonNull(crossServer, "crossServer");
        this.server = Objects.requireNonNull(server, "server");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public boolean teleportElsewhere(PlayerRef staff, String target) {
        Objects.requireNonNull(staff, "staff");
        Objects.requireNonNull(target, "target");
        if (!crossServer.enabled()) {
            return false;
        }
        crossServer.goTo(staff, target);
        return true;
    }

    @Override
    public CompletionStage<Boolean> teleportTo(PlayerRef staff, PlayerRef target) {
        Objects.requireNonNull(staff, "staff");
        Objects.requireNonNull(target, "target");
        CompletableFuture<Boolean> started = new CompletableFuture<>();
        Runnable unreachable = () -> started.complete(false);
        scheduler.onEntity(
                target,
                () -> {
                    Player targetPlayer = server.getPlayer(target.uuid());
                    if (targetPlayer == null) {
                        started.complete(false);
                        return;
                    }
                    Destination destination = Destination.at(BukkitRefs.toPosition(
                            Objects.requireNonNull(targetPlayer.getLocation(), "target location")));
                    scheduler.onEntity(
                            staff,
                            () -> started.complete(engine.launch(staff, destination, TeleportKind.ADMIN)
                                    .isOk()),
                            unreachable);
                },
                unreachable);
        return started;
    }
}
