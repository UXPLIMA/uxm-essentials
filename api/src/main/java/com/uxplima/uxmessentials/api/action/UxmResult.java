package com.uxplima.uxmessentials.api.action;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * What happened, for an action that produces something: the home it created, the balance it left behind.
 *
 * <p>Exactly one of the two is present, so {@link #succeeded()} and {@link #value()} never disagree.
 *
 * @param <T> what the action produced
 */
@NullMarked
public final class UxmResult<T> {

    private final @Nullable T value;
    private final Optional<UxmFailure> failure;

    private UxmResult(@Nullable T value, Optional<UxmFailure> failure) {
        this.value = value;
        this.failure = failure;
    }

    /** It happened, and this is what it produced. */
    public static <T> UxmResult<T> ok(T value) {
        return new UxmResult<>(Objects.requireNonNull(value, "value"), Optional.empty());
    }

    /** It did not happen, for this reason. */
    public static <T> UxmResult<T> failed(UxmFailure failure) {
        return new UxmResult<>(null, Optional.of(Objects.requireNonNull(failure, "failure")));
    }

    /** It did not happen, for this reason. */
    public static <T> UxmResult<T> failed(String code, String message) {
        return failed(UxmFailure.of(code, message));
    }

    /** Whether it happened. */
    public boolean succeeded() {
        return value != null;
    }

    /** What it produced, or empty when it did not happen. */
    public Optional<T> value() {
        return Optional.ofNullable(value);
    }

    /** Why it did not happen, or empty when it did. */
    public Optional<UxmFailure> failure() {
        return failure;
    }

    /** What it produced, for a caller that has already checked {@link #succeeded()}. */
    public T valueOrThrow() {
        if (value == null) {
            throw new IllegalStateException(
                    "the action failed: " + failureOrThrow().message());
        }
        return value;
    }

    /** The failure, for a caller that has already checked {@link #succeeded()}. */
    public UxmFailure failureOrThrow() {
        return failure.orElseThrow(() -> new IllegalStateException("the action succeeded, so there is no failure"));
    }

    /** Run {@code handler} with the failure when there is one, and nothing otherwise. */
    public void ifFailed(Consumer<UxmFailure> handler) {
        failure.ifPresent(Objects.requireNonNull(handler, "handler"));
    }

    /** This result with the value dropped, for a caller that only wanted to know whether it worked. */
    public UxmOutcome asOutcome() {
        return failure.map(UxmOutcome::failed).orElseGet(UxmOutcome::ok);
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof UxmResult<?> result
                && Objects.equals(value, result.value)
                && failure.equals(result.failure);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value, failure);
    }

    @Override
    public String toString() {
        return value != null ? "UxmResult[ok " + value + "]" : "UxmResult[failed " + failure.orElseThrow() + "]";
    }
}
