package com.uxplima.uxmessentials.homes.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.homes.application.port.HomeInviteRepository;
import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeSet;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/** Homes and invites kept in maps, standing in for the database. */
final class InMemoryHomes implements HomeRepository, HomeInviteRepository {

    private final Map<String, Home> homes = new HashMap<>();
    private final Map<String, Set<UUID>> invites = new HashMap<>();

    private static String key(PlayerRef owner, HomeSlot slot) {
        return owner.uuid() + "#" + slot.index();
    }

    @Override
    public HomeSet load(PlayerRef owner) {
        List<Home> owned = new ArrayList<>();
        for (Home home : homes.values()) {
            if (home.owner().equals(owner)) {
                owned.add(home);
            }
        }
        return HomeSet.of(owner, owned);
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
        homes.values().removeIf(home -> home.owner().equals(owner));
    }

    @Override
    public Set<UUID> invites(PlayerRef owner, HomeSlot slot) {
        return Set.copyOf(invites.getOrDefault(key(owner, slot), Set.of()));
    }

    @Override
    public void addInvite(PlayerRef owner, HomeSlot slot, UUID invited) {
        invites.computeIfAbsent(key(owner, slot), ignored -> new HashSet<>()).add(invited);
    }

    @Override
    public void removeInvite(PlayerRef owner, HomeSlot slot, UUID invited) {
        invites.getOrDefault(key(owner, slot), new HashSet<>()).remove(invited);
    }

    @Override
    public void removeAll(PlayerRef owner, HomeSlot slot) {
        invites.remove(key(owner, slot));
    }

    @Override
    public void removeAllForOwner(PlayerRef owner) {
        invites.keySet().removeIf(key -> key.startsWith(owner.uuid() + "#"));
    }
}
