package com.uxplima.uxmessentials.homes.application;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.homes.domain.HomeLimit;
import com.uxplima.uxmessentials.shared.application.permission.DestinationPermissions;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaFamily;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaReduction;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaResult;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.WorldRef;

/**
 * Resolves an owner's home limit through the shared {@code Permissions} quota reducer. How multiple
 * {@code uxmessentials.home.limit.<n>} tiers combine is governed by {@code limitMode}: {@code MAX} keeps
 * the highest tier the player holds (the default "highest wins" behavior), {@code STACK} sums all tiers
 * the player holds (additive slots). LuckPerms meta is treated identically to a node, and the {@code -1} unlimited
 * sentinel short-circuits to "no limit".
 *
 * <p>The world form ({@code uxmessentials.home.limit.<world>.<n>}) is resolved apart by {@link #inWorld} and caps the
 * owner's homes in that world only, the highest node held for it winning. Folded into the total, a world node would be
 * compared with the homes in every world: a small one would not limit the world, and a large one would lift the cap on
 * all of them.
 * The result is handed to the aggregate as a {@link HomeLimit} value object, so the reduction semantics
 * live entirely in the one shared reducer (docs/permissions.md, the numbered-node convention).
 */
public final class HomeQuota {

    private static final String NODE = DestinationPermissions.HOME_LIMIT;

    /**
     * The seed of a world limit's fold. No node is this small, so it comes back only when the owner holds no node for
     * the world, which tells "no limit of its own" apart from every number a node can carry.
     */
    private static final long NO_NODE = Long.MIN_VALUE;

    private final Permissions permissions;
    private final QuotaFamily family;
    private final int configDefault;

    /**
     * @param permissions the permission port used to resolve quota nodes
     * @param configDefault the per-server default home count for players with no matching tier node
     * @param limitMode how multiple tier nodes combine. {@link QuotaReduction#MAX} keeps the highest,
     *     {@link QuotaReduction#STACK} sums them all
     */
    public HomeQuota(Permissions permissions, int configDefault, QuotaReduction limitMode) {
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        if (configDefault < 0) {
            throw new IllegalArgumentException("default home limit must not be negative: " + configDefault);
        }
        this.configDefault = configDefault;
        Objects.requireNonNull(limitMode, "limitMode");
        this.family = limitMode == QuotaReduction.STACK ? QuotaFamily.stack(NODE) : QuotaFamily.quota(NODE);
    }

    /**
     * The quota family built for the configured {@code limitMode}. Exposed so placeholders and views can
     * pass the same family to {@link Permissions#resolveQuota} without constructing a second instance.
     */
    public QuotaFamily family() {
        return family;
    }

    /** {@code owner}'s limit on their homes in every world together. */
    public HomeLimit resolve(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        QuotaResult resolved = permissions.resolveQuota(owner, family, null, configDefault);
        if (resolved.isUnlimited()) {
            return HomeLimit.noLimit();
        }
        long value = resolved.orElse(configDefault);
        return HomeLimit.of(Math.toIntExact(Math.max(0, value)));
    }

    /**
     * {@code owner}'s limit on their homes in {@code world} alone, or empty when they hold no node for that world.
     * Permission nodes are lower case, so the world's name is read lower cased.
     */
    public Optional<HomeLimit> inWorld(PlayerRef owner, WorldRef world) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(world, "world");
        QuotaFamily worldFamily = QuotaFamily.quota(
                DestinationPermissions.homeWorldLimit(world.name().toLowerCase(Locale.ROOT)));
        QuotaResult resolved = permissions.resolveQuota(owner, worldFamily, null, NO_NODE);
        if (resolved.isUnlimited()) {
            return Optional.of(HomeLimit.noLimit());
        }
        long value = resolved.orElse(NO_NODE);
        if (value == NO_NODE) {
            return Optional.empty();
        }
        return Optional.of(HomeLimit.of(Math.clamp(value, 0, Integer.MAX_VALUE)));
    }
}
