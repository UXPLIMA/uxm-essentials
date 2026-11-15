package com.uxplima.uxmessentials.kits.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import com.uxplima.uxmessentials.kits.application.GrantFirstJoinKits;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import org.jspecify.annotations.NullMarked;

/**
 * Listens for {@link PlayerJoinEvent} to hand a player who joins this server for the first time every kit configured
 * with {@code firstJoin = true}, through {@link GrantFirstJoinKits}.
 */
@NullMarked
public final class KitsJoinListener implements Listener {

    private final GrantFirstJoinKits firstJoinKits;

    public KitsJoinListener(GrantFirstJoinKits firstJoinKits) {
        this.firstJoinKits = Objects.requireNonNull(firstJoinKits, "firstJoinKits");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.hasPlayedBefore()) {
            return;
        }
        firstJoinKits.grant(BukkitRefs.toRef(player));
    }
}
