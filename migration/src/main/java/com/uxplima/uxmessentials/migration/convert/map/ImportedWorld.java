package com.uxplima.uxmessentials.migration.convert.map;

import java.util.Objects;

import com.uxplima.uxmessentials.worlds.domain.ManagedWorld;
import org.jspecify.annotations.NullMarked;

/**
 * One parsed-and-mapped world, expressed as the domain {@link ManagedWorld} aggregate the worlds context owns. The
 * importer writes it through the same repository {@code /world create} and the enable-time reconciler save through,
 * so an imported world can never reach a state a normal command could not.
 *
 * <p>The world is marked adopted: its folder already exists on disk and we did not generate it. Its Bukkit uid is
 * left unknown, because an import may run while the world is unloaded; the enable-time reconciler fills it in the
 * first time the world is loaded, exactly as it does for a world adopted from the server's own list.
 *
 * @param world the mapped world aggregate
 */
@NullMarked
public record ImportedWorld(ManagedWorld world) {

    public ImportedWorld {
        Objects.requireNonNull(world, "world");
    }
}
