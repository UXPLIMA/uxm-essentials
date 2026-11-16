package com.uxplima.uxmessentials.vaults.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import com.uxplima.uxmessentials.vaults.adapter.inbound.gui.VaultView;

/**
 * Decides a leaving player's vault opens that were still undecided, and frees the vaults they held in windows they
 * never saw, so a refused open does not keep the vault from everybody else until they come back.
 */
public final class VaultQuitListener implements Listener {

    private final VaultView view;

    public VaultQuitListener(VaultView view) {
        this.view = Objects.requireNonNull(view, "view");
    }

    /** Last, after anything else that may close the window on quit, so a window that was showing has settled. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        view.viewerLeft(event.getPlayer().getUniqueId());
    }
}
