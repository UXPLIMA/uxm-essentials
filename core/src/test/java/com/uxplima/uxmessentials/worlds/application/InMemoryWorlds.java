package com.uxplima.uxmessentials.worlds.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.uxplima.uxmessentials.worlds.application.port.WorldRepository;
import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import com.uxplima.uxmessentials.worlds.domain.NetworkWorld;
import com.uxplima.uxmessentials.worlds.domain.WorldName;

/** The world register held in memory, as one backend of a network sees the table every backend shares. */
final class InMemoryWorlds implements WorldRepository {

    private final Table table;
    private final String server;

    /** A backend alone on its own table. */
    InMemoryWorlds() {
        this(new Table(), "here");
    }

    /** The backend named {@code server} on {@code table}. */
    InMemoryWorlds(Table table, String server) {
        this.table = table;
        this.server = server;
    }

    private Map<WorldName, ManagedWorld> mine() {
        return table.rows.computeIfAbsent(server, ignored -> new LinkedHashMap<>());
    }

    @Override
    public Optional<ManagedWorld> find(WorldName name) {
        return Optional.ofNullable(mine().get(name));
    }

    @Override
    public List<ManagedWorld> all() {
        return List.copyOf(mine().values());
    }

    @Override
    public boolean exists(WorldName name) {
        return mine().containsKey(name);
    }

    @Override
    public void save(ManagedWorld world) {
        mine().put(world.name(), world);
    }

    @Override
    public void delete(WorldName name) {
        mine().remove(name);
    }

    @Override
    public List<NetworkWorld> network() {
        List<NetworkWorld> worlds = new ArrayList<>();
        table.rows.forEach((owner, rows) -> rows.values().forEach(world -> worlds.add(new NetworkWorld(owner, world))));
        return worlds;
    }

    /** The table every backend shares, keyed by the server that runs each world. */
    static final class Table {

        private final Map<String, Map<WorldName, ManagedWorld>> rows = new LinkedHashMap<>();

        /** Write a row as {@code server} would, or as the migration left it under {@link NetworkWorld#UNSCOPED}. */
        void put(String server, ManagedWorld world) {
            rows.computeIfAbsent(server, ignored -> new LinkedHashMap<>()).put(world.name(), world);
        }

        Optional<ManagedWorld> find(String server, WorldName name) {
            return Optional.ofNullable(rows.getOrDefault(server, Map.of()).get(name));
        }
    }
}
