package com.uxplima.uxmessentials.api.view;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A confirmed binding between a Minecraft account and a Discord account.
 *
 * <p>The Discord id is the snowflake as a string, the way Discord's own API writes it: a number that does not fit
 * a signed 64-bit field for much longer is a number nobody should be parsing.
 *
 * @param playerId the bound Minecraft account
 * @param discordId the bound Discord user's id
 * @param linkedAt when the binding was confirmed
 */
public record UxmDiscordLink(UUID playerId, String discordId, Instant linkedAt) {

    public UxmDiscordLink {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(discordId, "discordId");
        Objects.requireNonNull(linkedAt, "linkedAt");
    }
}
