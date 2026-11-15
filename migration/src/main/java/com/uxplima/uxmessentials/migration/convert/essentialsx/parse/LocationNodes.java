package com.uxplima.uxmessentials.migration.convert.essentialsx.parse;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.ConfigurationNode;

/**
 * Reads the EssentialsX location shape shared by {@code userdata} homes, {@code warps} files and jail definitions: a
 * {@code world}/{@code world-name} key plus {@code x}/{@code y}/{@code z}/{@code yaw}/{@code pitch}. {@code world-name}
 * always holds the world's name, while {@code world} holds the name only on old files: modern EssentialsX writes the
 * world's uuid there and the name under {@code world-name}. The importer resolves worlds by name, so
 * {@code world-name} wins and {@code world} is read only when it is missing. A node with neither resolves to
 * {@code null} and the caller skips it.
 */
@NullMarked
final class LocationNodes {

    private LocationNodes() {}

    /** Read a location node, or {@code null} when the world key is absent (an unusable entry). */
    static @Nullable EssXLocation read(ConfigurationNode node) {
        String world = node.node("world-name").getString();
        if (world == null || world.isBlank()) {
            // Read separately: Configurate rejects a null default, so a node with only one of the two keys must not
            // pass the missing one's null as the other's default.
            world = node.node("world").getString();
        }
        if (world == null || world.isBlank()) {
            return null;
        }
        return new EssXLocation(
                world,
                node.node("x").getDouble(),
                node.node("y").getDouble(),
                node.node("z").getDouble(),
                (float) node.node("yaw").getDouble(),
                (float) node.node("pitch").getDouble());
    }
}
