package com.uxplima.uxmessentials.homes.adapter.outbound.api;

import java.util.Objects;

import com.uxplima.uxmessentials.api.bukkit.event.home.UxmHomeCreateEvent;
import com.uxplima.uxmessentials.api.bukkit.event.home.UxmHomeDeleteEvent;
import com.uxplima.uxmessentials.api.bukkit.event.home.UxmHomeIconChangeEvent;
import com.uxplima.uxmessentials.api.bukkit.event.home.UxmHomeLimitReachedEvent;
import com.uxplima.uxmessentials.api.bukkit.event.home.UxmHomeRelocateEvent;
import com.uxplima.uxmessentials.api.bukkit.event.home.UxmHomeRenameEvent;
import com.uxplima.uxmessentials.api.bukkit.event.home.UxmHomeVisibilityChangeEvent;
import com.uxplima.uxmessentials.homes.domain.event.HomeCreated;
import com.uxplima.uxmessentials.homes.domain.event.HomeDeleted;
import com.uxplima.uxmessentials.homes.domain.event.HomeIconChanged;
import com.uxplima.uxmessentials.homes.domain.event.HomeLimitReached;
import com.uxplima.uxmessentials.homes.domain.event.HomeRelocated;
import com.uxplima.uxmessentials.homes.domain.event.HomeRenamed;
import com.uxplima.uxmessentials.homes.domain.event.HomeVisibilityChanged;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.ApiValues;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.EventBridgeRegistry;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.Region;
import org.jspecify.annotations.NullMarked;

/**
 * Which Bukkit event each home fact becomes.
 *
 * <p>Every one is delivered on the owner's own region: a home is always about one player, and a listener reacting to
 * a home change almost always wants to act on that player. The owner is offline for none of these in practice, but
 * the entity scheduler no-ops safely if they log out between the change and the delivery.
 */
@NullMarked
public final class HomeEventBridges {

    private HomeEventBridges() {}

    public static void register(EventBridgeRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        registry.register(
                HomeCreated.class,
                UxmHomeCreateEvent.getHandlerList(),
                fact -> new UxmHomeCreateEvent(
                        fact.owner().uuid(),
                        fact.owner().name(),
                        fact.slot().index(),
                        ApiValues.location(fact.location())),
                fact -> Region.entity(fact.owner()));
        registry.register(
                HomeDeleted.class,
                UxmHomeDeleteEvent.getHandlerList(),
                fact -> new UxmHomeDeleteEvent(
                        fact.owner().uuid(), fact.owner().name(), fact.slot().index()),
                fact -> Region.entity(fact.owner()));
        registry.register(
                HomeRelocated.class,
                UxmHomeRelocateEvent.getHandlerList(),
                fact -> new UxmHomeRelocateEvent(
                        fact.owner().uuid(), fact.owner().name(), fact.slot().index()),
                fact -> Region.entity(fact.owner()));
        registry.register(
                HomeRenamed.class,
                UxmHomeRenameEvent.getHandlerList(),
                fact -> new UxmHomeRenameEvent(
                        fact.owner().uuid(), fact.owner().name(), fact.slot().index()),
                fact -> Region.entity(fact.owner()));
        registry.register(
                HomeIconChanged.class,
                UxmHomeIconChangeEvent.getHandlerList(),
                fact -> new UxmHomeIconChangeEvent(
                        fact.owner().uuid(), fact.owner().name(), fact.slot().index()),
                fact -> Region.entity(fact.owner()));
        registry.register(
                HomeVisibilityChanged.class,
                UxmHomeVisibilityChangeEvent.getHandlerList(),
                fact -> new UxmHomeVisibilityChangeEvent(
                        fact.owner().uuid(), fact.owner().name(), fact.slot().index()),
                fact -> Region.entity(fact.owner()));
        registry.register(
                HomeLimitReached.class,
                UxmHomeLimitReachedEvent.getHandlerList(),
                fact -> new UxmHomeLimitReachedEvent(
                        fact.owner().uuid(), fact.owner().name(), fact.currentCount(), fact.limit()),
                fact -> Region.entity(fact.owner()));
    }
}
