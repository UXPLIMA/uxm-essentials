package com.uxplima.uxmessentials.economy.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import com.uxplima.uxmessentials.economy.application.ReturnUnsoldItems;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/** Hands a joining player the unsold items that were kept for them while they were offline. */
@NullMarked
public final class ItemReturnListener implements Listener {

    private final ReturnUnsoldItems returns;
    private final Scheduler scheduler;

    public ItemReturnListener(ReturnUnsoldItems returns, Scheduler scheduler) {
        this.returns = Objects.requireNonNull(returns, "returns");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        PlayerRef player = BukkitRefs.toRef(event.getPlayer());
        // The kept rows are read from storage, which never happens on the join thread.
        scheduler.async(() -> returns.deliverKept(player));
    }
}
