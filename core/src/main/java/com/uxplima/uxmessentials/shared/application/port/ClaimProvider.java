package com.uxplima.uxmessentials.shared.application.port;

import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.NullMarked;

/**
 * Port through which claim-policy logic queries the active claim plugin, without coupling to any
 * specific provider SDK. The adapter layer supplies a concrete implementation wired to Lands,
 * GriefPrevention, or whichever plugin is present; the default (no provider installed) is the
 * always-inactive stub that returns {@code active() == false}.
 */
@NullMarked
public interface ClaimProvider {

    /**
     * Whether a claim plugin is currently active and available. When {@code false} every policy
     * check short-circuits to {@code ALLOWED} without querying the provider.
     */
    boolean active();

    /**
     * Returns a {@link ClaimLookup} for the claim that covers the block at
     * ({@code blockX}, {@code blockZ}) in {@code world}, or empty when no claim is present.
     *
     * <p>The coordinates are block-level (i.e. the integer floor of the world coordinates). The
     * implementation is expected to be fast — it is called on the region thread and may be called
     * repeatedly for proximity checks.
     */
    Optional<ClaimLookup> claimAt(WorldRef world, int blockX, int blockZ);

    /**
     * Thin, read-only view of a single claim sufficient for policy evaluation. The adapter
     * translates the claim-plugin's own model into these two predicates.
     */
    @NullMarked
    interface ClaimLookup {

        /**
         * Returns {@code true} when {@code player} is the owner of or a trusted member in this
         * claim and may therefore set a home here.
         */
        boolean isTrusted(UUID player);

        /**
         * Returns {@code true} when {@code player} has been explicitly banned from this claim and
         * should not be permitted to teleport to it.
         */
        boolean isBanned(UUID player);
    }
}
