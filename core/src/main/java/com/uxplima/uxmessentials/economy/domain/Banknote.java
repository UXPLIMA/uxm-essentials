package com.uxplima.uxmessentials.economy.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Domain record representing a physical banknote (check).
 * Paired with a unique UUID token to guarantee anti-duplication validation,
 * a {@link Money} amount representing the value, and a timestamp of creation.
 */
public record Banknote(UUID token, Money money, long createdAt) {

    public Banknote {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(money, "money");
    }

    /** Helper factory to create a fresh banknote with a new random token. */
    public static Banknote createFresh(Money money) {
        return new Banknote(UUID.randomUUID(), money, System.currentTimeMillis());
    }
}
