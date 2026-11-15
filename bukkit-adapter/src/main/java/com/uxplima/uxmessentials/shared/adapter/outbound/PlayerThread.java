package com.uxplima.uxmessentials.shared.adapter.outbound;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Runs work that reads or moves a player on that player's own region thread. Use cases that look something up on the
 * async lane and then start a teleport come through here, since the teleport reads where the player stands and what
 * they may do, which on Folia only their region thread may ask.
 */
@NullMarked
public final class PlayerThread {

    private PlayerThread() {}

    /**
     * Run {@code task} now when the caller already owns {@code player}'s region, otherwise after one hop to it. When
     * the player is gone before the hop lands, {@code retired} runs instead, so the caller can undo what it set up.
     */
    public static void run(Scheduler scheduler, PlayerRef player, Runnable task, Runnable retired) {
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(task, "task");
        Objects.requireNonNull(retired, "retired");
        if (scheduler.ownsEntity(player)) {
            task.run();
        } else {
            scheduler.onEntity(player, task, retired);
        }
    }
}
