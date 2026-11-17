package com.uxplima.uxmessentials.moderation.application.port;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.domain.JailState;
import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The record of every mute and jail copied from one account onto another spelling of its name on an offline-mode
 * server. A copy is named by the account it came from, its kind and the issue instant it shares with the original.
 *
 * <p>A copy and its record are written together or not at all, so no copy is ever left that its original cannot find.
 * A record is kept after its copy ends: it is how a lifted original finds the copies it made, how a sanction is
 * copied onto an account only once (so a copy staff lift on its own stays lifted), and how a copy is told from an
 * original (so a copy is never copied on, and never comes back to the account it came from).
 */
public interface CaseCopies {

    /** Nothing is ever recorded: the store for a server where nothing is copied. */
    CaseCopies NONE = new CaseCopies() {
        @Override
        public boolean copyMute(PlayerRef target, MuteState expected, MuteState copy, Instant at, Copy record) {
            return false;
        }

        @Override
        public boolean copyJail(PlayerRef target, JailState.Active copy, Instant at, Copy record) {
            return false;
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

    /**
     * Store {@code copy} as {@code target}'s mute, in place of {@code expected}, and keep {@code record} for it, in one
     * write: both or neither. Returns false, writing nothing, when the mute stored is no longer {@code expected}.
     * {@code at} stamps the account's row when this is the first anything stores for it.
     */
    boolean copyMute(PlayerRef target, MuteState expected, MuteState copy, Instant at, Copy record);

    /**
     * Store {@code copy} as {@code target}'s jail and keep {@code record} for it, in one write: both or neither.
     * Returns whether it was stored.
     */
    boolean copyJail(PlayerRef target, JailState.Active copy, Instant at, Copy record);

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
