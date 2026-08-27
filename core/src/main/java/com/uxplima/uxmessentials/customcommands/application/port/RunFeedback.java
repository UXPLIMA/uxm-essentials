package com.uxplima.uxmessentials.customcommands.application.port;

import com.uxplima.uxmessentials.customcommands.application.RunOutcome;
import com.uxplima.uxmessentials.customcommands.domain.CustomCommand;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Tells the actor what happened. Every outcome the use case produces passes through here exactly once, which is
 * what keeps the gate order and the lines a player reads in step with each other.
 *
 * <p>The adapter picks the catalog line for the outcome, except for a refused permission on a command that carries
 * its own deny message: that one is operator content and is rendered as written.
 */
public interface RunFeedback {

    /** Report {@code outcome} of running {@code command} to {@code who}. */
    void report(PlayerRef who, CustomCommand command, RunOutcome outcome);
}
