/**
 * The discord-link context's outbound persistence adapter: the jOOQ-backed {@code DiscordLinkStore} over the
 * generated {@code DISCORD_LINK_PENDING} and {@code DISCORD_LINKS} tables (the persistence V16 baseline), and
 * the {@code DiscordLinkRows} anti-corruption mapping. The {@code DiscordLinkStores} factory is the one seam
 * the bukkit-adapter wires through so it never names a jOOQ type.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.persistence.discordlink;
