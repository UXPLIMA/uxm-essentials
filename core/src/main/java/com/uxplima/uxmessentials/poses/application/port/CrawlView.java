package com.uxplima.uxmessentials.poses.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * Outbound port over the client-only fake block behind {@code /crawl}. A crawling player is held in the prone
 * swimming pose by a block the client believes fills the space its head would occupy when standing — the block a
 * block above the feet. The server never places a real block, so this port always sends the block to a single
 * player's connection (a client override) and never mutates the world; there is no real suffocation and no
 * server-side block update.
 *
 * <p>The two calls are the whole ghost-prevention contract for crawl. {@link #showFakeBlockAbove} sends the
 * invisible fake block at the head position, so the client cannot stand and stays crawling; as the player walks
 * the caller {@link #restoreRealBlock restores} the block at the old head and shows the fake block at the new one,
 * following the player. On every exit — stop, quit, teleport, death, world-change, chunk-unload — the caller
 * restores the real block at the last fake position so the client can never be left with a phantom block on screen.
 * Restoring a block the client already sees correctly is a harmless no-op, so the restore is safe to send on every
 * exit path.
 *
 * <p>The swimming {@code DATA_POSE} that makes onlookers see the crawl is a separate concern handled through
 * {@link PosePort} (the same seam the free poses use); this port owns only the fake block. The adapter resolves the
 * live player and world from the refs, running on the player's own region thread, and no-ops when the player is
 * offline.
 */
public interface CrawlView {

    /**
     * Show {@code who} a client-only fake block at {@code headBlock} — the block their head occupies when standing,
     * a block above their feet — so their client cannot stand and holds the crawl. A no-op when the player is
     * offline, and skipped when a real solid block already fills that space (the player is already in a low gap, so
     * no fake block is needed there).
     */
    void showFakeBlockAbove(PlayerRef who, Position headBlock);

    /**
     * Restore the real block at {@code headBlock} for {@code who} — resend the world's actual block data so the
     * client sees reality again where a fake block once sat. Called on every crawl exit and on each move (for the
     * block just left behind), so no phantom fake block is ever stranded on a player's screen. A no-op when the
     * player is offline.
     */
    void restoreRealBlock(PlayerRef who, Position headBlock);
}
