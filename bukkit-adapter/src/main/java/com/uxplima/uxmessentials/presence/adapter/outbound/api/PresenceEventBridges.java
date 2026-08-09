package com.uxplima.uxmessentials.presence.adapter.outbound.api;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.api.bukkit.event.presence.UxmAfkEvent;
import com.uxplima.uxmessentials.presence.domain.event.ReturnedFromAfk;
import com.uxplima.uxmessentials.presence.domain.event.WentAfk;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.EventBridgeRegistry;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.Region;
import org.jspecify.annotations.NullMarked;

/** Which Bukkit event each presence fact becomes. Both directions share one event with a flag. */
@NullMarked
public final class PresenceEventBridges {

    private PresenceEventBridges() {}

    public static void register(EventBridgeRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        registry.register(
                WentAfk.class,
                UxmAfkEvent.getHandlerList(),
                fact -> new UxmAfkEvent(
                        fact.subject().uuid(), fact.subject().name(), true, fact.reason(), fact.automatic(), fact.at()),
                fact -> Region.entity(fact.subject()));
        registry.register(
                ReturnedFromAfk.class,
                UxmAfkEvent.getHandlerList(),
                fact -> new UxmAfkEvent(
                        fact.subject().uuid(), fact.subject().name(), false, Optional.empty(), false, fact.at()),
                fact -> Region.entity(fact.subject()));
    }
}
