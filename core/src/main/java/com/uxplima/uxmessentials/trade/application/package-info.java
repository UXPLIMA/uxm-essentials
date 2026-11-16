/**
 * Application layer of the trade bounded context: the {@link com.uxplima.uxmessentials.trade.application.TradeModule}
 * feature-module identity and enable gate, and the typed {@link
 * com.uxplima.uxmessentials.trade.application.TradeConfig} view of {@code modules/trade/config.conf}, the trade-window
 * use cases, the request/accept flow, and the cross-server escrow orchestration. No Bukkit, Paper, Kyori, or
 * infrastructure type appears here: the layer speaks only the domain and the shared kernel ports.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.trade.application;
