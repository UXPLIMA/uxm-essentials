package com.uxplima.uxmessentials.bossbar.adapter.outbound.api;

import java.util.Objects;

import com.uxplima.uxmessentials.api.bukkit.event.bossbar.UxmBossBarVisibilityEvent;
import com.uxplima.uxmessentials.bossbar.domain.event.BossBarVisibilityToggled;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.EventBridgeRegistry;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.Region;
import org.jspecify.annotations.NullMarked;

/** Which Bukkit event the boss bar fact becomes. */
@NullMarked
public final class BossBarEventBridges {

    private BossBarEventBridges() {}

    public static void register(EventBridgeRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        registry.register(
                BossBarVisibilityToggled.class,
                UxmBossBarVisibilityEvent.getHandlerList(),
                fact -> new UxmBossBarVisibilityEvent(
                        fact.who().uuid(), fact.who().name(), fact.hidden()),
                fact -> Region.entity(fact.who()));
    }
}
