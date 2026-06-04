/**
 * The discord-link context's inbound Brigadier commands: {@code /discordlink} (issue a code, {@code status}
 * subcommand) and {@code /discordunlink} (remove a binding), built over the constructed
 * {@code DiscordLinkServices} and guarded by {@code uxmessentials.discord.link}. The redemption itself happens
 * in Discord through the bridge's {@code /link} slash command, not as a Minecraft command.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.discordlink.adapter.inbound.command;
