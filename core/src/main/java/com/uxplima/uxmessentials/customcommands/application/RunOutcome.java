package com.uxplima.uxmessentials.customcommands.application;

import java.time.Duration;
import java.util.Objects;

/**
 * What running a custom command did, one case per gate the run can stop at plus the accepted one. The use case
 * returns exactly one of these, and the feedback port turns it into the line the actor reads.
 *
 * <p>{@link #gate()} names the gate in a short stable token ({@code console}, {@code permission}, {@code depth},
 * {@code requirements}, {@code cooldown}, {@code warmup}, {@code cost}). {@code /customcmd test} renders it into
 * its report, so the token is part of the operator surface and does not change with the wording of a message.
 */
public sealed interface RunOutcome {

    /** The gate this outcome stopped at, or {@code none} when nothing stopped the run. */
    String gate();

    /** Every gate opened and the chain ran. */
    record Ok() implements RunOutcome {
        @Override
        public String gate() {
            return "none";
        }
    }

    /** A non-player sender ran a command whose file does not allow the console. */
    record ConsoleDenied() implements RunOutcome {
        @Override
        public String gate() {
            return "console";
        }
    }

    /** The actor does not hold the permission node the definition declares. */
    record NoPermission() implements RunOutcome {
        @Override
        public String gate() {
            return "permission";
        }
    }

    /** The command called itself past the configured depth, so the re-entry was refused. */
    record DepthExceeded() implements RunOutcome {
        @Override
        public String gate() {
            return "depth";
        }
    }

    /** A declared requirement was unmet; the definition's deny chain has already run when it has one. */
    record RequirementsFailed() implements RunOutcome {
        @Override
        public String gate() {
            return "requirements";
        }
    }

    /** The actor must wait {@code remaining} before running the command again. */
    record OnCooldown(Duration remaining) implements RunOutcome {

        public OnCooldown {
            Objects.requireNonNull(remaining, "remaining");
        }

        @Override
        public String gate() {
            return "cooldown";
        }
    }

    /** The chain is waiting out a warmup of {@code countdown}; movement cancels it. */
    record WarmupStarted(Duration countdown) implements RunOutcome {

        public WarmupStarted {
            Objects.requireNonNull(countdown, "countdown");
        }

        @Override
        public String gate() {
            return "warmup";
        }
    }

    /** The actor moved before the warmup finished, so nothing was charged and nothing ran. */
    record WarmupCancelled() implements RunOutcome {
        @Override
        public String gate() {
            return "warmup";
        }
    }

    /** The command costs {@code cost} and the actor cannot pay it. */
    record CannotAfford(double cost) implements RunOutcome {
        @Override
        public String gate() {
            return "cost";
        }
    }
}
