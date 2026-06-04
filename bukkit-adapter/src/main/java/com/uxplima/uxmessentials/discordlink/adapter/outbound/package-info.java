/**
 * The discord-link context's outbound adapter: {@code ConfirmLinkService}, the host-side implementation of the
 * {@code :api} {@code DiscordLinkConfirmation} seam, registered into the {@code ServicesManager} so the optional
 * Discord bridge redeems a {@code /link} code through the {@code ConfirmLink} use case with no compile-time link
 * to the host jar.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.discordlink.adapter.outbound;
