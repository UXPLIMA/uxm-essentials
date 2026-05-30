package com.uxplima.uxmessentials.homes.application;

import java.util.Objects;

import com.uxplima.uxmessentials.homes.domain.HomeLimit;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaFamily;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaResult;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.WorldRef;

/**
 * Resolves an owner's home limit through the shared {@code Permissions} quota reducer. The home limit is
 * the highest {@code uxmessentials.home.limit.<n>} node the owner holds (optionally scoped to the home's
 * world via {@code uxmessentials.home.limit.<world>.<n>}), folded together with any LuckPerms meta and the
 * per-context config default; the {@code -1} unlimited sentinel short-circuits to "no limit". The result
 * is handed to the aggregate as a {@link HomeLimit} value object, so the {@code max}/sentinel semantics
 * live entirely in the one shared reducer (docs/permissions.md, the numbered-node convention).
 */
public final class HomeQuota {

    /** The quota family for home limits — the {@code MAX}-direction reducer over the numbered nodes. */
    public static final QuotaFamily FAMILY = QuotaFamily.quota("uxmessentials.home.limit");

    private final Permissions permissions;
    private final int configDefault;

    public HomeQuota(Permissions permissions, int configDefault) {
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        if (configDefault < 0) {
            throw new IllegalArgumentException("default home limit must not be negative: " + configDefault);
        }
        this.configDefault = configDefault;
    }

    /**
     * Resolve {@code owner}'s home limit, scoped to {@code world} so the world-scoped node form folds in.
     * Pass the world the new home would live in; a {@code null} world resolves the unscoped family only.
     */
    public HomeLimit resolve(PlayerRef owner, WorldRef world) {
        Objects.requireNonNull(owner, "owner");
        QuotaResult resolved = permissions.resolveQuota(owner, FAMILY, world, configDefault);
        if (resolved.isUnlimited()) {
            return HomeLimit.noLimit();
        }
        long value = resolved.orElse(configDefault);
        return HomeLimit.of(Math.toIntExact(Math.max(0, value)));
    }
}
