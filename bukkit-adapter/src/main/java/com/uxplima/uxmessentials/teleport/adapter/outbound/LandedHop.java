package com.uxplima.uxmessentials.teleport.adapter.outbound;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Reports a hop {@code teleportAsync} completed: the player has moved, so the landing is reported whatever happens
 * next. The arrival's side effects (the teleport event, the title, the sound) run on the player's region thread
 * first, but a paid teleport settles its money on the landing, so neither a side effect that throws, nor a player
 * who left in that tick, nor a scheduler that is stopping may swallow it.
 */
@NullMarked
final class LandedHop {

    private LandedHop() {}

    /**
     * On {@code who}'s region thread run {@code arrival}, then {@code landed}. When the player is gone before that
     * thread is reached, or the scheduler refuses the hop, {@code landed} still runs, from the calling thread, with
     * the arrival skipped: there is nobody left to show it to. {@code landed} may therefore run twice in a race and
     * must be once-only itself.
     */
    static void report(Scheduler scheduler, Logger log, PlayerRef who, Runnable arrival, Runnable landed) {
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(log, "log");
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(arrival, "arrival");
        Objects.requireNonNull(landed, "landed");
        Runnable onPlayerThread = () -> {
            try {
                arrival.run();
            } catch (RuntimeException arrivalFailure) {
                log.error("arrival effects for " + who.name() + " failed after the hop landed", arrivalFailure);
            } finally {
                landed.run();
            }
        };
        try {
            scheduler.onEntity(who, onPlayerThread, landed);
        } catch (RuntimeException refused) {
            log.error("arrival of " + who.name() + " could not reach their thread after the hop landed", refused);
            landed.run();
        }
    }
}
