package com.uxplima.uxmessentials.teleport.application;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.permission.TeleportPermissions;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaFamily;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaResult;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.teleport.domain.SafeSearchArea;

/**
 * Resolves a player's random-teleport search radius from the numbered {@code uxmessentials.rtp.radius.<n>} tier
 * through the shared {@link Permissions} quota reducer. The largest tier held wins, and it can only narrow the search:
 * the configured {@code rtp.max-radius} stays the outer edge, so a tier above it searches no further than a player who
 * holds none. A player with no tier node, or with the {@code -1} unlimited one, searches the full configured radius,
 * and the world border still clamps the served location on top ({@link SafeSearchArea#maxRadius()}).
 *
 * <p>This applies to the per-player live search, the {@code /rtp biome} path, which builds a fresh area per request.
 * The plain {@code /rtp} and {@code /rtp <world>} paths serve from the shared, world-wide pre-warmed pool (O(1), not
 * per-player), so they carry no per-player radius by construction.
 */
public final class RtpRadiusTier {

    /** The quota family for the radius tier: {@code uxmessentials.rtp.radius.<n>}, reduced by the maximum. */
    public static final QuotaFamily FAMILY = QuotaFamily.quota(TeleportPermissions.RTP_RADIUS);

    /**
     * The seed of the fold. No tier is this small, so it comes back only when the player holds none, which tells "no
     * tier" apart from every radius a node can carry without seeding the fold with the configured radius.
     */
    private static final long NO_TIER = Long.MIN_VALUE;

    private final Permissions permissions;

    public RtpRadiusTier(Permissions permissions) {
        this.permissions = Objects.requireNonNull(permissions, "permissions");
    }

    /**
     * Narrow {@code base}'s configured outer radius to {@code who}'s resolved {@code rtp.radius} tier. The result is
     * never wider than {@code base}: a tier above the configured radius leaves it as it is.
     */
    public SafeSearchArea clamp(PlayerRef who, SafeSearchArea base) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(base, "base");
        QuotaResult resolved = permissions.resolveQuota(who, FAMILY, null, NO_TIER);
        if (resolved.isUnlimited()) {
            return base;
        }
        long tier = resolved.orElse(NO_TIER);
        if (tier == NO_TIER || tier >= base.configuredMaxRadius()) {
            return base;
        }
        return base.withConfiguredMaxRadius((double) tier);
    }
}
