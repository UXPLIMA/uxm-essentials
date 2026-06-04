package com.uxplima.uxmessentials.playerstate.adapter.outbound;

import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.playerstate.adapter.inbound.gui.InvseeView;
import com.uxplima.uxmessentials.playerstate.application.port.InventoryViewer;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link InventoryViewer} implementation for {@code /invsee} and {@code /endersee}. {@code /invsee} routes
 * through the managed {@link InvseeView}, which mirrors the target's full inventory (main slots, armour, offhand)
 * into a private copy the viewer edits and reconciles back on close — never the target's live
 * {@link org.bukkit.inventory.PlayerInventory}, which is the classic dupe vector. The viewer's edit right is the
 * {@code uxmessentials.invsee.modify} node, which {@link InvseeView} resolves on the viewer's entity thread
 * (where the live player is safe to read) before the menu opens; without it the menu opens view-only.
 *
 * <p>{@code /endersee} keeps the direct open of the target's real {@link Player#getEnderChest()} inventory: an
 * ender chest is already a shared container Bukkit reconciles correctly, so it carries no raw-inventory dupe and
 * needs no managed copy. Both opens run on the viewer's owning entity thread through the injected
 * {@link Scheduler}, and are skipped when either player has gone offline.
 */
@NullMarked
public final class BukkitInventoryViewer implements InventoryViewer {

    private final Scheduler scheduler;
    private final InvseeView invseeView;

    public BukkitInventoryViewer(Scheduler scheduler, InvseeView invseeView) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.invseeView = Objects.requireNonNull(invseeView, "invseeView");
    }

    @Override
    public void viewInventory(PlayerRef viewer, PlayerRef subject) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(subject, "subject");
        invseeView.open(viewer, subject);
    }

    @Override
    public void viewEnderChest(PlayerRef viewer, PlayerRef subject) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(subject, "subject");
        scheduler.onEntity(viewer, () -> {
            Player looker = Bukkit.getPlayer(viewer.uuid());
            Player target = Bukkit.getPlayer(subject.uuid());
            if (looker != null && looker.isOnline() && target != null && target.isOnline()) {
                looker.openInventory(target.getEnderChest());
            }
        });
    }
}
