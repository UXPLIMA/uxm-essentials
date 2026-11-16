package com.uxplima.uxmessentials.moderation.application;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.message.SharedMessageKey;

/**
 * Runs the work a moderation command or menu click asks for on the async lane, so its database reads and writes never
 * hold up the server thread. The use cases it runs already reach the running server only through ports that hop to the
 * right thread themselves: a kick runs on the kicked player's own thread once the ban is stored, a broadcast on the
 * global thread, and every line a player reads is delivered on that player's thread in the order it was sent.
 *
 * <p>A player who leaves before the write lands is not kicked, since there is nobody to kick, but the sanction is
 * stored all the same, so the login check refuses them next time; one who comes back before it lands is kicked when
 * it does. A failure tells the staff member the command did not go through, as a failure on the server thread did,
 * and is rethrown so the scheduler logs it.
 */
public final class StorageLane {

    private final Scheduler scheduler;
    private final Notifier notifier;

    public StorageLane(Scheduler scheduler, Notifier notifier) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Run {@code work} for {@code actor} on the async lane. */
    public void run(PlayerRef actor, Runnable work) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(work, "work");
        scheduler.async(() -> {
            try {
                work.run();
            } catch (RuntimeException failure) {
                notifier.send(actor, SharedMessageKey.COMMAND_ERROR);
                throw failure;
            }
        });
    }
}
