package com.uxplima.uxmessentials.npc.adapter.inbound.listener;

import org.bukkit.entity.Player;

/**
 * The seam between the click-interaction listener and the two ways an NPC's bound command can run: as the
 * server console or as the clicking player. Pulling it behind this port keeps the listener's prefix/substitution
 * logic unit-testable against a recording fake, with the Bukkit dispatch isolated in the single implementation.
 */
public interface NpcCommandRunner {

    /** Run {@code command} as the server console ({@code Bukkit.dispatchCommand} on the console sender). */
    void runAsConsole(String command);

    /** Run {@code command} as {@code player} ({@code player.performCommand}). */
    void runAsPlayer(Player player, String command);
}
