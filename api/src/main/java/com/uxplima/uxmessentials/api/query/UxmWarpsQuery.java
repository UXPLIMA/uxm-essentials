package com.uxplima.uxmessentials.api.query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import com.uxplima.uxmessentials.api.view.UxmWarp;

/**
 * What server warps exist, and which of them a given player would be shown.
 *
 * <p>Warp names are unique server-wide and are matched the way the command matches them, so the name a player types
 * is the name to pass here.
 */
public interface UxmWarpsQuery {

    /** Every warp on the server, in creation order, whoever may use them. */
    CompletableFuture<List<UxmWarp>> list();

    /** The warp under this name, or empty when there is none. */
    CompletableFuture<Optional<UxmWarp>> get(String name);

    /**
     * The warps this player would see in {@code /warps}: the same permission filter the command applies, so a warp
     * gated behind a node they do not hold is absent rather than present and unusable.
     */
    CompletableFuture<List<UxmWarp>> visibleTo(UUID playerId);

    /** Whether a warp already exists under this name, which is the check {@code /setwarp} makes. */
    CompletableFuture<Boolean> exists(String name);

    /**
     * The mean player rating of one warp, or zero when nobody has rated it.
     *
     * <p>Its own method rather than a field on {@link UxmWarp}, because the rating lives in a separate table: were
     * it a field, listing every warp would cost one extra query per warp.
     */
    CompletableFuture<Double> averageRating(String name);
}
