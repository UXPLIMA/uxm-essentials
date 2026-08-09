package com.uxplima.uxmessentials.worlds.adapter.outbound.api;

import java.util.Objects;

import com.uxplima.uxmessentials.api.bukkit.event.world.UxmWorldAdoptEvent;
import com.uxplima.uxmessentials.api.bukkit.event.world.UxmWorldCreateEvent;
import com.uxplima.uxmessentials.api.bukkit.event.world.UxmWorldDeleteEvent;
import com.uxplima.uxmessentials.api.bukkit.event.world.UxmWorldEntryDeniedEvent;
import com.uxplima.uxmessentials.api.bukkit.event.world.UxmWorldImportEvent;
import com.uxplima.uxmessentials.api.bukkit.event.world.UxmWorldLoadEvent;
import com.uxplima.uxmessentials.api.bukkit.event.world.UxmWorldSettingChangeEvent;
import com.uxplima.uxmessentials.api.bukkit.event.world.UxmWorldUnloadEvent;
import com.uxplima.uxmessentials.api.bukkit.event.world.UxmWorldUnregisterEvent;
import com.uxplima.uxmessentials.api.view.UxmWorldAccess;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.EventBridgeRegistry;
import com.uxplima.uxmessentials.shared.adapter.outbound.api.Region;
import com.uxplima.uxmessentials.worlds.domain.AccessDecision;
import com.uxplima.uxmessentials.worlds.domain.event.WorldAdopted;
import com.uxplima.uxmessentials.worlds.domain.event.WorldCreated;
import com.uxplima.uxmessentials.worlds.domain.event.WorldDeleted;
import com.uxplima.uxmessentials.worlds.domain.event.WorldEntryDenied;
import com.uxplima.uxmessentials.worlds.domain.event.WorldImported;
import com.uxplima.uxmessentials.worlds.domain.event.WorldLoaded;
import com.uxplima.uxmessentials.worlds.domain.event.WorldSettingChanged;
import com.uxplima.uxmessentials.worlds.domain.event.WorldUnloaded;
import com.uxplima.uxmessentials.worlds.domain.event.WorldUnregistered;
import org.jspecify.annotations.NullMarked;

/**
 * Which Bukkit event each world fact becomes.
 *
 * <p>These are the plugin's only genuinely server-wide facts, so they are delivered on the global region: a world
 * loading is not about a player and belongs to no region in particular. The one exception is a refused entry, which
 * is about the player who was refused and follows them.
 */
@NullMarked
public final class WorldEventBridges {

    private WorldEventBridges() {}

    public static void register(EventBridgeRegistry registry) {
        Objects.requireNonNull(registry, "registry");
        registry.register(
                WorldCreated.class,
                UxmWorldCreateEvent.getHandlerList(),
                fact -> new UxmWorldCreateEvent(fact.name().value()),
                fact -> Region.global());
        registry.register(
                WorldAdopted.class,
                UxmWorldAdoptEvent.getHandlerList(),
                fact -> new UxmWorldAdoptEvent(fact.name().value()),
                fact -> Region.global());
        registry.register(
                WorldImported.class,
                UxmWorldImportEvent.getHandlerList(),
                fact -> new UxmWorldImportEvent(fact.name().value()),
                fact -> Region.global());
        registry.register(
                WorldLoaded.class,
                UxmWorldLoadEvent.getHandlerList(),
                fact -> new UxmWorldLoadEvent(fact.name().value()),
                fact -> Region.global());
        registry.register(
                WorldUnloaded.class,
                UxmWorldUnloadEvent.getHandlerList(),
                fact -> new UxmWorldUnloadEvent(fact.name().value()),
                fact -> Region.global());
        registry.register(
                WorldDeleted.class,
                UxmWorldDeleteEvent.getHandlerList(),
                fact -> new UxmWorldDeleteEvent(fact.name().value()),
                fact -> Region.global());
        registry.register(
                WorldUnregistered.class,
                UxmWorldUnregisterEvent.getHandlerList(),
                fact -> new UxmWorldUnregisterEvent(fact.name().value()),
                fact -> Region.global());
        registry.register(
                WorldSettingChanged.class,
                UxmWorldSettingChangeEvent.getHandlerList(),
                fact -> new UxmWorldSettingChangeEvent(fact.name().value(), fact.settingKey(), fact.settingValue()),
                fact -> Region.global());
        registry.register(
                WorldEntryDenied.class,
                UxmWorldEntryDeniedEvent.getHandlerList(),
                fact -> new UxmWorldEntryDeniedEvent(
                        fact.name().value(), fact.player().uuid(), fact.player().name(), access(fact.reason())),
                fact -> Region.entity(fact.player()));
    }

    // An exhaustive switch rather than valueOf on the name: if the domain grows a refusal reason, this stops
    // compiling until somebody decides what the published enum should call it.
    private static UxmWorldAccess access(AccessDecision decision) {
        return switch (decision) {
            case ALLOWED -> UxmWorldAccess.ALLOWED;
            case DENIED_PERMISSION -> UxmWorldAccess.DENIED_PERMISSION;
            case DENIED_FULL -> UxmWorldAccess.DENIED_FULL;
        };
    }
}
