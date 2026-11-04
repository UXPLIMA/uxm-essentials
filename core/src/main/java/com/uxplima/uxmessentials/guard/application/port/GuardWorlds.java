package com.uxplima.uxmessentials.guard.application.port;

import java.util.List;
import java.util.Optional;

/** The worlds the server has loaded and their heights, which the compiler needs to lay out sections. */
public interface GuardWorlds {

    /** The names of the loaded worlds. */
    List<String> loaded();

    /** The lowest and highest block of a loaded world. */
    Optional<Height> height(String world);

    /**
     * A world's vertical extent.
     *
     * @param minY the lowest block
     * @param maxY the highest block
     */
    record Height(int minY, int maxY) {}
}
