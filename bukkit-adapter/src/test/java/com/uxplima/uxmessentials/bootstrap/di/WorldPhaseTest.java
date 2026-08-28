package com.uxplima.uxmessentials.bootstrap.di;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import org.bukkit.Server;
import org.bukkit.World;

import org.junit.jupiter.api.Test;

/**
 * Pins the seam that pays for {@code load: STARTUP}.
 *
 * <p>The plugin enables before the worlds exist, which is what lets the default world reach
 * {@code getDefaultWorldGenerator} at all. Everything that costs is here: work handed over during a boot has to
 * wait, work handed over on a runtime re-wire has to run at once, and a task that throws must not take the rest
 * of the queue with it.
 */
class WorldPhaseTest {

    private static final Logger LOG = Logger.getLogger(WorldPhaseTest.class.getName());

    @Test
    void queuesWorkWhileTheServerHasNoWorlds() {
        WorldPhase phase = new WorldPhase(serverWithWorlds(0), LOG);
        AtomicInteger ran = new AtomicInteger();

        phase.run("first", ran::incrementAndGet);
        phase.run("second", ran::incrementAndGet);

        assertThat(ran)
                .as("a boot enables before the worlds load, so nothing may run yet")
                .hasValue(0);
        assertThat(phase.pending()).isEqualTo(2);
    }

    @Test
    void releasesQueuedWorkInOrderOnceTheWorldsAreUp() {
        WorldPhase phase = new WorldPhase(serverWithWorlds(0), LOG);
        List<String> order = new java.util.ArrayList<>();

        phase.run("first", () -> order.add("first"));
        phase.run("second", () -> order.add("second"));
        int ran = phase.runQueued();

        assertThat(ran).isEqualTo(2);
        assertThat(order).containsExactly("first", "second");
        assertThat(phase.pending())
                .as("the queue is emptied as it is taken, so a second ServerLoadEvent is a no-op")
                .isZero();
        assertThat(phase.runQueued()).isZero();
    }

    @Test
    void runsInlineWhenTheWorldsAreAlreadyThere() {
        WorldPhase phase = new WorldPhase(serverWithWorlds(3), LOG);
        AtomicInteger ran = new AtomicInteger();

        phase.run("hot reload", ran::incrementAndGet);

        assertThat(ran)
                .as("a module re-wired at runtime must not wait for an event that already fired hours ago")
                .hasValue(1);
        assertThat(phase.pending()).isZero();
    }

    @Test
    void runsInlineAfterTheWorldsHaveBeenReleased() {
        WorldPhase phase = new WorldPhase(serverWithWorlds(0), LOG);
        phase.runQueued();
        AtomicInteger ran = new AtomicInteger();

        phase.run("late", ran::incrementAndGet);

        assertThat(ran).hasValue(1);
    }

    @Test
    void oneFailingTaskDoesNotStopTheRest() {
        WorldPhase phase = new WorldPhase(serverWithWorlds(0), LOG);
        AtomicInteger ran = new AtomicInteger();

        phase.run("before", ran::incrementAndGet);
        phase.run("throws", () -> {
            throw new IllegalStateException("boom");
        });
        phase.run("after", ran::incrementAndGet);
        int count = phase.runQueued();

        assertThat(count).isEqualTo(3);
        assertThat(ran)
                .as("a throw here cannot disable the plugin the way one in onEnable does, so it must be"
                        + " isolated and logged rather than allowed to swallow the rest of the queue")
                .hasValue(2);
    }

    private static Server serverWithWorlds(int count) {
        Server server = mock(Server.class);
        when(server.getWorlds()).thenReturn(java.util.Collections.nCopies(count, mock(World.class)));
        return server;
    }
}
