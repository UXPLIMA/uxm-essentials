package com.uxplima.uxmessentials.kits.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import com.uxplima.uxmessentials.kits.application.GrantFirstJoinKits;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.FirstJoins;
import org.jspecify.annotations.NullMarked;

/**
 * Listens for {@link PlayerJoinEvent} to hand a player new to the network every kit configured with
 * {@code firstJoin = true}, through {@link GrantFirstJoinKits}. A player moving to a server of the network they never
 * played on is not new ({@link FirstJoins}), so the kits are not handed out again on each server.
 */
@NullMarked
public final class KitsJoinListener implements Listener {

    private final GrantFirstJoinKits firstJoinKits;
    private final FirstJoins firstJoins;

    public KitsJoinListener(GrantFirstJoinKits firstJoinKits, FirstJoins firstJoins) {
        this.firstJoinKits = Objects.requireNonNull(firstJoinKits, "firstJoinKits");
        this.firstJoins = Objects.requireNonNull(firstJoins, "firstJoins");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!firstJoins.firstJoin(player.getUniqueId(), player.hasPlayedBefore())) {
            return;
        }
        firstJoinKits.grant(BukkitRefs.toRef(player));
    }
}
