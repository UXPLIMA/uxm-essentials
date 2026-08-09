package com.uxplima.uxmessentials.shared.adapter.outbound.api;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.NullMarked;

/**
 * Where a notification event is delivered: the thread that owns whatever the event is about.
 *
 * <p>On Paper this is one main thread and the distinction is free; on Folia it is the difference between delivering
 * an event on the thread that owns the subject and delivering it on the wrong one, where a listener touching the
 * subject would be a data race. Carrying the choice as a value rather than deciding it in the bridge is what lets
 * each bridged event declare its own answer next to its mapper, where the fact's shape is obvious.
 */
@NullMarked
public sealed interface Region {

    /** Schedule {@code task} onto this region through the kernel scheduler. */
    void schedule(Scheduler scheduler, Runnable task);

    /** The region owning a player's entity: the right answer for almost every event, which is about a player. */
    static Region entity(PlayerRef player) {
        return new Entity(player);
    }

    /** The region owning a place: for a fact about a location whose player may be elsewhere or absent. */
    static Region at(Position position) {
        return new At(position);
    }

    /** The global region: genuinely server-wide facts only, since it serialises and costs Folia its parallelism. */
    static Region global() {
        return Global.INSTANCE;
    }

    record Entity(PlayerRef player) implements Region {
        public Entity {
            Objects.requireNonNull(player, "player");
        }

        @Override
        public void schedule(Scheduler scheduler, Runnable task) {
            scheduler.onEntity(player, task);
        }
    }

    record At(Position position) implements Region {
        public At {
            Objects.requireNonNull(position, "position");
        }

        @Override
        public void schedule(Scheduler scheduler, Runnable task) {
            scheduler.onRegion(position, task);
        }
    }

    enum Global implements Region {
        INSTANCE;

        @Override
        public void schedule(Scheduler scheduler, Runnable task) {
            scheduler.onGlobal(task);
        }
    }
}
