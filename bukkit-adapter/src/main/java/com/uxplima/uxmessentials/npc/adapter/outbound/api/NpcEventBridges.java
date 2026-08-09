package com.uxplima.uxmessentials.npc.adapter.outbound.api;

import java.util.Objects;

import com.uxplima.uxmessentials.api.bukkit.event.npc.UxmNpcCreateEvent;
import com.uxplima.uxmessentials.api.bukkit.event.npc.UxmNpcDeleteEvent;
import com.uxplima.uxmessentials.api.bukkit.event.npc.UxmNpcMoveEvent;
import com.uxplima.uxmessentials.npc.domain.event.NpcCreated;
import com.uxplima.uxmessentials.npc.domain.event.NpcDeleted;
import com.uxplima.uxmessentials.npc.domain.event.NpcMoved;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.ApiValues;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.EventBridgeRegistry;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.Region;
import org.jspecify.annotations.NullMarked;

/**
 * Which Bukkit event each NPC fact becomes.
 *
 * <p>A create and a move both name a position, so they are delivered where the NPC is, which is the region a listener
 * needs to be on to touch the entity. A delete leaves nothing standing, so it follows the staff member instead.
 */
@NullMarked
public final class NpcEventBridges {

    private NpcEventBridges() {}

    public static void register(EventBridgeRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        registry.register(
                NpcCreated.class,
                UxmNpcCreateEvent.getHandlerList(),
                fact -> new UxmNpcCreateEvent(
                        fact.name().value(),
                        fact.creator().uuid(),
                        fact.creator().name(),
                        ApiValues.location(fact.location())),
                fact -> Region.at(fact.location()));
        registry.register(
                NpcDeleted.class,
                UxmNpcDeleteEvent.getHandlerList(),
                fact -> new UxmNpcDeleteEvent(
                        fact.name().value(),
                        fact.removedBy().uuid(),
                        fact.removedBy().name()),
                fact -> Region.entity(fact.removedBy()));
        registry.register(
                NpcMoved.class,
                UxmNpcMoveEvent.getHandlerList(),
                fact -> new UxmNpcMoveEvent(fact.name().value(), ApiValues.location(fact.to())),
                fact -> Region.at(fact.to()));
    }
}
