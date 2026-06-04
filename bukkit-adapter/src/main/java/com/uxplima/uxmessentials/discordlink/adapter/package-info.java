/**
 * The discord-link context's bukkit-side wiring: {@code DiscordlinkWiring} builds the jOOQ store, the use
 * cases, the {@code /discordlink} {@code /discordunlink} commands, and the {@code ConfirmLinkService} the plugin
 * registers into the {@code ServicesManager} so the optional Discord bridge redeems a {@code /link} code through
 * the same {@code ConfirmLink} use case. {@code DiscordLinkServices} holds the constructed use cases the
 * commands share.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.discordlink.adapter;
