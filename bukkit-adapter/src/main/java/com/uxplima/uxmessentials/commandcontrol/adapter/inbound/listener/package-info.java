/**
 * The command-control context's Bukkit listeners.
 * {@link com.uxplima.uxmessentials.commandcontrol.adapter.inbound.listener.CommandGateListener} gates every player
 * command on the {@link com.uxplima.uxmessentials.commandcontrol.domain.RuleSet} whitelist / blacklist, cancelling a
 * denied dispatch and sending the configured deny line;
 * {@link com.uxplima.uxmessentials.commandcontrol.adapter.inbound.listener.BukkitPlayerFacts} adapts the live
 * {@code Player} to the domain's Bukkit-free player-facts view with a lazy group lookup.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.commandcontrol.adapter.inbound.listener;
