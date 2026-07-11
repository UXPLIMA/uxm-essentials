package com.uxplima.uxmessentials.playerwarps.domain;

import java.util.Locale;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * The role a non-owner player holds on a warp, in descending authority:
 *
 * <ul>
 *   <li>{@link #OWNER} — the warp's creator, with unconditional control. This constant exists so the members
 *       table can carry the owner row too when a use case wants the full roster in one place; the aggregate's
 *       own owner field remains the source of truth for ownership.
 *   <li>{@link #CO_OWNER} — a trusted delegate who may manage the warp almost as fully as the owner.
 *   <li>{@link #MANAGER} — a helper with a narrower set of management actions.
 * </ul>
 *
 * <p>The persisted token is the constant's {@link #name()} (uppercase); {@link #parse(String)} reads it back
 * case-insensitively, matching the {@link WarpAccess} / {@link WarpStatus} idiom already in the package.
 */
public enum WarpRole {
    OWNER,
    CO_OWNER,
    MANAGER;

    /**
     * Match a stored or user-supplied token to a constant, ignoring case and surrounding whitespace. Returns
     * an empty result — never throws — for {@code null}, blank, or unrecognised input.
     */
    public static Optional<WarpRole> parse(@Nullable String token) {
        if (token == null) {
            return Optional.empty();
        }
        String normalised = token.strip().toUpperCase(Locale.ROOT);
        if (normalised.isEmpty()) {
            return Optional.empty();
        }
        for (WarpRole role : values()) {
            if (role.name().equals(normalised)) {
                return Optional.of(role);
            }
        }
        return Optional.empty();
    }
}
