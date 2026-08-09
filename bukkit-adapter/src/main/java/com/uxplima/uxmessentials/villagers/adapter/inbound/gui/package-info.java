/**
 * The villagers context's trade-manager GUI: a menu-engine window whose chrome lives in
 * {@code modules/villagers/gui/trade-manager.conf} and whose buy/sell stacks are a declared content region. The window
 * itself (spec, bindings, region geometry) is
 * {@link com.uxplima.uxmessentials.villagers.adapter.inbound.gui.VillagerManagerWindow}, the region provider is
 * {@link com.uxplima.uxmessentials.villagers.adapter.inbound.gui.VillagerTradeContent}, the behaviour behind the
 * buttons is {@link com.uxplima.uxmessentials.villagers.adapter.inbound.gui.VillagerManagerView}, one open session is
 * a {@link com.uxplima.uxmessentials.villagers.adapter.inbound.gui.VillagerManagerHolder}, and the pure translation
 * between region stacks and merchant recipes is
 * {@link com.uxplima.uxmessentials.villagers.adapter.inbound.gui.VillagerManagerLayout}.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.villagers.adapter.inbound.gui;
