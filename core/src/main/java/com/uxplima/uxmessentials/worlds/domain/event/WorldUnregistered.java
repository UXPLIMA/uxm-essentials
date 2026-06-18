package com.uxplima.uxmessentials.worlds.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.worlds.domain.WorldName;

/** Published when a world is dropped from the registry but its files are kept. */
public record WorldUnregistered(WorldName name) implements WorldEvent {
    public WorldUnregistered {
        Objects.requireNonNull(name, "name");
    }
}
