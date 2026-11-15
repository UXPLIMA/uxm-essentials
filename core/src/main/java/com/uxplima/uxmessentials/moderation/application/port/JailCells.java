package com.uxplima.uxmessentials.moderation.application.port;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * Moves a player who arrived from another backend into a jail on this one, and says whether they got there, so a
 * jail hop that cannot land is reported instead of passing for a success. A port because the move is a platform
 * call; tests bind a fake.
 */
@FunctionalInterface
public interface JailCells {

    /**
     * On {@code target}'s entity thread: move them to {@code cell}. Exactly one of {@code onLanded} and
     * {@code onMissed} runs, {@code onLanded} once they stand in the cell.
     */
    void move(PlayerRef target, Position cell, Runnable onLanded, Runnable onMissed);
}
