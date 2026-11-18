package com.uxplima.uxmessentials.shared.adapter.outbound.board;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.uxplima.uxmessentials.shared.adapter.outbound.skin.HeadTextures;

/**
 * Holds a player's skin for the board heads while they are here.
 *
 * <p>The skin is read off the joining player's profile, which the skin module dressed before they arrived, so this
 * reads memory on the player's own thread and asks nothing of the store or the network.
 */
public final class BoardSkinListener implements Listener {

    private final BoardSkins skins;

    public BoardSkinListener(BoardSkins skins) {
        this.skins = Objects.requireNonNull(skins, "skins");
    }

    /** Runs last, so a plugin that dresses the player at join is read too. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        skins.arrived(
                event.getPlayer().getUniqueId(),
                HeadTextures.worn(event.getPlayer().getPlayerProfile()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        skins.left(event.getPlayer().getUniqueId());
    }
}
