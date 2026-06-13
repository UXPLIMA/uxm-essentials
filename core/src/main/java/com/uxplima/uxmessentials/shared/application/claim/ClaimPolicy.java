package com.uxplima.uxmessentials.shared.application.claim;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.ClaimProvider;
import com.uxplima.uxmessentials.shared.application.port.ClaimProvider.ClaimLookup;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.shared.domain.claim.ClaimDecision;
import org.jspecify.annotations.NullMarked;

/**
 * Provider-agnostic claim policy. Evaluates whether a player may place a home at a position or
 * teleport to one, based on the operator-configured {@link ClaimPolicySettings} and whatever the
 * injected {@link ClaimProvider} reports about the target block.
 *
 * <p>When the provider is inactive (no claim plugin loaded), every check returns
 * {@link ClaimDecision#ALLOWED} without touching the provider.
 */
@NullMarked
public final class ClaimPolicy {

    private final ClaimProvider provider;
    private final ClaimPolicySettings settings;

    public ClaimPolicy(ClaimProvider provider, ClaimPolicySettings settings) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /**
     * Determines whether {@code player} may place a home at ({@code blockX}, {@code blockZ}) in
     * {@code world}. Mirrors the uxmHome UxmClaimsHook canCreateHomeAt logic.
     *
     * <ul>
     *   <li>Provider inactive → {@code ALLOWED}.
     *   <li>Block inside a claim the player trusts → {@code ALLOWED}.
     *   <li>Block inside a foreign claim and {@code blockForeignClaims} is on → {@code DENIED_FOREIGN}.
     *   <li>Block inside a foreign claim and {@code blockForeignClaims} is off → {@code ALLOWED}.
     *   <li>Block in unclaimed land and {@code requireClaim} is on → {@code DENIED_REQUIRED}.
     *   <li>Block in unclaimed land and {@code foreignChunkDistance} &gt; 0 → proximity check →
     *       {@code DENIED_TOO_CLOSE} when a foreign claim is within that chunk radius.
     *   <li>Otherwise → {@code ALLOWED}.
     * </ul>
     */
    public ClaimDecision canPlace(UUID player, WorldRef world, int blockX, int blockZ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(world, "world");

        if (!provider.active()) {
            return ClaimDecision.ALLOWED;
        }

        Optional<ClaimLookup> claimOpt = provider.claimAt(world, blockX, blockZ);

        if (claimOpt.isPresent()) {
            ClaimLookup lookup = claimOpt.get();
            if (lookup.isTrusted(player)) {
                return ClaimDecision.ALLOWED;
            }
            // Foreign claim exists — either block or allow based on config.
            return settings.blockForeignClaims() ? ClaimDecision.DENIED_FOREIGN : ClaimDecision.ALLOWED;
        }

        // Block is in unclaimed land.
        if (settings.requireClaim()) {
            return ClaimDecision.DENIED_REQUIRED;
        }

        int distance = settings.foreignChunkDistance();
        if (distance > 0) {
            return checkProximity(player, world, blockX, blockZ, distance);
        }

        return ClaimDecision.ALLOWED;
    }

    /**
     * Determines whether {@code player} may teleport to ({@code blockX}, {@code blockZ}) in
     * {@code world}. Returns {@code ALLOWED} immediately when {@code checkTeleportAccess} is off.
     * When a claim covers the block and the player is not trusted there (or is banned),
     * returns {@code DENIED_ACCESS}.
     */
    public ClaimDecision canAccess(UUID player, WorldRef world, int blockX, int blockZ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(world, "world");

        if (!provider.active()) {
            return ClaimDecision.ALLOWED;
        }

        if (!settings.checkTeleportAccess()) {
            return ClaimDecision.ALLOWED;
        }

        Optional<ClaimLookup> claimOpt = provider.claimAt(world, blockX, blockZ);
        if (claimOpt.isEmpty()) {
            // No claim at destination — claim may have been removed; allow teleport.
            return ClaimDecision.ALLOWED;
        }

        ClaimLookup lookup = claimOpt.get();
        return lookup.isTrusted(player) ? ClaimDecision.ALLOWED : ClaimDecision.DENIED_ACCESS;
    }

    /**
     * Scans the chunk grid in the range {@code [-distance, distance]} around the base chunk of
     * ({@code blockX}, {@code blockZ}), skipping the centre. If any chunk contains a claim the
     * player is not trusted in, returns {@code DENIED_TOO_CLOSE}.
     */
    private ClaimDecision checkProximity(UUID player, WorldRef world, int blockX, int blockZ, int distance) {
        int baseChunkX = blockX >> 4;
        int baseChunkZ = blockZ >> 4;

        for (int dx = -distance; dx <= distance; dx++) {
            for (int dz = -distance; dz <= distance; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                // Query a representative block inside the neighbouring chunk.
                int sampleX = (baseChunkX + dx) << 4;
                int sampleZ = (baseChunkZ + dz) << 4;
                Optional<ClaimLookup> nearby = provider.claimAt(world, sampleX, sampleZ);
                if (nearby.isPresent() && !nearby.get().isTrusted(player)) {
                    return ClaimDecision.DENIED_TOO_CLOSE;
                }
            }
        }

        return ClaimDecision.ALLOWED;
    }
}
