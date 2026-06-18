package com.uxplima.uxmessentials.worlds.adapter.outbound;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.bukkit.GameRule;

import com.uxplima.uxmessentials.worlds.application.port.GameRuleCatalog;
import org.jspecify.annotations.NullMarked;

/** {@link GameRuleCatalog} over Bukkit's {@link GameRule} registry. */
@NullMarked
public final class BukkitGameRuleCatalog implements GameRuleCatalog {

    // GameRule's name-keyed surface (values/getName) is marked for removal in favour of the namespaced
    // Registry, but the registry is keyed by snake_case ids whereas the vanilla rule names exposed here
    // match what commands and configs use; we keep that surface until a name-preserving replacement ships.
    @Override
    @SuppressWarnings({"deprecation", "removal"})
    public Optional<GameRuleType> typeOf(String name) {
        return Arrays.stream(GameRule.values())
                .filter(rule -> rule.getName().equals(name))
                .findFirst()
                .map(rule -> rule.getType() == Integer.class ? GameRuleType.INTEGER : GameRuleType.BOOLEAN);
    }

    // Same camelCase rule names as typeOf; see the note above for why the removal-marked surface stays.
    @Override
    @SuppressWarnings({"deprecation", "removal"})
    public List<String> names() {
        return Arrays.stream(GameRule.values()).map(GameRule::getName).sorted().toList();
    }
}
