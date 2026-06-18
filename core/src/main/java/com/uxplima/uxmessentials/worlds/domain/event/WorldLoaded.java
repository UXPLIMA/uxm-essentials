package com.uxplima.uxmessentials.worlds.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.worlds.domain.WorldName;

/** Published after a managed world is loaded into the server. */
public record WorldLoaded(WorldName name) implements WorldEvent {
    public WorldLoaded {
        Objects.requireNonNull(name, "name");
    }
}
