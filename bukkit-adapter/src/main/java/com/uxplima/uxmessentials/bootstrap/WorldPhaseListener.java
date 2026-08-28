package com.uxplima.uxmessentials.bootstrap;

import java.util.Objects;
import java.util.logging.Logger;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;

import com.uxplima.uxmessentials.bootstrap.di.WorldPhase;
import org.jspecify.annotations.NullMarked;

/**
 * Releases the enable-time work that was waiting for the worlds.
 *
 * <p>{@code ServerLoadEvent} is the first moment at which every world named in {@code bukkit.yml} and
 * {@code server.properties} exists, which is what the queued tasks were waiting for. It fires once on boot and
 * again after a {@code /reload}; draining on both is correct, because a reload re-runs enable and therefore
 * refills the queue from scratch.
 *
 * <p>Registered at monitor priority so anything else listening for the same event, ours or another plugin's,
 * has already had its turn: the queued work adopts and spawns things into worlds, and it should see the server
 * as the rest of the startup left it.
 */
@NullMarked
public final class WorldPhaseListener implements Listener {

    private final WorldPhase phase;
    private final Logger log;

    public WorldPhaseListener(WorldPhase phase, Logger log) {
        this.phase = Objects.requireNonNull(phase, "phase");
        this.log = Objects.requireNonNull(log, "log");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerLoad(ServerLoadEvent event) {
        int ran = phase.runQueued();
        if (ran > 0) {
            log.info("Worlds are up; ran " + ran + " deferred startup task" + (ran == 1 ? "" : "s") + ".");
        }
    }
}
