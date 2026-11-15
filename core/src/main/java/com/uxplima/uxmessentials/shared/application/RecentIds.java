package com.uxplima.uxmessentials.shared.application;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The ids of the cross-server frames a backend acted on lately. With {@code network.transport = both} a frame reaches
 * a peer over the proxy and over Redis, so an action a frame triggers (a line shown, a message delivered) would
 * happen twice; asking this first makes the second copy a no-op. Only the newest ids are kept: a duplicate arrives
 * within moments of the first copy, so an id that long ago dropped out is never asked about again.
 */
public final class RecentIds {

    /** Far more frames than arrive between two copies of one frame, and small enough to cost nothing. */
    private static final int CAPACITY = 4096;

    private final Map<UUID, Boolean> seen = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, Boolean> eldest) {
            return size() > CAPACITY;
        }
    };

    /** Whether {@code id} is new; it counts as seen from now on. */
    public synchronized boolean firstTime(UUID id) {
        Objects.requireNonNull(id, "id");
        return seen.put(id, Boolean.TRUE) == null;
    }
}
