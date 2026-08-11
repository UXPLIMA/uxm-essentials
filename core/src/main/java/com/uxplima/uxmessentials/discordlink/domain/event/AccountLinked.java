package com.uxplima.uxmessentials.discordlink.domain.event;

import java.time.Instant;
import java.util.Objects;

import com.uxplima.uxmessentials.discordlink.domain.DiscordId;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A player's Minecraft account and a Discord account are now bound to each other.
 *
 * <p>Published when the code is redeemed, which happens on Discord's side rather than in game, so the player may
 * well be offline when it fires.
 *
 * @param player the bound Minecraft account
 * @param discordId the bound Discord user
 * @param linkedAt when the binding was confirmed
 */
public record AccountLinked(PlayerRef player, DiscordId discordId, Instant linkedAt) implements DiscordLinkEvent {

    public AccountLinked {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(discordId, "discordId");
        Objects.requireNonNull(linkedAt, "linkedAt");
    }
}
