package com.uxplima.uxmessentials.worlds.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldName;

/** The world register held in memory. */
final class InMemoryWorlds implements WorldRepository {

    private final Map<WorldName, ManagedWorld> rows = new LinkedHashMap<>();

    @Override
    public Optional<ManagedWorld> find(WorldName name) {
        return Optional.ofNullable(rows.get(name));
    }

    @Override
    public List<ManagedWorld> all() {
        return List.copyOf(rows.values());
    }

    @Override
    public boolean exists(WorldName name) {
        return rows.containsKey(name);
    }

    @Override
    public void save(ManagedWorld world) {
        rows.put(world.name(), world);
    }

    @Override
    public void delete(WorldName name) {
        rows.remove(name);
    }
}
