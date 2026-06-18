package com.uxplima.uxmessentials.worlds.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.worlds.domain.WorldName;

/** Published after an existing world folder is imported into management. */
public record WorldImported(WorldName name) implements WorldEvent {
    public WorldImported {
        Objects.requireNonNull(name, "name");
    }
}
