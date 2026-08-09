package com.uxplima.uxmessentials.api.action;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import org.jspecify.annotations.NullMarked;

/**
 * What happened, for an action with nothing to hand back.
 *
 * <pre>{@code
 * homes.delete(playerId, "base").thenAccept(outcome -> {
 *     if (!outcome.succeeded()) {
 *         getLogger().info("could not delete: " + outcome.failureOrThrow().message());
 *     }
 * });
 * }</pre>
 *
 * @param failure why it did not happen, or empty when it did
 */
@NullMarked
public record UxmOutcome(Optional<UxmFailure> failure) {

    private static final UxmOutcome OK = new UxmOutcome(Optional.empty());

    public UxmOutcome {
        Objects.requireNonNull(failure, "failure");
    }

    /** It happened. */
    public static UxmOutcome ok() {
        return OK;
    }

    /** It did not happen, for this reason. */
    public static UxmOutcome failed(UxmFailure failure) {
        return new UxmOutcome(Optional.of(Objects.requireNonNull(failure, "failure")));
    }

    /** It did not happen, for this reason. */
    public static UxmOutcome failed(String code, String message) {
        return failed(UxmFailure.of(code, message));
    }

    /** Whether it happened. */
    public boolean succeeded() {
        return failure.isEmpty();
    }

    /** Run {@code handler} with the failure when there is one, and nothing otherwise. */
    public void ifFailed(Consumer<UxmFailure> handler) {
        failure.ifPresent(Objects.requireNonNull(handler, "handler"));
    }

    /** The failure, for a caller that has already checked {@link #succeeded()}. */
    public UxmFailure failureOrThrow() {
        return failure.orElseThrow(() -> new IllegalStateException("the action succeeded, so there is no failure"));
    }
}
