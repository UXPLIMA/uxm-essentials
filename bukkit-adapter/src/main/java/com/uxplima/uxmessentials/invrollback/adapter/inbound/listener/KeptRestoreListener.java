package com.uxplima.uxmessentials.invrollback.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import com.uxplima.uxmessentials.invrollback.adapter.inbound.gui.SnapshotRestorer;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import org.jspecify.annotations.NullMarked;

/**
 * Puts on a joining player the restore staff chose while they were away. It listens at MONITOR, after every other
 * plugin had its say on the join, so the restored inventory is the last word; the restorer reads the database off the
 * tick and does nothing when no restore was kept.
 */
@NullMarked
public final class KeptRestoreListener implements Listener {

    private final SnapshotRestorer restorer;

    public KeptRestoreListener(SnapshotRestorer restorer) {
        this.restorer = Objects.requireNonNull(restorer, "restorer");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        restorer.applyKept(BukkitRefs.toRef(event.getPlayer()));
    }
}
