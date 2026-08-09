package com.uxplima.uxmessentials.api.query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.view.UxmSanction;
import com.uxplima.uxmessentials.api.view.UxmSanctionRecord;
import com.uxplima.uxmessentials.api.view.UxmWarn;

/**
 * What punishments a player is serving, and what they have served before.
 *
 * <p>The sanction methods answer about now: a ban that has lapsed is empty here and a line in {@link #history}
 * instead. Every one of them answers for an offline player too, because a punishment that could only be read
 * while its subject was online would be no use to the plugin that has to enforce it.
 *
 * <p>A permanent ban laid down through the server's own ban list rather than through this plugin is not visible
 * here. That list belongs to the server, and a consumer that needs it should read it from the server.
 */
public interface UxmModerationQuery {

    /** The ban this player is serving, or empty when they are not banned. */
    CompletableFuture<Optional<UxmSanction>> ban(UUID playerId);

    /** The mute this player is serving, or empty when they are not muted. */
    CompletableFuture<Optional<UxmSanction>> mute(UUID playerId);

    /** The jail sentence this player is serving, or empty when they are not jailed. */
    CompletableFuture<Optional<UxmSanction>> jail(UUID playerId);

    /** The warnings still counting against this player, newest first. Expired ones are not included. */
    CompletableFuture<List<UxmWarn>> warns(UUID playerId);

    /** This player's moderation history, newest first, at most {@code limit} lines. */
    CompletableFuture<List<UxmSanctionRecord>> history(UUID playerId, int limit);
}
