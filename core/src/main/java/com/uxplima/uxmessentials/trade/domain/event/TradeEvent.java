package com.uxplima.uxmessentials.trade.domain.event;

import com.uxplima.uxmessentials.shared.domain.DomainEvent;

/**
 * The facts the trade context publishes.
 *
 * <p>Two, because a trade has two endings. Everything in between (an item staked, a confirmation given and taken
 * back again) is window state that changes several times a second and is nobody else's business.
 */
public sealed interface TradeEvent extends DomainEvent permits TradeCompleted, TradeCancelled {}
