package com.uxplima.uxmessentials.worlds.domain.event;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.worlds.domain.AccessDecision;
import com.uxplima.uxmessentials.worlds.domain.WorldName;

/** Published when access control refuses a player entry to a world; the reason is never ALLOWED. */
public record WorldEntryDenied(WorldName name, PlayerRef player, AccessDecision reason) implements WorldEvent {
    public WorldEntryDenied {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(reason, "reason");
        if (reason.allowed()) {
            throw new IllegalArgumentException("denial reason must not be ALLOWED");
        }
    }
}
