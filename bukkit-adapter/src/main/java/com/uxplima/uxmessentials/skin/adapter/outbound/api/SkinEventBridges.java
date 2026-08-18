package com.uxplima.uxmessentials.skin.adapter.outbound.api;

import java.util.Objects;

import com.uxplima.uxmessentials.api.bukkit.event.skin.UxmSkinChangeEvent;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.EventBridgeRegistry;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.Region;
import com.uxplima.uxmessentials.skin.domain.event.SkinChanged;
import com.uxplima.uxmessentials.skin.domain.event.SkinCleared;
import org.jspecify.annotations.NullMarked;

/**
 * Which Bukkit event the two skin facts become.
 *
 * <p>Both become the same published event, since a listener that redraws a face cares that the face changed rather
 * than which door it came through; a clear is told apart by its own flag. Each follows the player who is wearing
 * it, because every listener that cares is about to touch them.
 */
@NullMarked
public final class SkinEventBridges {

    private SkinEventBridges() {}

    public static void register(EventBridgeRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        registry.register(
                SkinChanged.class,
                UxmSkinChangeEvent.getHandlerList(),
                fact -> new UxmSkinChangeEvent(
                        fact.who().uuid(),
                        fact.who().name(),
                        SkinSources.typeOf(fact.source()),
                        fact.source().value(),
                        false),
                fact -> Region.entity(fact.who()));
        registry.register(
                SkinCleared.class,
                UxmSkinChangeEvent.getHandlerList(),
                fact -> new UxmSkinChangeEvent(fact.who().uuid(), fact.who().name(), "", "", true),
                fact -> Region.entity(fact.who()));
    }
}
