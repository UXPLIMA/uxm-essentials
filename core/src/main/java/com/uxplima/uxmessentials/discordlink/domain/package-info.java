/**
 * Pure domain of the discord-link bounded context: the {@code LinkCode} one-time code (unambiguous alphabet),
 * the {@code DiscordId} snowflake, the {@code PendingLink} (player, code, expiry) and {@code ConfirmedLink}
 * (player, Discord id, linked-at) value objects, and the {@code DiscordLinkError} failure enum. A pending code
 * is issued in game and redeemed in Discord; a confirmed link is account identity, so the store behind it is
 * DB-backed, never PDC. No Bukkit, Paper, Kyori, or logging type appears here; the model is built from value
 * objects and the kernel primitive {@code PlayerRef}.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.discordlink.domain;
