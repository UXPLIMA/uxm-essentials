package com.uxplima.uxmessentials.shared.query;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * The two doubles every published query test needs: something to move the read off the calling thread, and
 * something to put a name to a UUID.
 *
 * <p>Shared rather than copied per context, because every query implementation takes the same two ports and a
 * per-test copy of each would be the same forty lines written a dozen times.
 */
public final class QueryDoubles {

    private QueryDoubles() {}

    /**
     * Runs the read straight away, on the calling thread, and counts that it was asked to move it off one.
     *
     * <p>Every tick-thread method throws: a published query has no business scheduling one, and a test that would
     * otherwise pass quietly should say so.
     */
    public static final class InlineScheduler implements Scheduler {

        private int asyncCalls;

        /** How many reads were handed to the worker pool, which is one per query call. */
        public int asyncCalls() {
            return asyncCalls;
        }

        @Override
        public void onGlobal(Runnable task) {
            throw new AssertionError("a published query must not schedule tick-thread work");
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            throw new AssertionError("a published query must not schedule tick-thread work");
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            throw new AssertionError("a published query must not schedule tick-thread work");
        }

        @Override
        public void async(Runnable task) {
            asyncCalls++;
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            throw new AssertionError("a published query must not delay its read");
        }
    }

    /** Names the players it was told about and nobody else, so the unknown-player path is exercised too. */
    public static final class MapLookup implements PlayerLookup {

        private final Map<UUID, PlayerRef> known = new HashMap<>();

        public MapLookup with(PlayerRef player) {
            known.put(player.uuid(), player);
            return this;
        }

        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            return known.values().stream()
                    .filter(player -> player.name().equals(name))
                    .findFirst();
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            return Optional.ofNullable(known.get(uuid));
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return known.containsKey(uuid);
        }
    }
}
