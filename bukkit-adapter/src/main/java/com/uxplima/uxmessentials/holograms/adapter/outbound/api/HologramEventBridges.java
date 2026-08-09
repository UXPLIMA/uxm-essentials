package com.uxplima.uxmessentials.holograms.adapter.outbound.api;

import java.util.Objects;

import com.uxplima.uxmessentials.api.bukkit.event.hologram.UxmHologramCreateEvent;
import com.uxplima.uxmessentials.api.bukkit.event.hologram.UxmHologramDeleteEvent;
import com.uxplima.uxmessentials.holograms.domain.event.HologramCreated;
import com.uxplima.uxmessentials.holograms.domain.event.HologramDeleted;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.ApiValues;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.EventBridgeRegistry;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.Region;
import org.jspecify.annotations.NullMarked;

/**
 * Which Bukkit event each hologram fact becomes.
 *
 * <p>A create goes to the hologram's own region, since that is where the display now stands and where a listener
 * would act. A delete goes to the staff member who removed it.
 */
@NullMarked
public final class HologramEventBridges {

    private HologramEventBridges() {}

    public static void register(EventBridgeRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        registry.register(
                HologramCreated.class,
                UxmHologramCreateEvent.getHandlerList(),
                fact -> new UxmHologramCreateEvent(
                        fact.creator().uuid(),
                        fact.creator().name(),
                        fact.name().value(),
                        ApiValues.location(fact.location())),
                fact -> Region.at(fact.location()));
        registry.register(
                HologramDeleted.class,
                UxmHologramDeleteEvent.getHandlerList(),
                fact -> new UxmHologramDeleteEvent(
                        fact.removedBy().uuid(),
                        fact.removedBy().name(),
                        fact.name().value()),
                fact -> Region.entity(fact.removedBy()));
    }
}
