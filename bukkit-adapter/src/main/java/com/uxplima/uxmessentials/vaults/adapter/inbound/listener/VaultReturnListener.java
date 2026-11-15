package com.uxplima.uxmessentials.vaults.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vaults.adapter.outbound.BukkitVaultItemHandBack;

/** Hands a joining player the items of a vault deleted while its window was open, kept while they were offline. */
public final class VaultReturnListener implements Listener {

    private final BukkitVaultItemHandBack handBack;
    private final Scheduler scheduler;

    public VaultReturnListener(BukkitVaultItemHandBack handBack, Scheduler scheduler) {
        this.handBack = Objects.requireNonNull(handBack, "handBack");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        PlayerRef player = BukkitRefs.toRef(event.getPlayer());
        // The kept rows are read from storage, which never happens on the join thread.
        scheduler.async(() -> handBack.deliverKept(player));
    }
}
