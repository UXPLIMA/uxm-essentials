package com.uxplima.uxmessentials.worlds.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldName;

final class FakeWorldRepository implements WorldRepository {
    final Map<String, ManagedWorld> store = new LinkedHashMap<>();

    @Override
    public Optional<ManagedWorld> find(WorldName name) {
        return Optional.ofNullable(store.get(name.value()));
    }

    @Override
    public List<ManagedWorld> all() {
        return new ArrayList<>(store.values());
    }

    @Override
    public boolean exists(WorldName name) {
        return store.containsKey(name.value());
    }

    @Override
    public void save(ManagedWorld world) {
        store.put(world.name().value(), world);
    }

    @Override
    public void delete(WorldName name) {
        store.remove(name.value());
    }
}
