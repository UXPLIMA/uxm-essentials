/**
 * Bukkit-facing adapters of the trade bounded context: {@link com.uxplima.uxmessentials.trade.adapter.TradeWiring},
 * which resolves the module's typed config on enable and wires the dual-inventory trade window, the {@code /trade}
 * Brigadier commands, the inventory listeners, and the cross-server bus transport behind one seam.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.trade.adapter;
