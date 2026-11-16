package com.uxplima.uxmessentials.playerwarps.application;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpLimit;
import com.uxplima.uxmessentials.shared.application.permission.DestinationPermissions;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaFamily;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaResult;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.WorldRef;

/**
 * Resolves an owner's player-warp limits through the shared {@code Permissions} quota reducer. Two limits apply, each
 * to its own count:
 *
 * <ul>
 *   <li>{@link #resolve} caps every warp the owner has, in any world: the highest {@code uxmessentials.pwarp.limit.<n>}
 *       node held, folded with any LuckPerms meta and the config default.
 *   <li>{@link #inWorld} caps the owner's warps in one world only: the highest
 *       {@code uxmessentials.pwarp.limit.<world>.<n>} node held. An owner who holds none for that world has no limit
 *       of their own there, only the one across every world.
 * </ul>
 *
 * <p>The two are resolved apart, never folded together: folded, a world node would be compared with the warps in
 * every world, and a large world node would lift the cap on all of them. The {@code -1} unlimited sentinel
 * short-circuits either to "no limit".
 */
public final class PlayerWarpQuota {

    /** The quota family for player-warp limits: the {@code MAX}-direction reducer over the numbered nodes. */
    public static final QuotaFamily FAMILY = QuotaFamily.quota(DestinationPermissions.PWARP_LIMIT);

    /**
     * The seed of a world limit's fold. No node is this small, so it comes back only when the owner holds no node for
     * the world, which tells "no limit of its own" apart from every number a node can carry.
     */
    private static final long NO_NODE = Long.MIN_VALUE;

    private final Permissions permissions;
    private final int configDefault;

    public PlayerWarpQuota(Permissions permissions, int configDefault) {
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        if (configDefault < 0) {
            throw new IllegalArgumentException("default player warp limit must not be negative: " + configDefault);
        }
        this.configDefault = configDefault;
    }

    /** {@code owner}'s limit on their warps in every world together. */
    public PlayerWarpLimit resolve(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        QuotaResult resolved = permissions.resolveQuota(owner, FAMILY, null, configDefault);
        if (resolved.isUnlimited()) {
            return PlayerWarpLimit.noLimit();
        }
        long value = resolved.orElse(configDefault);
        return PlayerWarpLimit.of(Math.toIntExact(Math.max(0, value)));
    }

    /**
     * {@code owner}'s limit on their warps in {@code world} alone, or empty when they hold no node for that world.
     * Permission nodes are lower case, so the world's name is read lower cased.
     */
    public Optional<PlayerWarpLimit> inWorld(PlayerRef owner, WorldRef world) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(world, "world");
        QuotaFamily family = QuotaFamily.quota(
                DestinationPermissions.pwarpWorldLimit(world.name().toLowerCase(Locale.ROOT)));
        QuotaResult resolved = permissions.resolveQuota(owner, family, null, NO_NODE);
        if (resolved.isUnlimited()) {
            return Optional.of(PlayerWarpLimit.noLimit());
        }
        long value = resolved.orElse(NO_NODE);
        if (value == NO_NODE) {
            return Optional.empty();
        }
        return Optional.of(PlayerWarpLimit.of(Math.clamp(value, 0, Integer.MAX_VALUE)));
    }
}
