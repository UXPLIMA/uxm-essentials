package com.uxplima.uxmessentials.teleport.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.application.OfflineTeleport;
import com.uxplima.uxmessentials.teleport.application.ResolveRtp;
import com.uxplima.uxmessentials.teleport.application.ResolveSpawn;
import com.uxplima.uxmessentials.teleport.application.TeleportSettings;
import com.uxplima.uxmessentials.teleport.application.port.TeleportExecutor;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;

/**
 * Owns all automatic join movement, so no two join handlers race to move a player. A {@code /tpofflinehere}
 * relocation waiting for the player on this server comes first; only when none is applied does the join's own move
 * run: first-join RTP when enabled, otherwise first- or every-join spawn through the same resolution chain as
 * {@code /spawn}. Deciding that needs the relocation table, so the join's own move runs a moment after the join, on
 * the player's thread. Both moves are immediate, untracked relocations and do not overwrite {@code /back}.
 */
public final class SpawnJoinListener implements Listener {

    private final TeleportSettings settings;
    private final ResolveSpawn resolveSpawn;
    private final ResolveRtp resolveRtp;
    private final TeleportExecutor executor;
    private final Permissions permissions;
    private final OfflineTeleport relocations;

    public SpawnJoinListener(
            TeleportSettings settings,
            ResolveSpawn resolveSpawn,
            ResolveRtp resolveRtp,
            TeleportExecutor executor,
            Permissions permissions,
            OfflineTeleport relocations) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.resolveSpawn = Objects.requireNonNull(resolveSpawn, "resolveSpawn");
        this.resolveRtp = Objects.requireNonNull(resolveRtp, "resolveRtp");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.relocations = Objects.requireNonNull(relocations, "relocations");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerRef who = BukkitRefs.toRef(player);
        relocations.arrive(who, joinMove(player, who));
    }

    /** The join's own move, decided here where the player is read, and run only when no relocation is applied. */
    private Runnable joinMove(Player player, PlayerRef who) {
        String exemption = settings.joinSpawnExemptPermission();
        if (!exemption.isEmpty() && permissions.has(who, exemption)) {
            return SpawnJoinListener::stay;
        }
        boolean firstJoin = !player.hasPlayedBefore();
        WorldRef world = BukkitRefs.toRef(player.getWorld());
        if (firstJoin && settings.rtpOnFirstJoin()) {
            return () -> resolveRtp.firstJoin(who, world);
        }
        if (!settings.spawnOnEveryJoin() && !(firstJoin && settings.spawnOnFirstJoin())) {
            return SpawnJoinListener::stay;
        }
        return () -> resolveSpawn
                .resolveDefault(world)
                .ifPresent(position -> executor.relocate(
                        who, Destination.at(position), TeleportKind.SPAWN, SpawnJoinListener::landed));
    }

    private static void stay() {
        // No join move is configured for this player: they stay where they logged in.
    }

    private static void landed() {
        // Automatic join movement intentionally has no cooldown, charge, message or arrival effect.
    }
}
