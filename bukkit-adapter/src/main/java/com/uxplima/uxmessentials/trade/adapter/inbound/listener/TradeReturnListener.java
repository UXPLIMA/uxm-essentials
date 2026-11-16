package com.uxplima.uxmessentials.trade.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.trade.application.ReturnTradeItems;
import org.jspecify.annotations.NullMarked;

/** Hands a joining player the items a trade kept for them while they were offline or unreachable. */
@NullMarked
public final class TradeReturnListener implements Listener {

    private final ReturnTradeItems returns;
    private final Scheduler scheduler;

    public TradeReturnListener(ReturnTradeItems returns, Scheduler scheduler) {
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
