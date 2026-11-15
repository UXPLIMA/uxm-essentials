package com.uxplima.uxmessentials.migration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeSet;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/** Homes kept in a map, standing in for the database. */
final class InMemoryHomes implements HomeRepository {

    private final Map<String, Home> homes = new LinkedHashMap<>();

    private static String key(PlayerRef owner, HomeSlot slot) {
        return owner.uuid() + "#" + slot.index();
    }

    List<Home> all() {
        return List.copyOf(homes.values());
    }

    @Override
    public HomeSet load(PlayerRef owner) {
        return HomeSet.of(
                owner,
                homes.values().stream()
                        .filter(home -> home.owner().uuid().equals(owner.uuid()))
                        .toList());
    }

    @Override
    public int count(PlayerRef owner) {
        return load(owner).count();
    }

    @Override
    public Optional<Home> findSlot(PlayerRef owner, HomeSlot slot) {
        return Optional.ofNullable(homes.get(key(owner, slot)));
    }

    @Override
    public void save(Home home) {
        homes.put(key(home.owner(), home.slot()), home);
    }

    @Override
    public void deleteSlot(PlayerRef owner, HomeSlot slot) {
        homes.remove(key(owner, slot));
    }

    @Override
    public void deleteAll(PlayerRef owner) {
        homes.values().removeIf(home -> home.owner().uuid().equals(owner.uuid()));
    }
}
