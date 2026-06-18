package com.uxplima.uxmessentials.worlds.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.worlds.domain.WorldName;

/** Published after a world is unregistered and its files permanently deleted. */
public record WorldDeleted(WorldName name) implements WorldEvent {
    public WorldDeleted {
        Objects.requireNonNull(name, "name");
    }
}
