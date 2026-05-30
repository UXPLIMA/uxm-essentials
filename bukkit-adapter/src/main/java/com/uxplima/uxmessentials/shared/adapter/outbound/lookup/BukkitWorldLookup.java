package com.uxplima.uxmessentials.shared.adapter.outbound.lookup;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.World;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.WorldLookup;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link WorldLookup} implementation. Resolves a loaded world by name or persistent uid and maps
 * it to a {@link WorldRef}; a name or uid that no longer maps to a loaded world returns empty.
 */
@NullMarked
public final class BukkitWorldLookup implements WorldLookup {

    @Override
    public Optional<WorldRef> findByName(String name) {
        Objects.requireNonNull(name, "name");
        World world = Bukkit.getWorld(name);
        return world == null ? Optional.empty() : Optional.of(BukkitRefs.toRef(world));
    }

    @Override
    public Optional<WorldRef> findByUid(UUID uid) {
        Objects.requireNonNull(uid, "uid");
        World world = Bukkit.getWorld(uid);
        return world == null ? Optional.empty() : Optional.of(BukkitRefs.toRef(world));
    }
}
