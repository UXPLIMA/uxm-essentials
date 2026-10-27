package com.uxplima.uxmessentials.testing;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.Nullable;

/** Permissions held in memory: granted nodes, and a resolved quota per node family. */
public final class FakePermissions implements Permissions {

    private final Set<String> granted = new HashSet<>();
    private final Map<String, QuotaResult> quotas = new HashMap<>();

    public FakePermissions grant(PlayerRef who, String node) {
        granted.add(who.uuid() + "|" + node);
        return this;
    }

    public FakePermissions quota(String node, QuotaResult result) {
        quotas.put(node, result);
        return this;
    }

    @Override
    public boolean has(PlayerRef who, String node) {
        return granted.contains(who.uuid() + "|" + node);
    }

    @Override
    public QuotaResult resolveQuota(PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
        return quotas.getOrDefault(family.node(), QuotaResult.limited(configDefault));
    }
}
