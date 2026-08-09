package com.uxplima.uxmessentials.homes.adapter.outbound.api;

import java.util.Objects;

import com.uxplima.uxmessentials.api.bukkit.event.home.UxmHomePreCreateEvent;
import com.uxplima.uxmessentials.api.bukkit.event.home.UxmHomePreDeleteEvent;
import com.uxplima.uxmessentials.api.bukkit.event.home.UxmHomePreRelocateEvent;
import com.uxplima.uxmessentials.homes.domain.event.HomeCreating;
import com.uxplima.uxmessentials.homes.domain.event.HomeDeleting;
import com.uxplima.uxmessentials.homes.domain.event.HomeRelocating;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.ApiValues;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.VetoRegistry;
import org.jspecify.annotations.NullMarked;

/**
 * Which pre-event each vetoable home operation asks.
 *
 * <p>Three of them, and the three the homes context can undo for free: creating, deleting and moving a home. Renaming
 * and re-iconing are cosmetic and have no pre-event on purpose, so a listener is never asked a question whose answer
 * nobody would act on.
 */
@NullMarked
public final class HomeVetoBridges {

    private HomeVetoBridges() {}

    public static void register(VetoRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        registry.register(
                HomeCreating.class,
                UxmHomePreCreateEvent.getHandlerList(),
                proposal -> new UxmHomePreCreateEvent(
                        proposal.owner().uuid(),
                        proposal.owner().name(),
                        proposal.slot().index(),
                        ApiValues.location(proposal.location())));
        registry.register(
                HomeDeleting.class,
                UxmHomePreDeleteEvent.getHandlerList(),
                proposal -> new UxmHomePreDeleteEvent(
                        proposal.owner().uuid(),
                        proposal.owner().name(),
                        proposal.slot().index()));
        registry.register(
                HomeRelocating.class,
                UxmHomePreRelocateEvent.getHandlerList(),
                proposal -> new UxmHomePreRelocateEvent(
                        proposal.owner().uuid(),
                        proposal.owner().name(),
                        proposal.slot().index(),
                        ApiValues.location(proposal.location())));
    }
}
