package com.uxplima.uxmessentials.worlds.domain;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * A snapshot of a pregeneration job: {@code done} of {@code total} chunks generated after
 * {@code elapsed} wall-clock time. Derives a completion fraction and an extrapolated time-to-finish.
 */
public record PregenProgress(long done, long total, Duration elapsed) {

    public PregenProgress {
        Objects.requireNonNull(elapsed, "elapsed");
        if (done < 0) {
            throw new IllegalArgumentException("done must not be negative: " + done);
        }
        if (total < 0) {
            throw new IllegalArgumentException("total must not be negative: " + total);
        }
    }

    /** The completed fraction in {@code [0, 1]}; an empty job (no total) is considered complete. */
    public double fraction() {
        return total <= 0 ? 1.0 : Math.min(1.0, (double) done / total);
    }

    /**
     * The estimated remaining time, extrapolated from the current rate. Empty before any progress
     * (no rate yet) and once the job has reached or passed its total.
     */
    public Optional<Duration> eta() {
        if (done <= 0 || done >= total) {
            return Optional.empty();
        }
        long remaining = total - done;
        return Optional.of(elapsed.dividedBy(done).multipliedBy(remaining));
    }
}
