package com.uxplima.uxmessentials.api.action;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Changing a Discord binding.
 *
 * <p>One verb, and deliberately one. Removing a binding is something a plugin has real reason to do: an account
 * was sold, a ban was handed out, a member left the server. Creating one is not, because a binding means "the
 * person holding this Discord account proved they hold this Minecraft account", and a binding written without
 * that proof would say something untrue in the same field everything else reads.
 */
public interface UxmDiscordLinkActions {

    /**
     * Remove this player's binding.
     *
     * <p>Fails with {@code not-found} when they had none, so a caller can tell a removal from a no-op. The player
     * does not have to be online: the binding is a database row, not a session.
     */
    CompletableFuture<UxmOutcome> unlink(UUID playerId);
}
