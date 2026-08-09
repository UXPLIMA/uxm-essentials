package com.uxplima.uxmessentials.playerwarps.adapter.outbound.api;

import java.util.Objects;

import com.uxplima.uxmessentials.api.bukkit.event.playerwarp.UxmPlayerWarpCreateEvent;
import com.uxplima.uxmessentials.api.bukkit.event.playerwarp.UxmPlayerWarpDeleteEvent;
import com.uxplima.uxmessentials.playerwarps.domain.event.PlayerWarpCreated;
import com.uxplima.uxmessentials.playerwarps.domain.event.PlayerWarpDeleted;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.ApiValues;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.EventBridgeRegistry;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.Region;
import org.jspecify.annotations.NullMarked;

/** Which Bukkit event each player-warp fact becomes. Both are delivered on the owner's own region. */
@NullMarked
public final class PlayerWarpEventBridges {

    private PlayerWarpEventBridges() {}

    public static void register(EventBridgeRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        registry.register(
                PlayerWarpCreated.class,
                UxmPlayerWarpCreateEvent.getHandlerList(),
                fact -> new UxmPlayerWarpCreateEvent(
                        fact.owner().uuid(),
                        fact.owner().name(),
                        fact.name().value(),
                        ApiValues.location(fact.location())),
                fact -> Region.entity(fact.owner()));
        registry.register(
                PlayerWarpDeleted.class,
                UxmPlayerWarpDeleteEvent.getHandlerList(),
                fact -> new UxmPlayerWarpDeleteEvent(
                        fact.owner().uuid(), fact.owner().name(), fact.name().value()),
                fact -> Region.entity(fact.owner()));
    }
}
