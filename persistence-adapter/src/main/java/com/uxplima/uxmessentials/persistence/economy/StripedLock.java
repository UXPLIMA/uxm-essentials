package com.uxplima.uxmessentials.persistence.economy;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.jspecify.annotations.NullMarked;

/**
 * A local striped lock array monitor using {@link ReentrantLock}s.
 * Stripes are indexed by the hash code of the player's {@link UUID} to prevent global lock contention.
 */
@NullMarked
public final class StripedLock {

    private final Lock[] locks;

    public StripedLock(int stripes) {
        if (stripes <= 0) {
            throw new IllegalArgumentException("Stripes must be positive: " + stripes);
        }
        this.locks = new Lock[stripes];
        for (int i = 0; i < stripes; i++) {
            this.locks[i] = new ReentrantLock();
        }
    }

    public Lock get(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        // Mask the sign bit rather than Math.abs: abs(Integer.MIN_VALUE) is still negative and would throw
        // ArrayIndexOutOfBounds, so clear the high bit to land in [0, 2^31) before the modulo.
        int index = (uuid.hashCode() & 0x7fffffff) % locks.length;
        return locks[index];
    }
}
