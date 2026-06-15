package com.uxplima.uxmessentials.npc.adapter.outbound;

import org.bukkit.entity.Player;

/**
 * The seam the {@code CONNECT} action uses to send a player to another proxy backend. Behind it sits a
 * BungeeCord/Velocity "Connect" plugin-message; pulling it out keeps the action runner testable against a
 * recording fake and lets a server with no outgoing proxy channel registered degrade to a logged no-op rather
 * than failing the click.
 */
public interface NpcServerConnector {

    /** Whether the outgoing proxy channel is registered, so a connect request can actually leave this backend. */
    boolean isAvailable();

    /** Ask the proxy to move {@code player} to the backend named {@code server}. A no-op when unavailable. */
    void connect(Player player, String server);
}
