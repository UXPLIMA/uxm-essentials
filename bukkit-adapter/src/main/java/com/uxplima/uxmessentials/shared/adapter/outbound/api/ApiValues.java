package com.uxplima.uxmessentials.shared.adapter.outbound.api;

import java.util.UUID;

import com.uxplima.uxmessentials.api.view.UxmLocation;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.WorldLookup;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.NullMarked;

/**
 * The kernel value types as the published API sees them.
 *
 * <p>One place for every such conversion, so a change to a kernel type is a change to one method rather than to
 * however many bridge classes happened to inline it. Events convert one way only, since a fact has already been
 * recorded and there is nothing to hand back; a query converts the other way too, because the consumer names the
 * player it is asking about.
 */
@NullMarked
public final class ApiValues {

    private ApiValues() {}

    /**
     * The player a query is about, named where we can name them.
     *
     * <p>A consumer asks by UUID, which is the only identifier that survives a name change or an offline-mode
     * server. Repositories and permission lookups key on the UUID alone, so a player nobody has a name for is still
     * answered correctly; the UUID stands in as the name so the value is never half-built.
     */
    public static PlayerRef subject(PlayerLookup lookup, UUID playerId) {
        return lookup.findByUuid(playerId).orElseGet(() -> new PlayerRef(playerId, playerId.toString()));
    }

    /**
     * An API location as a kernel position, or empty when no loaded world has that name.
     *
     * <p>A consumer names a world; the plugin needs its identity, which only a loaded world has. Empty is the
     * honest answer for a world that is unloaded or misspelled, and every action that takes a location turns it
     * into a failure the caller can read rather than an exception.
     */
    public static java.util.Optional<Position> position(WorldLookup worlds, UxmLocation location) {
        java.util.Objects.requireNonNull(worlds, "worlds");
        java.util.Objects.requireNonNull(location, "location");
        return worlds.findByName(location.world())
                .map(world -> new Position(
                        world, location.x(), location.y(), location.z(), location.yaw(), location.pitch()));
    }

    /** A kernel position as an API location: the world by name, since the world may not be loaded. */
    public static UxmLocation location(Position position) {
        return new UxmLocation(
                position.world().name(), position.x(), position.y(), position.z(), position.yaw(), position.pitch());
    }
}
