package com.uxplima.uxmessentials.velocity.commandcontrol;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.commandcontrol.domain.PlayerFacts;
import com.velocitypowered.api.proxy.Player;

/**
 * The Velocity-side {@link PlayerFacts} the {@code :core} rule set and hide policy read: the player's
 * permission group (via the {@link ProxyGroupSource}) and a permission check (via the Velocity
 * {@code CommandSource#hasPermission}). This is the proxy twin of the backend's {@code BukkitPlayerFacts},
 * so the same domain decision runs unchanged on the proxy.
 */
public final class ProxyPlayerFacts implements PlayerFacts {

    private final Player player;
    private final ProxyGroupSource groups;

    public ProxyPlayerFacts(Player player, ProxyGroupSource groups) {
        this.player = Objects.requireNonNull(player, "player");
        this.groups = Objects.requireNonNull(groups, "groups");
    }

    @Override
    public Optional<String> group() {
        return groups.groupOf(player.getUniqueId());
    }

    @Override
    public boolean hasPermission(String node) {
        Objects.requireNonNull(node, "node");
        return player.hasPermission(node);
    }
}
