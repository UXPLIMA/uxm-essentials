package com.uxplima.uxmessentials.playerstate.adapter.outbound;

import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.playerstate.domain.GameModeRef;
import com.uxplima.uxmessentials.playerstate.domain.PlayerStateSnapshot;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link com.uxplima.uxmessentials.playerstate.application.port.StateReconciler} implementation. It pushes
 * an immutable {@link PlayerStateSnapshot} onto the live Bukkit player, hopping to the player's owning
 * region/entity thread through the injected {@link Scheduler} port — the Bukkit mutators
 * ({@code setInvulnerable}, {@code setAllowFlight}, {@code setGameMode}, {@code setWalkSpeed},
 * {@code setFlySpeed}) are only valid there on Folia (docs/02-concurrency §playerstate reconciliation). An
 * offline player is a silent no-op (the entity scheduler refuses a despawned entity).
 *
 * <p>The reconciler is the single place domain state crosses to the Bukkit API; the use cases never touch a
 * live {@code Player}. Disabling fly while the player is airborne also clears their in-flight state so they do
 * not hang in the air.
 */
@NullMarked
public final class BukkitStateReconciler
        implements com.uxplima.uxmessentials.playerstate.application.port.StateReconciler {

    private final Scheduler scheduler;

    public BukkitStateReconciler(Scheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public void reconcile(PlayerRef who, PlayerStateSnapshot snapshot) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(snapshot, "snapshot");
        scheduler.onEntity(who, () -> apply(who, snapshot));
    }

    private void apply(PlayerRef who, PlayerStateSnapshot snapshot) {
        Player player = Bukkit.getPlayer(who.uuid());
        if (player == null || !player.isOnline()) {
            return;
        }
        player.setInvulnerable(snapshot.god());
        applyFlight(player, snapshot.fly());
        snapshot.gameMode().map(BukkitStateReconciler::toBukkit).ifPresent(player::setGameMode);
        player.setWalkSpeed(snapshot.walkSpeed().toWalkMultiplier());
        player.setFlySpeed(snapshot.flySpeed().toFlyMultiplier());
    }

    private static void applyFlight(Player player, boolean allowed) {
        player.setAllowFlight(allowed);
        if (!allowed && player.isFlying()) {
            player.setFlying(false);
        }
    }

    private static GameMode toBukkit(GameModeRef mode) {
        return switch (mode) {
            case SURVIVAL -> GameMode.SURVIVAL;
            case CREATIVE -> GameMode.CREATIVE;
            case ADVENTURE -> GameMode.ADVENTURE;
            case SPECTATOR -> GameMode.SPECTATOR;
        };
    }
}
