package com.uxplima.uxmessentials.communication.adapter.outbound;

import java.util.concurrent.ThreadLocalRandom;

import com.uxplima.uxmessentials.communication.application.port.RandomSource;
import org.jspecify.annotations.NullMarked;

/**
 * The production {@link RandomSource}: a bounded draw from {@link ThreadLocalRandom}. Keeping randomness behind
 * the port leaves the announcer's no-immediate-repeat rule and the random connection orderings deterministic
 * under test (a fixed sequence is injected there) while production picks an unbiased index per call. The
 * announcer timer and the connection listeners may run on different threads, so a thread-local generator avoids
 * the contention a single shared {@code Random} would add.
 */
@NullMarked
public final class ThreadLocalRandomSource implements RandomSource {

    @Override
    public int nextBounded(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive: " + bound);
        }
        return ThreadLocalRandom.current().nextInt(bound);
    }
}
