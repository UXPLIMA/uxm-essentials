package com.uxplima.uxmessentials.migration.adapter;

import java.util.Optional;

import org.bukkit.Server;
import org.bukkit.World;

import com.uxplima.uxmessentials.migration.convert.essentialsx.map.WorldNameResolver;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.NullMarked;

/**
 * Resolves an EssentialsX world name to a {@link WorldRef} against the live server world list. A name the
 * server does not load (a deleted or renamed world) resolves to {@link Optional#empty()}, and the mapper
 * drops that location rather than failing the record (docs/12-migration §4). This is the one Bukkit-facing
 * seam the EssentialsX mappers need; everything else in {@code convert/} stays platform-neutral.
 */
@NullMarked
public final class BukkitWorldNameResolver implements WorldNameResolver {

    private final Server server;

    public BukkitWorldNameResolver(Server server) {
        this.server = java.util.Objects.requireNonNull(server, "server");
    }

    @Override
    public Optional<WorldRef> resolve(String name) {
        java.util.Objects.requireNonNull(name, "name");
        World world = server.getWorld(name);
        return world == null ? Optional.empty() : Optional.of(new WorldRef(world.getUID(), world.getName()));
    }
}
