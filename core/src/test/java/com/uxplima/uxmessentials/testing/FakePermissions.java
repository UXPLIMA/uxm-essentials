package com.uxplima.uxmessentials.testing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.Nullable;

/**
 * Permissions held in memory: granted nodes, a resolved quota per node family, and numbered tier nodes held under a
 * family, which fold with the config default the way the real reducer does.
 */
public final class FakePermissions implements Permissions {

    private final Set<String> granted = new HashSet<>();
    private final Map<String, QuotaResult> quotas = new HashMap<>();
    private final Map<String, List<Long>> tiers = new HashMap<>();

    public FakePermissions grant(PlayerRef who, String node) {
        granted.add(who.uuid() + "|" + node);
        return this;
    }

    public FakePermissions quota(String node, QuotaResult result) {
        quotas.put(node, result);
        return this;
    }

    /** Hold the numbered node {@code family.value}, as a permission plugin would grant it. */
    public FakePermissions holdTier(String family, long value) {
        tiers.computeIfAbsent(family, unused -> new ArrayList<>()).add(value);
        return this;
    }

    @Override
    public boolean has(PlayerRef who, String node) {
        return granted.contains(who.uuid() + "|" + node);
    }

    @Override
    public QuotaResult resolveQuota(PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
        QuotaResult fixed = quotas.get(family.node());
        if (fixed != null) {
            return fixed;
        }
        List<Long> held = tiers.getOrDefault(family.node(), List.of());
        if (family.direction() == QuotaReduction.STACK) {
            return QuotaResult.limited(
                    held.isEmpty()
                            ? configDefault
                            : held.stream().mapToLong(Long::longValue).sum());
        }
        long folded = configDefault;
        for (long tier : held) {
            folded = family.direction() == QuotaReduction.MIN ? Math.min(folded, tier) : Math.max(folded, tier);
        }
        return QuotaResult.limited(folded);
    }
}
