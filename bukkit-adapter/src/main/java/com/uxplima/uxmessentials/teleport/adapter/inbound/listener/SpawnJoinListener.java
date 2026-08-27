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
import com.uxplima.uxmessentials.teleport.application.ResolveRtp;
import com.uxplima.uxmessentials.teleport.application.ResolveSpawn;
import com.uxplima.uxmessentials.teleport.application.TeleportSettings;
import com.uxplima.uxmessentials.teleport.application.port.TeleportExecutor;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;

/**
 * Owns all automatic join movement. First-join RTP has precedence when enabled; otherwise first/every-join spawn
 * uses the exact same resolution chain as {@code /spawn}. Both paths are immediate, untracked relocations and do
 * not overwrite {@code /back}. A single listener prevents two independently configured join handlers racing.
 */
public final class SpawnJoinListener implements Listener {

    private final TeleportSettings settings;
    private final ResolveSpawn resolveSpawn;
    private final ResolveRtp resolveRtp;
    private final TeleportExecutor executor;
    private final Permissions permissions;

    public SpawnJoinListener(
            TeleportSettings settings,
            ResolveSpawn resolveSpawn,
            ResolveRtp resolveRtp,
            TeleportExecutor executor,
            Permissions permissions) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.resolveSpawn = Objects.requireNonNull(resolveSpawn, "resolveSpawn");
        this.resolveRtp = Objects.requireNonNull(resolveRtp, "resolveRtp");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerRef who = BukkitRefs.toRef(player);
        String exemption = settings.joinSpawnExemptPermission();
        if (!exemption.isEmpty() && permissions.has(who, exemption)) {
            return;
        }

        boolean firstJoin = !player.hasPlayedBefore();
        WorldRef world = BukkitRefs.toRef(player.getWorld());
        if (firstJoin && settings.rtpOnFirstJoin()) {
            resolveRtp.firstJoin(who, world);
            return;
        }
        if (!settings.spawnOnEveryJoin() && !(firstJoin && settings.spawnOnFirstJoin())) {
            return;
        }
        resolveSpawn
                .resolveDefault(world)
                .ifPresent(position -> executor.relocate(
                        who, Destination.at(position), TeleportKind.SPAWN, SpawnJoinListener::landed));
    }

    private static void landed() {
        // Automatic join movement intentionally has no cooldown, charge, message or arrival effect.
    }
}
