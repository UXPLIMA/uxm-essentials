package com.uxplima.uxmessentials.testing;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.PlayerClaimLedger;

/** A shared claim ledger in memory: the kit claim is an insert against a unique key, cooldowns are last write wins. */
public final class MemoryClaimLedger implements PlayerClaimLedger {

    private final Set<String> kits = new HashSet<>();
    private final Map<String, Long> cooldowns = new HashMap<>();

    @Override
    public Claims load(UUID player, long now) {
        Set<String> claimed = new HashSet<>();
        String prefix = player + "/";
        kits.stream().filter(key -> key.startsWith(prefix)).forEach(key -> claimed.add(key.substring(prefix.length())));
        Map<String, Long> running = new HashMap<>();
        cooldowns.forEach((key, end) -> {
            if (key.startsWith(prefix) && end > now) {
                running.put(key.substring(prefix.length()), end);
            }
        });
        return new Claims(claimed, running);
    }

    @Override
    public boolean claimKit(UUID player, String kit, long claimedAt) {
        return kits.add(player + "/" + kit);
    }

    @Override
    public void releaseKit(UUID player, String kit) {
        kits.remove(player + "/" + kit);
    }

    @Override
    public void releaseAllKits(UUID player) {
        kits.removeIf(key -> key.startsWith(player + "/"));
    }

    @Override
    public void saveCooldown(UUID player, String scope, long readyAt) {
        cooldowns.put(player + "/" + scope, readyAt);
    }

    public boolean hasKit(UUID player, String kit) {
        return kits.contains(player + "/" + kit);
    }

    public long cooldown(UUID player, String scope) {
        return cooldowns.getOrDefault(player + "/" + scope, 0L);
    }
}
