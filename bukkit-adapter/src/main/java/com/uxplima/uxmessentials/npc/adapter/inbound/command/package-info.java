/**
 * The npc context's inbound command adapter: the single {@code /npc} Brigadier command (create / delete / list /
 * movehere / skin / command) gated by {@code uxmessentials.npc.admin}, mapping each subcommand's arguments to one
 * use-case call and resolving the create-time and {@code player:} skins from live Bukkit profiles.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.npc.adapter.inbound.command;
