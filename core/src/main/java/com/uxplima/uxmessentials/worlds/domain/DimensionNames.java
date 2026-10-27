package com.uxplima.uxmessentials.worlds.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * How a dimension key and a world name map onto each other.
 *
 * <p>On Paper 26.2 every world is a dimension of the level, kept under {@code <level>/dimensions/<namespace>/<path>}
 * and addressed as {@code namespace:path}. The server still names a world: the level's three dimensions are the
 * level's name, that name with {@code _nether} and that name with {@code _the_end}, any other dimension of
 * {@code minecraft} is named by its path, and a dimension of another namespace by {@code namespace_path}. That is
 * Paper's own rule, so a name and a key the server gave reach the same world. A nested path such as
 * {@code mypack:caves/deep} is not a world name, and is refused.
 */
public final class DimensionNames {

    private static final String MINECRAFT = "minecraft";

    private DimensionNames() {}

    /** The key an operator typed, or empty when {@code raw} is a plain world name. */
    public static Optional<DimensionKey> parse(String raw) {
        Objects.requireNonNull(raw, "raw");
        if (raw.indexOf(':') < 0) {
            return Optional.empty();
        }
        if (raw.indexOf('/') >= 0) {
            return Optional.empty();
        }
        try {
            return Optional.of(DimensionKey.of(raw));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    /** The name the server gives the world of {@code key} in the level called {@code levelName}. */
    public static String worldName(DimensionKey key, String levelName) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(levelName, "levelName");
        String path = path(key);
        if (!MINECRAFT.equals(namespace(key))) {
            return namespace(key) + "_" + path;
        }
        return switch (path) {
            case "overworld" -> levelName;
            case "the_nether" -> levelName + "_nether";
            case "the_end" -> levelName + "_the_end";
            default -> path;
        };
    }

    /** The environment a dimension with this path is created in: the vanilla two by name, any other as normal. */
    public static WorldEnvironment environment(String path) {
        Objects.requireNonNull(path, "path");
        return switch (path) {
            case "the_nether" -> WorldEnvironment.NETHER;
            case "the_end" -> WorldEnvironment.THE_END;
            default -> WorldEnvironment.NORMAL;
        };
    }

    public static String namespace(DimensionKey key) {
        return key.value().substring(0, key.value().indexOf(':'));
    }

    public static String path(DimensionKey key) {
        return key.value().substring(key.value().indexOf(':') + 1);
    }
}
