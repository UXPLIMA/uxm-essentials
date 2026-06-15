package com.uxplima.uxmessentials.npc.adapter.outbound;

import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.npc.adapter.inbound.listener.NpcCommandRunner;
import org.jspecify.annotations.NullMarked;

/**
 * The Bukkit-backed {@link NpcCommandRunner}: a console command goes through {@code Bukkit.dispatchCommand} on the
 * console sender, a player command through {@code player.performCommand}. Both must run on the main/region thread,
 * which the interaction listener guarantees before calling in.
 */
@NullMarked
public final class BukkitNpcCommandRunner implements NpcCommandRunner {

    @Override
    public void runAsConsole(String command) {
        Objects.requireNonNull(command, "command");
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }

    @Override
    public void runAsPlayer(Player player, String command) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(command, "command");
        player.performCommand(command);
    }
}
