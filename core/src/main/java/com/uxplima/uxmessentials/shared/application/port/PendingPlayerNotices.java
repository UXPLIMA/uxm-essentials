package com.uxplima.uxmessentials.shared.application.port;

import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerNotice;

/**
 * The lines kept for players who were not online when they were meant for them, in the shared database, until the
 * player is next online on any server of it.
 */
public interface PendingPlayerNotices {

    /** Keep {@code notice} until its recipient is next online. */
    void keep(PlayerNotice notice);

    /**
     * Take the notices kept for {@code recipient}, oldest first, removing them. A notice is handed to one taker only,
     * so two servers taking at once never show the same one twice.
     */
    List<PlayerNotice> take(UUID recipient);
}
