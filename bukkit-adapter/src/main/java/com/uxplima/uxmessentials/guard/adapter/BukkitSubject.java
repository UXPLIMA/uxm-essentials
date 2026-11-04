package com.uxplima.uxmessentials.guard.adapter;

import com.uxplima.uxmessentials.guard.domain.GuardSubject;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.PlaceholderApiSupport;
import com.uxplima.uxmlib.condition.Comparison;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

/**
 * A player as the guard engine sees them. Permission targets ask Bukkit; condition targets use the uxmLib
 * comparison over PlaceholderAPI, the same vocabulary as menus and click actions. A condition that does not parse
 * is not met, so a typo never opens a region.
 */
public final class BukkitSubject implements GuardSubject {

    private final Player player;

    public BukkitSubject(Player player) {
        this.player = player;
    }

    public Player player() {
        return player;
    }

    @Override
    public @Nullable UUID id() {
        return player.getUniqueId();
    }

    @Override
    public boolean has(String node) {
        return player.hasPermission(node);
    }

    @Override
    public boolean meets(String expression) {
        Comparison.ParsedComparison parsed;
        try {
            parsed = Comparison.parse(expression);
        } catch (IllegalArgumentException malformed) {
            return false;
        }
        UnaryOperator<String> bridge = PlaceholderApiSupport.messageBridge(player.getUniqueId());
        return parsed.comparison().test(bridge.apply(parsed.left()), bridge.apply(parsed.right()));
    }

    @Override
    public String toString() {
        return player.getName();
    }
}
