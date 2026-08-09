package com.uxplima.uxmessentials.rest.auth;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * One issued token, as it is stored: a name to talk about it by, the hash of the secret, what it may do, and when
 * it was made.
 *
 * <p>The secret itself is not here and is not anywhere. It exists once, in the chat line that issued it. A token
 * that has been lost is revoked and made again, which is the only safe thing a store that cannot read its own
 * secrets can offer.
 *
 * @param label the name an operator gave it, unique, and the name the audit log records for its writes
 * @param hash the SHA-256 of the secret, hex
 * @param scopes what it is allowed to do
 * @param createdAt when it was issued
 */
public record ApiToken(String label, String hash, Set<String> scopes, Instant createdAt) {

    public ApiToken {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(hash, "hash");
        scopes = Set.copyOf(Objects.requireNonNull(scopes, "scopes"));
        Objects.requireNonNull(createdAt, "createdAt");
        if (label.isBlank()) {
            throw new IllegalArgumentException("a token label must not be blank");
        }
    }

    /** Whether this token carries {@code scope}. */
    public boolean allows(String scope) {
        return scopes.contains(scope);
    }
}
