package com.uxplima.uxmessentials.worlds.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A world the plugin manages: its stable {@link WorldName} identity, immutable creation
 * {@link WorldSpec}, and the mutable management facets A owns (alias, auto-load, the adopted flag,
 * and the Bukkit uid once known). Immutable; mutations return a new instance. Per-world settings and
 * gamerules are added by sub-project B.
 */
public record ManagedWorld(
        WorldName name,
        WorldSpec spec,
        Optional<String> alias,
        boolean autoLoad,
        boolean adopted,
        Optional<UUID> knownUid,
        Instant createdAt,
        Optional<UUID> createdBy) {

    public ManagedWorld {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(alias, "alias");
        Objects.requireNonNull(knownUid, "knownUid");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(createdBy, "createdBy");
    }

    /** A world freshly created by the plugin: not yet loaded (no uid), not adopted. */
    public static ManagedWorld created(
            WorldName name, WorldSpec spec, boolean autoLoad, Optional<UUID> creator, Instant createdAt) {
        return new ManagedWorld(name, spec, Optional.empty(), autoLoad, false, Optional.empty(), createdAt, creator);
    }

    /** A world discovered already-loaded at enable time and taken under management. */
    public static ManagedWorld adopted(WorldName name, WorldSpec spec, UUID uid, Instant createdAt) {
        return new ManagedWorld(
                name,
                spec,
                Optional.empty(),
                true,
                true,
                Optional.of(Objects.requireNonNull(uid, "uid")),
                createdAt,
                Optional.empty());
    }

    public ManagedWorld withAlias(Optional<String> newAlias) {
        return new ManagedWorld(name, spec, newAlias, autoLoad, adopted, knownUid, createdAt, createdBy);
    }

    public ManagedWorld withAutoLoad(boolean newAutoLoad) {
        return new ManagedWorld(name, spec, alias, newAutoLoad, adopted, knownUid, createdAt, createdBy);
    }

    public ManagedWorld withKnownUid(UUID uid) {
        return new ManagedWorld(
                name,
                spec,
                alias,
                autoLoad,
                adopted,
                Optional.of(Objects.requireNonNull(uid, "uid")),
                createdAt,
                createdBy);
    }
}
