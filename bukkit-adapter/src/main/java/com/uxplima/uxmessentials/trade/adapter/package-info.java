/**
 * Bukkit-facing adapters of the trade bounded context. Phase 1 stands up only
 * {@link com.uxplima.uxmessentials.trade.adapter.TradeWiring}, which resolves the module's typed config on enable; the
 * dual-inventory trade window, the {@code /trade} Brigadier commands, the inventory listeners, and the cross-server
 * bus transport land here in the later phases behind the same wiring seam.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.trade.adapter;
