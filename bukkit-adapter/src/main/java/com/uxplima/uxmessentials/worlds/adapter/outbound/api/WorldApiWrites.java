package com.uxplima.uxmessentials.worlds.adapter.outbound.api;

import java.util.Objects;

import com.uxplima.uxmessentials.worlds.adapter.WorldsServices;
import com.uxplima.uxmessentials.worlds.application.LoadWorld;
import com.uxplima.uxmessentials.worlds.application.UnloadWorld;
import org.jspecify.annotations.NullMarked;

/**
 * The two world use cases the published API runs, out of the two dozen the module assembles.
 *
 * <p>Creating, deleting, importing and pre-generating are all deliberately absent: they write to disk on a scale
 * an API call should not start without somebody watching. Loading and unloading are reversible and cheap, which
 * is what makes them worth publishing.
 *
 * @param load {@code /world load}
 * @param unload {@code /world unload}
 */
@NullMarked
public record WorldApiWrites(LoadWorld load, UnloadWorld unload) {

    public WorldApiWrites {
        Objects.requireNonNull(load, "load");
        Objects.requireNonNull(unload, "unload");
    }

    /** The two as the module built them. */
    public static WorldApiWrites of(WorldsServices services) {
        Objects.requireNonNull(services, "services");
        return new WorldApiWrites(services.loadWorld(), services.unloadWorld());
    }
}
