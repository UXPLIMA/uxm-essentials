package com.uxplima.uxmessentials.discordlink.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.discordlink.domain.DiscordId;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * A binding was removed, and the two accounts are strangers again.
 *
 * <p>Carries the Discord account that was bound, because by the time a listener runs there is no longer anywhere
 * to look it up: that is the whole point of the event.
 *
 * @param player the Minecraft account that was bound
 * @param discordId the Discord user it was bound to
 */
public record AccountUnlinked(PlayerRef player, DiscordId discordId) implements DiscordLinkEvent {

    public AccountUnlinked {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(discordId, "discordId");
    }
}
