package com.uxplima.uxmessentials.shared.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * One observed link between an account and the address it connected from, expressed as an opaque IP token rather
 * than a raw address: the adapter tokenises the connecting IP before it ever reaches the domain, so this value
 * carries no reversible network data. It is the atom the alt lookups group over: two accounts that ever produced
 * associations with the same {@code ipToken} are alts of each other.
 *
 * <p>Kernel-owned because both the moderation context (its {@code /alts} and {@code /seenip} reads) and the
 * security context (its join-time alt cap and {@code /ipalts}) answer from the same associations. Neither context
 * owns the capture.
 *
 * @param account the player the connection belongs to
 * @param ipToken the one-way token standing in for the address the connection came from
 */
public record IpAssociation(UUID account, String ipToken) {

    public IpAssociation {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(ipToken, "ipToken");
        if (ipToken.isBlank()) {
            throw new IllegalArgumentException("ipToken must not be blank");
        }
    }
}
