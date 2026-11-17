package com.uxplima.uxmessentials.presence.adapter.outbound;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Server;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.presence.application.port.NickDisplay;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link NickDisplay} over Bukkit: a player's display name is set on their own thread, where Folia allows a
 * {@link Player} to be changed. A player not on this backend is left alone.
 */
@NullMarked
public final class BukkitNickDisplay implements NickDisplay {

    private final Server server;
    private final Scheduler scheduler;

    public BukkitNickDisplay(Server server, Scheduler scheduler) {
        this.server = Objects.requireNonNull(server, "server");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public void show(UUID player, Optional<String> nick) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(nick, "nick");
        Player online = server.getPlayer(player);
        if (online == null) {
            return;
        }
        scheduler.onEntity(BukkitRefs.toRef(online), () -> {
            Player here = server.getPlayer(player);
            if (here != null) {
                here.displayName(Component.text(nick.orElse(here.getName())));
            }
        });
    }
}
