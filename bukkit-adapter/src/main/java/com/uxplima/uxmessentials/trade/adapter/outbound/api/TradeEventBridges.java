package com.uxplima.uxmessentials.trade.adapter.outbound.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.api.bukkit.event.trade.UxmTradeCancelEvent;
import com.uxplima.uxmessentials.api.bukkit.event.trade.UxmTradeCompleteEvent;
import com.uxplima.uxmessentials.api.view.UxmMoney;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.EventBridgeRegistry;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.Region;
import com.uxplima.uxmessentials.trade.domain.event.TradeCancelled;
import com.uxplima.uxmessentials.trade.domain.event.TradeCompleted;
import org.jspecify.annotations.NullMarked;

/**
 * Which Bukkit event each trade fact becomes.
 *
 * <p>A trade has two subjects and one of them may already have left, so both facts go global rather than following
 * either player's thread. The alternative would be picking one of the two, which is not a choice this has any
 * grounds to make.
 */
@NullMarked
public final class TradeEventBridges {

    private TradeEventBridges() {}

    public static void register(EventBridgeRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        registry.register(
                TradeCompleted.class,
                UxmTradeCompleteEvent.getHandlerList(),
                fact -> new UxmTradeCompleteEvent(
                        fact.id().value(),
                        fact.initiator().uuid(),
                        fact.initiator().name(),
                        fact.partner().uuid(),
                        fact.partner().name(),
                        fact.initiatorItems(),
                        fact.partnerItems(),
                        money(fact.initiatorMoney()),
                        money(fact.partnerMoney()),
                        fact.initiatorExperience(),
                        fact.partnerExperience()),
                fact -> Region.global());
        registry.register(
                TradeCancelled.class,
                UxmTradeCancelEvent.getHandlerList(),
                fact -> new UxmTradeCancelEvent(
                        fact.id().value(),
                        fact.initiator().uuid(),
                        fact.initiator().name(),
                        fact.partner().uuid(),
                        fact.partner().name()),
                fact -> Region.global());
    }

    /** One entry per currency staked, in currency order so two reads of the same trade look the same. */
    private static List<UxmMoney> money(Map<String, BigDecimal> staked) {
        return staked.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new UxmMoney(entry.getKey(), entry.getValue()))
                .toList();
    }
}
