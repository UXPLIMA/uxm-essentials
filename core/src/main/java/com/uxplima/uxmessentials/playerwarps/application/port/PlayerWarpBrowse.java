package com.uxplima.uxmessentials.playerwarps.application.port;

import com.uxplima.uxmessentials.playerwarps.domain.Page;
import com.uxplima.uxmessentials.playerwarps.domain.WarpCard;
import com.uxplima.uxmessentials.playerwarps.domain.WarpQuery;

/**
 * Outbound read-model port for the paged warp browse — the read side that answers the old browse's inefficiency.
 * Where the {@link PlayerWarpRepository} loads whole {@code PlayerWarp} aggregates for the owner-scoped and admin
 * paths, this port returns one page of lightweight {@link WarpCard}s straight from the database: it applies the
 * {@link WarpQuery}'s filters and ordering, reads exactly one page of rows, and reports the total match count in
 * the same round trip. Opening the browse on a hundred-thousand-warp server therefore costs the same as opening
 * it on an eight-warp one — the query is always bounded by the page window.
 *
 * <p>Implementations must never load an aggregate, never call {@link PlayerWarpRepository#all()}, and never fetch
 * the whole table to paginate in memory. This is the durable read path; a caller runs it off the tick thread.
 */
public interface PlayerWarpBrowse {

    /** One page of cards matching {@code query}, ordered by its {@link WarpQuery#sort()}, with the total count. */
    Page<WarpCard> page(WarpQuery query);
}
