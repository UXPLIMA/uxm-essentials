package com.uxplima.uxmessentials.vanish.adapter.outbound.api;

import java.util.Objects;

import com.uxplima.uxmessentials.api.bukkit.event.vanish.UxmVanishToggleEvent;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.EventBridgeRegistry;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.Region;
import com.uxplima.uxmessentials.vanish.domain.event.VanishToggled;
import org.jspecify.annotations.NullMarked;

/**
 * Which Bukkit event the vanish fact becomes.
 *
 * <p>It follows the player who was hidden, since every listener that cares is about to touch them: their tab entry,
 * their nametag, whatever else was drawing them.
 */
@NullMarked
public final class VanishEventBridges {

    private VanishEventBridges() {}

    public static void register(EventBridgeRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        registry.register(
                VanishToggled.class,
                UxmVanishToggleEvent.getHandlerList(),
                fact -> new UxmVanishToggleEvent(
                        fact.player().uuid(),
                        fact.player().name(),
                        fact.vanished(),
                        fact.level().level()),
                fact -> Region.entity(fact.player()));
    }
}
