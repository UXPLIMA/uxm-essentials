package com.uxplima.uxmessentials.playerstate.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port for the staff "look at someone's storage" verbs, {@code /invsee} and {@code /endersee}. Unlike
 * the {@link PlayerEffects} verbs, which act on the subject, these open a container <em>for the viewer</em> that
 * shows the subject's contents, so the open must run on the viewer's owning region/entity thread (that is where
 * their open inventory lives). The viewer must be online (an offline viewer is a silent no-op); the subject may be
 * online or offline: an offline subject's stored data is read from and written back to disk by the adapter.
 */
public interface InventoryViewer {

    /**
     * Open {@code subject}'s inventory in {@code viewer}'s screen, running {@code shown} once the window is in front
     * of them. The window opens later than this call returns and may be refused (by another plugin, or because the
     * viewer is still saving an edit), and then {@code shown} never runs.
     */
    void viewInventory(PlayerRef viewer, PlayerRef subject, Runnable shown);

    /** Open {@code subject}'s ender chest in {@code viewer}'s screen; {@code shown} as for {@link #viewInventory}. */
    void viewEnderChest(PlayerRef viewer, PlayerRef subject, Runnable shown);
}
