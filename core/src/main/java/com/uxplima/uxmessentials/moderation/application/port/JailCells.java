package com.uxplima.uxmessentials.moderation.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;

/**
 * Moves a jailed player into a stored jail cell on this backend and says whether they got there, so a move that
 * cannot happen is reported instead of passing for a success. A port because the move is a platform call; tests bind
 * a fake.
 */
public interface JailCells {

    /**
     * On {@code target}'s entity thread: move them to {@code cell}. Exactly one of {@code onLanded} and
     * {@code onMissed} runs, {@code onLanded} once they stand in the cell.
     */
    void move(PlayerRef target, Position cell, Runnable onLanded, Runnable onMissed);

    /**
     * On a player's entity thread: whether {@code world} is loaded on this backend, by its uid. A jail stamped with a
     * server that no longer answers is still applied here when its world is.
     */
    boolean hasWorld(WorldRef world);
}
