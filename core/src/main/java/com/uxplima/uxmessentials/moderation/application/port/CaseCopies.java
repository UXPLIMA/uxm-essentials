package com.uxplima.uxmessentials.moderation.application.port;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The record of every mute and jail copied from one account onto another spelling of its name on an offline-mode
 * server. A copy is named by the account it came from, its kind and the issue instant it shares with the original.
 *
 * <p>A record is kept after its copy ends: it is how a lifted original finds the copies it made, how a sanction is
 * copied onto an account only once (so a copy staff lift on its own stays lifted), and how a copy is told from an
 * original (so a copy is never copied on, and never comes back to the account it came from).
 */
public interface CaseCopies {

    /** Nothing is ever recorded: the store for a server where nothing is copied. */
    CaseCopies NONE = new CaseCopies() {
        @Override
        public void record(Copy copy) {
            // Nothing is copied, so there is nothing to keep.
        }

        @Override
        public List<Copy> heldBy(Collection<UUID> targets) {
            return List.of();
        }

        @Override
        public List<Copy> madeFrom(UUID origin, Kind kind) {
            return List.of();
        }
    };

    /** The kinds of sanction that are copied. */
    enum Kind {
        MUTE,
        JAIL
    }

    /** Keep {@code copy}; keeping one already kept changes nothing. */
    void record(Copy copy);

    /** Every copy ever made onto any of {@code targets}. */
    List<Copy> heldBy(Collection<UUID> targets);

    /** Every copy of kind {@code kind} ever made from a sanction of {@code origin}. */
    List<Copy> madeFrom(UUID origin, Kind kind);

    /** One copy: {@code target} was given a copy of the {@code kind} {@code origin} was issued at {@code issuedAt}. */
    record Copy(UUID target, Kind kind, UUID origin, Instant issuedAt) {

        public Copy {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(origin, "origin");
            Objects.requireNonNull(issuedAt, "issuedAt");
        }
    }
}
