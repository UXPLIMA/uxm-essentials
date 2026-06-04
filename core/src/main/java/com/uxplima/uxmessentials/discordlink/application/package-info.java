/**
 * Application layer of the discord-link bounded context: the {@code BeginLink} / {@code ConfirmLink} /
 * {@code Unlink} / {@code LinkStatus} use cases over the {@code DiscordLinkStore} port, the
 * {@code DiscordlinkModule} feature module and its command surface, the {@code DiscordlinkMessageKey} catalog,
 * and the {@code DiscordLinkNotifier}. {@code BeginLink} issues a one-time code in game; {@code ConfirmLink} is
 * the method the Discord bridge redeems a {@code /link} code through over the {@code ServicesManager} seam, on
 * JDA's own thread. No Bukkit, Paper, Kyori, or logging type appears here.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.discordlink.application;
