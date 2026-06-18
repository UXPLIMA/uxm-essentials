package com.uxplima.uxmessentials.worlds.domain.event;

import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.worlds.domain.WorldName;

/** Sealed family of worlds-context domain events; bridged to Bukkit events only in the adapter. */
public sealed interface WorldEvent extends DomainEvent
        permits WorldCreated, WorldImported, WorldAdopted, WorldLoaded, WorldUnloaded, WorldUnregistered, WorldDeleted {

    WorldName name();
}
