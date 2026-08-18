package com.uxplima.uxmessentials.poses.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * Outbound port over everything that holds a player prone for {@code /crawl}. A crawl is two things at once: the
 * server-side swimming pose, which lays the body flat for every onlooker and for the crawler's own camera, and a
 * ceiling low enough that the client refuses to stand back up. This port owns both, so the application only ever
 * says "hold this player here" or "let them go".
 *
 * <p>The ceiling is deliberately <em>not</em> a block. A fake block sent to one client is solid to that client in
 * every sense: the third-person camera collides with it and pulls in, and the light and shadow around it change, so
 * the player sees an invisible box over their head. The adapter instead sends a client-only entity whose collision
 * box sits just above the crawler, which the client honours for movement without ever treating it as terrain.
 *
 * <p>{@link #hold} is idempotent and doubles as the follow call: the crawler walks around, so every move re-states
 * where the ceiling belongs. {@link #release} undoes the whole thing (pose and ceiling) and is safe to call for a
 * player who is not crawling, which keeps it callable on every exit path: stop, quit, teleport, death, world
 * change. Both calls resolve the live player from the ref and no-op when they are offline.
 */
public interface CrawlView {

    /**
     * Hold {@code who} prone at {@code feet}: put them in the swimming pose and keep the ceiling just above them.
     * Called once when the crawl begins and again on every move, so the ceiling follows the player. A no-op when
     * the player is offline.
     */
    void hold(PlayerRef who, Position feet);

    /**
     * Let {@code who} stand back up: clear the swimming pose and drop the ceiling. Safe for a player who was never
     * crawling, so every crawl exit can call it unconditionally.
     */
    void release(PlayerRef who);
}
