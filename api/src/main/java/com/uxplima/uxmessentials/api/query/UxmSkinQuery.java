package com.uxplima.uxmessentials.api.query;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.view.UxmSkin;

/**
 * The skin a player is wearing by their own choice.
 *
 * <p>Empty means they chose nothing, which is not the same as looking like Steve: a player who chose nothing still
 * arrives wearing whatever the join order gave them (their premium skin, their Bedrock skin, or one of the server's
 * default pool). What is published is the choice, because that is the part a consumer can act on.
 */
public interface UxmSkinQuery {

    /** What this player chose, or empty when they chose nothing. */
    CompletableFuture<Optional<UxmSkin>> of(UUID playerId);
}
