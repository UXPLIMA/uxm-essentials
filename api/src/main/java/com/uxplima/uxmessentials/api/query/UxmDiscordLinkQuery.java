package com.uxplima.uxmessentials.api.query;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.view.UxmDiscordLink;

/**
 * Who is linked to whom.
 *
 * <p>Bindings are stored in the database, so every method here waits. Both directions are readable, because both
 * are asked: a Minecraft plugin starts from a player, and a bot starts from a Discord account.
 *
 * <p>A code somebody has been issued but not yet redeemed is not readable. It is a secret in transit, and the
 * only thing it is good for is being redeemed by the person holding it.
 */
public interface UxmDiscordLinkQuery {

    /** The binding for this player, or empty when they have not linked. */
    CompletableFuture<Optional<UxmDiscordLink>> of(UUID playerId);

    /**
     * The binding for this Discord id, or empty when it is bound to nobody.
     *
     * <p>A string that is not a snowflake at all answers empty rather than throwing: nothing is bound to it, and
     * that is a true answer for a value that came from outside.
     */
    CompletableFuture<Optional<UxmDiscordLink>> byDiscordId(String discordId);

    /** Whether this player has a binding, for the common case where the binding itself is not needed. */
    CompletableFuture<Boolean> isLinked(UUID playerId);
}
