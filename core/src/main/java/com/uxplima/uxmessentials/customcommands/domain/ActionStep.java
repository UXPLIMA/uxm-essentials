package com.uxplima.uxmessentials.customcommands.domain;

import java.time.Duration;
import java.util.Objects;

/**
 * One step of a command's chain: the action token exactly as the operator wrote it, and how long after the command
 * was accepted the step runs. An offset of zero is the common case and runs immediately.
 *
 * <p>The token is opaque here. The domain never asks what {@code console:give %arg_target% diamond} means; it only
 * carries it in order, which is what keeps the vocabulary the adapter owns free to grow without touching this.
 */
public record ActionStep(String token, Duration offset) {

    public ActionStep {
        token = Objects.requireNonNull(token, "token").strip();
        if (token.isBlank()) {
            throw new IllegalArgumentException("an action token must not be blank");
        }
        Objects.requireNonNull(offset, "offset");
        if (offset.isNegative()) {
            throw new IllegalArgumentException("an action offset must not be negative: " + offset);
        }
    }

    /** A step that runs as soon as the chain starts. */
    public static ActionStep now(String token) {
        return new ActionStep(token, Duration.ZERO);
    }

    /** Whether this step waits before it runs, which is what decides between direct dispatch and scheduling. */
    public boolean delayed() {
        return !offset.isZero();
    }
}
