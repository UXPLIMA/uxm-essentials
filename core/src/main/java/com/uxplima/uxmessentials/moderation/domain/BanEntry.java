package com.uxplima.uxmessentials.moderation.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * One row of the active-ban read model {@code /banlist} renders, a still-in-effect tempban (a {@code /ban}
 * records a far-future tempban row, so both permanent and timed bans live in one table). It is a flat
 * projection of a {@code moderation_tempbans} row, carrying the banned target, who issued it, the optional
 * reason, the wall-clock expiry and when it was issued; the use case resolves the target's display name from the projection's
 * UUID. Read-only: there is no behaviour here, only the fields the list line interpolates.
 *
 * @param target the banned player's UUID
 * @param issuer who issued the ban
 * @param reason the optional free-text reason
 * @param until the wall-clock expiry (a far-future instant for a permanent {@code /ban})
 * @param issuedAt when the ban was issued, which a permanent ban's far-future expiry is measured from
 * @param drawnReason the catalog line {@code reason} was drawn from, when the server wrote it
 */
public record BanEntry(
        java.util.UUID target,
        Issuer issuer,
        Optional<String> reason,
        Instant until,
        Instant issuedAt,
        Optional<DrawnReason> drawnReason) {

    public BanEntry {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(issuer, "issuer");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(until, "until");
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(drawnReason, "drawnReason");
    }

    /** A ban whose reason a staff member gave. */
    public BanEntry(java.util.UUID target, Issuer issuer, Optional<String> reason, Instant until, Instant issuedAt) {
        this(target, issuer, reason, until, issuedAt, Optional.empty());
    }
}
