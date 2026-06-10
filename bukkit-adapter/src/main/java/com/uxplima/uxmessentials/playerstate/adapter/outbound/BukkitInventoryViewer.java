package com.uxplima.uxmessentials.playerstate.adapter.outbound;

import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.playerstate.adapter.inbound.gui.InvseeView;
import com.uxplima.uxmessentials.playerstate.adapter.inbound.gui.OfflineContainerView;
import com.uxplima.uxmessentials.playerstate.application.port.InventoryViewer;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link InventoryViewer} implementation for {@code /invsee} and {@code /endersee}, routing by whether the
 * subject is online. When online, {@code /invsee} goes through the managed {@link InvseeView} (a private copy
 * reconciled on close — never the live {@link org.bukkit.inventory.PlayerInventory}, the classic dupe vector) and
 * {@code /endersee} opens the target's real {@link Player#getEnderChest()} (an ender chest is already a shared
 * container Bukkit reconciles correctly). When the subject is offline, both route to the {@link
 * OfflineContainerView}, which reads the target's stored items from disk into the same kind of managed menu and
 * writes the edits back to the {@code playerdata} file on close.
 *
 * <p>The online open runs on the viewer's owning entity thread through the injected {@link Scheduler}; the offline
 * path schedules its own disk read and menu open. The routing decision reads the subject's current online state on
 * the calling (command) thread.
 */
@NullMarked
public final class BukkitInventoryViewer implements InventoryViewer {

    private final Scheduler scheduler;
    private final InvseeView invseeView;
    private final OfflineContainerView offlineView;

    public BukkitInventoryViewer(Scheduler scheduler, InvseeView invseeView, OfflineContainerView offlineView) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.invseeView = Objects.requireNonNull(invseeView, "invseeView");
        this.offlineView = Objects.requireNonNull(offlineView, "offlineView");
    }

    @Override
    public void viewInventory(PlayerRef viewer, PlayerRef subject) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(subject, "subject");
        if (isOnline(subject)) {
            invseeView.open(viewer, subject);
        } else {
            offlineView.openInventory(viewer, subject);
        }
    }

    @Override
    public void viewEnderChest(PlayerRef viewer, PlayerRef subject) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(subject, "subject");
        if (!isOnline(subject)) {
            offlineView.openEnderChest(viewer, subject);
            return;
        }
        scheduler.onEntity(viewer, () -> {
            Player looker = Bukkit.getPlayer(viewer.uuid());
            Player target = Bukkit.getPlayer(subject.uuid());
            if (looker != null && looker.isOnline() && target != null && target.isOnline()) {
                looker.openInventory(target.getEnderChest());
            }
        });
    }

    private static boolean isOnline(PlayerRef subject) {
        Player target = Bukkit.getPlayer(subject.uuid());
        return target != null && target.isOnline();
    }
}
