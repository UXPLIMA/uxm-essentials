/**
 * The command-control context's outbound adapters.
 * {@link com.uxplima.uxmessentials.commandcontrol.adapter.outbound.PlayerGroupSource} reads a player's primary
 * permission group for the gate, keeping a permission plugin a soft dependency:
 * {@link com.uxplima.uxmessentials.commandcontrol.adapter.outbound.LuckPermsPlayerGroupSource} binds only when
 * LuckPerms is installed, chosen by
 * {@link com.uxplima.uxmessentials.commandcontrol.adapter.outbound.PlayerGroupSources}, with an empty fallback
 * otherwise.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.commandcontrol.adapter.outbound;
