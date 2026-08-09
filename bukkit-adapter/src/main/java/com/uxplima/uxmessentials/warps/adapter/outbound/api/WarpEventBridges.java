package com.uxplima.uxmessentials.warps.adapter.outbound.api;

import java.util.Objects;

import com.uxplima.uxmessentials.api.bukkit.event.warp.UxmWarpCreateEvent;
import com.uxplima.uxmessentials.api.bukkit.event.warp.UxmWarpDeleteEvent;
import com.uxplima.uxmessentials.api.bukkit.event.warp.UxmWarpPreCreateEvent;
import com.uxplima.uxmessentials.api.bukkit.event.warp.UxmWarpPreDeleteEvent;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.ApiValues;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.EventBridgeRegistry;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.Region;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.VetoRegistry;
import com.uxplima.uxmessentials.warps.domain.event.WarpCreated;
import com.uxplima.uxmessentials.warps.domain.event.WarpCreating;
import com.uxplima.uxmessentials.warps.domain.event.WarpDeleted;
import com.uxplima.uxmessentials.warps.domain.event.WarpDeleting;
import org.jspecify.annotations.NullMarked;

/**
 * Which Bukkit event each server-warp fact becomes.
 *
 * <p>A create is delivered on the warp's own region, because a listener reacting to a new warp usually wants to look
 * at where it points. A delete has no position left to speak of, so it goes to the region of whoever removed it.
 */
@NullMarked
public final class WarpEventBridges {

    private WarpEventBridges() {}

    public static void register(EventBridgeRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        registry.register(
                WarpCreated.class,
                UxmWarpCreateEvent.getHandlerList(),
                fact -> new UxmWarpCreateEvent(
                        fact.owner().uuid(),
                        fact.owner().name(),
                        fact.name().value(),
                        ApiValues.location(fact.location())),
                fact -> Region.at(fact.location()));
        registry.register(
                WarpDeleted.class,
                UxmWarpDeleteEvent.getHandlerList(),
                fact -> new UxmWarpDeleteEvent(
                        fact.removedBy().uuid(),
                        fact.removedBy().name(),
                        fact.name().value()),
                fact -> Region.entity(fact.removedBy()));
    }

    /** The two warp actions worth refusing: bringing one into existence, and taking it away. */
    public static void registerVetoes(VetoRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        registry.register(
                WarpCreating.class,
                UxmWarpPreCreateEvent.getHandlerList(),
                proposal -> new UxmWarpPreCreateEvent(
                        proposal.owner().uuid(),
                        proposal.owner().name(),
                        proposal.name().value(),
                        ApiValues.location(proposal.location())));
        registry.register(
                WarpDeleting.class,
                UxmWarpPreDeleteEvent.getHandlerList(),
                proposal -> new UxmWarpPreDeleteEvent(
                        proposal.actor().uuid(),
                        proposal.actor().name(),
                        proposal.name().value()));
    }
}
