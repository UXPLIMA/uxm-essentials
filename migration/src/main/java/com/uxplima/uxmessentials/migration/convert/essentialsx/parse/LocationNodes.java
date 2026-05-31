package com.uxplima.uxmessentials.migration.convert.essentialsx.parse;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.ConfigurationNode;

/**
 * Reads the EssentialsX location shape shared by {@code userdata} homes and {@code warps} files: a
 * {@code world}/{@code world-name} key plus {@code x}/{@code y}/{@code z}/{@code yaw}/{@code pitch}.
 * EssentialsX has used both {@code world} and {@code world-name} across versions, so both are tried;
 * a node with no world resolves to {@code null} and the caller skips it (docs/12-migration §4).
 */
@NullMarked
final class LocationNodes {

    private LocationNodes() {}

    /** Read a location node, or {@code null} when the world key is absent (an unusable entry). */
    static @Nullable EssXLocation read(ConfigurationNode node) {
        String world = node.node("world-name").getString(node.node("world").getString());
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
