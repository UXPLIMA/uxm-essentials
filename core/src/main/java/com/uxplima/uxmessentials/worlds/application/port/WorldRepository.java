package com.uxplima.uxmessentials.worlds.application.port;

import java.util.List;
import java.util.Optional;

import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldName;

/** Persistence of world metadata (the {@code world} table). DB-backed, Caffeine-cached. */
public interface WorldRepository {

    Optional<ManagedWorld> find(WorldName name);

    List<ManagedWorld> all();

    boolean exists(WorldName name);

    void save(ManagedWorld world);

    void delete(WorldName name);
}
