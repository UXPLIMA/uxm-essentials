package com.uxplima.uxmessentials.worlds.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.worlds.domain.WorldName;

/** Published after a world is created and persisted. */
public record WorldCreated(WorldName name) implements WorldEvent {
    public WorldCreated {
        Objects.requireNonNull(name, "name");
    }
}
