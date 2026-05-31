package com.uxplima.uxmessentials.migration.convert.essentialsx.parse;

import java.util.Objects;

import org.jspecify.annotations.NullMarked;

/**
 * One parsed EssentialsX {@code warps/<name>.yml} file: the warp name and the raw {@link EssXLocation}
 * it points at. Source-shaped foreign detail; the mapper turns it into a domain {@code Warp}.
 *
 * @param name the warp name as the file declares it
 * @param location the raw destination
 */
@NullMarked
public record EssXWarp(String name, EssXLocation location) {

    public EssXWarp {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(location, "location");
    }
}
