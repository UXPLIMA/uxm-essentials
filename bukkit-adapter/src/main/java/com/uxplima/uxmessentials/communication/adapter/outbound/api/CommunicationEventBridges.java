package com.uxplima.uxmessentials.communication.adapter.outbound.api;

import java.util.Objects;

import com.uxplima.uxmessentials.api.bukkit.event.communication.UxmAnnouncerReloadEvent;
import com.uxplima.uxmessentials.api.bukkit.event.communication.UxmBroadcastOptOutEvent;
import com.uxplima.uxmessentials.communication.domain.event.AnnouncerReloaded;
import com.uxplima.uxmessentials.communication.domain.event.BroadcastOptOutToggled;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.EventBridgeRegistry;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.Region;
import org.jspecify.annotations.NullMarked;

/** Which Bukkit event each communication fact becomes. */
@NullMarked
public final class CommunicationEventBridges {

    private CommunicationEventBridges() {}

    public static void register(EventBridgeRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        registry.register(
                BroadcastOptOutToggled.class,
                UxmBroadcastOptOutEvent.getHandlerList(),
                fact -> new UxmBroadcastOptOutEvent(
                        fact.subject().uuid(), fact.subject().name(), fact.optedOut(), fact.at()),
                fact -> Region.entity(fact.subject()));
        registry.register(
                AnnouncerReloaded.class,
                UxmAnnouncerReloadEvent.getHandlerList(),
                fact -> new UxmAnnouncerReloadEvent(fact.lineCount(), fact.at()),
                fact -> Region.global());
    }
}
