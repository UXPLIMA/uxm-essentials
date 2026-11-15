package com.uxplima.uxmessentials.shared.application.hop;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/** How a resolved hop moves the player on the target backend. */
@FunctionalInterface
public interface HopLanding {

    /**
     * On {@code who}'s region thread: move them at once. Exactly one of {@code onLanded} and {@code onMissed} should
     * run, {@code onLanded} only once the player stands at the destination; the arrival guards against both.
     */
    void land(PlayerRef who, Runnable onLanded, Runnable onMissed);
}
