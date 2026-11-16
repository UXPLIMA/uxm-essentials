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

    /**
     * {@code step}, a part of a landing that a scheduler runs later, made to end the claimed hop as a miss when it
     * throws. Once a landing has hopped threads the arrival's own guard is no longer on the stack, and a scheduler only
     * logs what a task throws, so without this the player would get neither the move nor the money back. The failure
     * is passed on so it is still logged; the arrival's guard keeps a later outcome from counting twice.
     */
    static Runnable missOnFailure(Runnable step, Runnable onMissed) {
        return () -> {
            try {
                step.run();
            } catch (RuntimeException failed) {
                onMissed.run();
                throw failed;
            }
        };
    }
}
