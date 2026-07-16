/**
 * The ranks context's Brigadier command handlers: {@link com.uxplima.uxmessentials.ranks.adapter.inbound.command.RankupCommand}
 * ({@code /rankup}) drives the {@code Rankup} pipeline for the invoking player, and
 * {@link com.uxplima.uxmessentials.ranks.adapter.inbound.command.RanksCommand} ({@code /ranks setrank}) sets a
 * player's rank pointer directly through {@code SetRank}. Each resolves only the player identity and maps the
 * use case's typed outcome to a {@code RanksMessageKey}; no rankup rule lives here.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.ranks.adapter.inbound.command;
