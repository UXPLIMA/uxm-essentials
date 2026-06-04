package com.uxplima.uxmessentials.discordlink.application;

import java.util.Objects;

import com.uxplima.uxmessentials.discordlink.application.port.DiscordLinkStore;
import com.uxplima.uxmessentials.discordlink.domain.DiscordLinkError;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * Removes a player's confirmed Discord binding ({@code /discordunlink}). Returns success when a binding existed
 * and was removed, or {@code NOT_LINKED} when the player had none — so the command can tell the player whether
 * anything was actually unbound.
 */
public final class Unlink {

    private final DiscordLinkStore store;

    public Unlink(DiscordLinkStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    /** Unbind {@code player}; success when a binding was removed, {@code NOT_LINKED} when there was none. */
    public Result<Unit, DiscordLinkError> unlink(PlayerRef player) {
        Objects.requireNonNull(player, "player");
        return store.unlink(player.uuid()) ? Result.ok() : Result.err(DiscordLinkError.NOT_LINKED);
    }
}
