package com.uxplima.uxmessentials.npc.adapter.outbound;

import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.outbound.action.ClickCommandRunner;
import org.jspecify.annotations.NullMarked;

/**
 * The Bukkit-backed {@link ClickCommandRunner}: a console command goes through {@code Bukkit.dispatchCommand} on
 * the console sender, a player command through {@code player.performCommand}. Both must run on the main/region
 * thread, which the interaction listener guarantees before calling in.
 */
@NullMarked
public final class BukkitNpcCommandRunner implements ClickCommandRunner {

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

    @Override
    public void runAsPlayerOp(Player player, String command) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(command, "command");
        boolean wasOp = player.isOp();
        try {
            if (!wasOp) {
                player.setOp(true);
            }
            player.performCommand(command);
        } finally {
            // Restore the prior op state even if the command threw, so the elevation never leaks past this dispatch.
            if (!wasOp) {
                player.setOp(false);
            }
        }
    }
}
