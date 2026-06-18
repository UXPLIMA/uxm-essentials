package com.uxplima.uxmessentials.worlds.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.worlds.domain.WorldName;

/** Published after a managed world is unloaded from the server. */
public record WorldUnloaded(WorldName name) implements WorldEvent {
    public WorldUnloaded {
        Objects.requireNonNull(name, "name");
    }
}
