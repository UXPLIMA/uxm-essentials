package com.uxplima.uxmessentials.itemworld.adapter.outbound.api;

import java.util.Objects;

import com.uxplima.uxmessentials.api.bukkit.event.itemworld.UxmEntityPurgeEvent;
import com.uxplima.uxmessentials.api.bukkit.event.itemworld.UxmMobSpawnEvent;
import com.uxplima.uxmessentials.api.view.UxmPurgeCategory;
import com.uxplima.uxmessentials.api.view.UxmPurgeScope;
import com.uxplima.uxmessentials.itemworld.domain.PurgeSelection;
import com.uxplima.uxmessentials.itemworld.domain.event.EntitiesPurged;
import com.uxplima.uxmessentials.itemworld.domain.event.MobsSpawned;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.EventBridgeRegistry;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.Region;
import org.jspecify.annotations.NullMarked;

/**
 * Which Bukkit event each itemworld fact becomes.
 *
 * <p>Both follow the staff member who ran the command rather than the world they acted on: the world may be huge and
 * a purge has no one position, while the actor has exactly one.
 */
@NullMarked
public final class ItemWorldEventBridges {

    private ItemWorldEventBridges() {}

    public static void register(EventBridgeRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        registry.register(
                MobsSpawned.class,
                UxmMobSpawnEvent.getHandlerList(),
                fact -> new UxmMobSpawnEvent(
                        fact.actor().uuid(),
                        fact.actor().name(),
                        fact.world().name(),
                        fact.spec().typeId(),
                        fact.spec().amount(),
                        fact.spawned(),
                        fact.at()),
                fact -> Region.entity(fact.actor()));
        registry.register(
                EntitiesPurged.class,
                UxmEntityPurgeEvent.getHandlerList(),
                fact -> new UxmEntityPurgeEvent(
                        fact.actor().uuid(),
                        fact.actor().name(),
                        fact.world().name(),
                        scope(fact.selection().scope()),
                        category(fact.selection().category()),
                        fact.selection().radius(),
                        fact.selection().typeId(),
                        fact.removed(),
                        fact.at()),
                fact -> Region.entity(fact.actor()));
    }

    private static UxmPurgeScope scope(PurgeSelection.Scope scope) {
        return switch (scope) {
            case RADIUS -> UxmPurgeScope.RADIUS;
            case WORLD -> UxmPurgeScope.WORLD;
        };
    }

    private static UxmPurgeCategory category(PurgeSelection.Category category) {
        return switch (category) {
            case MONSTERS -> UxmPurgeCategory.MONSTERS;
            case NAMED_TYPE -> UxmPurgeCategory.NAMED_TYPE;
            case ALL_ENTITIES -> UxmPurgeCategory.ALL_ENTITIES;
        };
    }
}
