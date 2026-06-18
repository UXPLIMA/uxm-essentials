package com.uxplima.uxmessentials.worlds.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.worlds.domain.WorldName;

/** Published when an already-loaded server world is taken under management at enable. */
public record WorldAdopted(WorldName name) implements WorldEvent {
    public WorldAdopted {
        Objects.requireNonNull(name, "name");
    }
}
