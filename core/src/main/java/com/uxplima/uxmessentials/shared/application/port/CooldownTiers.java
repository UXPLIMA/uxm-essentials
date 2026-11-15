package com.uxplima.uxmessentials.shared.application.port;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.permission.SharedPermissions;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns.CooldownKind;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns.TierRule;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaFamily;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * How long one player's cooldown lasts, from the numbered tier nodes they hold. Every {@link Cooldowns} adapter sizes
 * its stamps here, so a single server and a network apply one rule.
 *
 * <p>A held tier sets the wait and the shortest one held wins; {@code 0} means no wait. The configured wait applies
 * only when the player holds no tier, so a tier can add a wait the config leaves out as well as shorten one. A kind
 * with {@link TierRule#CAP} keeps its configured wait as a ceiling the tier can only lower. The bypass node is not
 * read here: the adapters check it before they size anything.
 */
public final class CooldownTiers {

    /**
     * The seed of the shortest-wins fold. No tier is this long, so it comes back only when the player holds none,
     * which tells "no tier" apart from any tier value without seeding the fold with the configured wait.
     */
    private static final long NO_TIER = Long.MAX_VALUE;

    private CooldownTiers() {}

    /** The wait in seconds {@code who} gets for {@code kind}. */
    public static long waitSeconds(Permissions permissions, PlayerRef who, CooldownKind kind) {
        Objects.requireNonNull(kind, "kind");
        return resolve(permissions, who, kind.cooldownNode(), kind.defaultSeconds(), kind.tierRule());
    }

    /**
     * The wait in seconds {@code who} gets for the per-command cooldown keyed by {@code label}, with
     * {@code configSeconds} as the wait when they hold no {@code uxmessentials.cooldown.<label>.<seconds>} tier.
     */
    public static long labelWaitSeconds(Permissions permissions, PlayerRef who, String label, long configSeconds) {
        Objects.requireNonNull(label, "label");
        return resolve(permissions, who, SharedPermissions.cooldown(label), configSeconds, TierRule.SET);
    }

    private static long resolve(
            Permissions permissions, PlayerRef who, String tierNode, long configSeconds, TierRule rule) {
        Objects.requireNonNull(permissions, "permissions");
        Objects.requireNonNull(who, "who");
        long held = permissions
                .resolveQuota(who, QuotaFamily.threshold(tierNode), null, NO_TIER)
                .orElse(NO_TIER);
        if (held == NO_TIER) {
            return configSeconds;
        }
        long tier = Math.max(0L, held);
        return rule == TierRule.CAP ? Math.min(tier, configSeconds) : tier;
    }
}
