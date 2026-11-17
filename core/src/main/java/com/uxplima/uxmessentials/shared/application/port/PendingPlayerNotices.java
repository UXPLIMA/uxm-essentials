package com.uxplima.uxmessentials.shared.application.port;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerNotice;

/**
 * The lines kept for players who were not online when they were meant for them, in the shared database, until the
 * player is next online on any server of it. Only so many lines are kept for one player, the newest; and a line nobody
 * came back for is dropped once it is old ({@link #purgeOlderThan}), so a player who never returns, or one sent a
 * flood of lines, costs a bounded number of rows.
 */
public interface PendingPlayerNotices {

    /** Keep {@code notice} until its recipient is next online, dropping their oldest lines past the limit. */
    void keep(PlayerNotice notice);

    /**
     * Take the notices kept for {@code recipient}, oldest first, removing them. A notice is handed to one taker only,
     * so two servers taking at once never show the same one twice.
     */
    List<PlayerNotice> take(UUID recipient);

    /** Drop every line kept since before {@code cutoff}; returns how many. */
    int purgeOlderThan(Instant cutoff);
}
