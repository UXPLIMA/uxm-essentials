package com.uxplima.uxmessentials.bootstrap.di;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Server;

import org.jspecify.annotations.NullMarked;

/**
 * The seam for enable-time work that needs the worlds to exist.
 *
 * <p><strong>Why this exists.</strong> The plugin declares {@code load: STARTUP} in
 * {@code paper-plugin.yml}, because that is the only way {@code getDefaultWorldGenerator} is ever
 * called for the default world: {@code CraftServer.getGenerator} refuses a generator whose plugin is
 * not enabled yet, and a {@code POSTWORLD} plugin is by definition not enabled when the default world
 * is created. Serving {@code generator: uxmEssentials:void} from {@code bukkit.yml} therefore requires
 * enabling before the worlds load, which in turn means that during {@code onLoad} and the whole of
 * {@code onEnable} the server has no worlds at all.
 *
 * <p><strong>What that costs.</strong> Wiring that reads {@code getWorlds()} sees an empty list and
 * silently does nothing; wiring that resolves a world by name gets null and logs an "unknown world"
 * that is not the operator's fault; wiring that creates a world does so before the server has made its
 * own. None of those fail loudly, which is the dangerous part. Anything in that shape hands its work
 * here instead.
 *
 * <p><strong>The rule.</strong> {@link #run} executes the task straight away when the server already
 * has worlds, and queues it for the first {@code ServerLoadEvent} when it does not. Both cases are
 * real: a normal boot queues, and a module re-wired at runtime (a hot reload, where the worlds have
 * been up for hours) runs inline. Callers do not need to know which one they are in.
 *
 * <p><strong>Failure.</strong> A throw inside a queued task cannot disable the plugin the way a throw
 * in {@code onEnable} does, because by then enable has long returned. Each task is therefore wrapped:
 * one that fails is logged with the label it was queued under and the rest still run, the same
 * isolation the command and listener registration loops use.
 *
 * <p><strong>Ownership.</strong> The queue is written on the enable thread and drained on the main
 * thread at {@code ServerLoadEvent}. Those never overlap, but they are different threads, so every
 * method that touches the queue is synchronised on this instance and the drain takes a copy before
 * running anything.
 */
@NullMarked
public final class WorldPhase {

    private final Server server;
    private final Logger log;

    /** Label plus task, kept in queue order. Guarded by {@code this}. */
    private final List<Deferred> queued = new ArrayList<>();

    /** Set once the worlds are up, so work queued afterwards runs inline. Guarded by {@code this}. */
    private boolean worldsLoaded;

    public WorldPhase(Server server, Logger log) {
        this.server = Objects.requireNonNull(server, "server");
        this.log = Objects.requireNonNull(log, "log");
    }

    /**
     * Run {@code task} once the worlds exist: now if they already do, otherwise at
     * {@code ServerLoadEvent}.
     *
     * @param what a short label naming the work, used only in the log line when it fails
     * @param task the work to run
     */
    public void run(String what, Runnable task) {
        Objects.requireNonNull(what, "what");
        Objects.requireNonNull(task, "task");
        synchronized (this) {
            if (!worldsLoaded && server.getWorlds().isEmpty()) {
                queued.add(new Deferred(what, task));
                return;
            }
            worldsLoaded = true;
        }
        execute(new Deferred(what, task));
    }

    /**
     * Drain everything queued so far, in the order it was queued. Called from the
     * {@code ServerLoadEvent} handler; safe to call again, since the queue is emptied as it is taken.
     *
     * @return how many tasks ran
     */
    public int runQueued() {
        List<Deferred> pending;
        synchronized (this) {
            worldsLoaded = true;
            if (queued.isEmpty()) {
                return 0;
            }
            pending = List.copyOf(queued);
            queued.clear();
        }
        pending.forEach(this::execute);
        return pending.size();
    }

    /** How many tasks are still waiting for the worlds. Exposed for the tests that pin the seam. */
    public synchronized int pending() {
        return queued.size();
    }

    private void execute(Deferred deferred) {
        try {
            deferred.task().run();
        } catch (RuntimeException failure) {
            log.log(Level.SEVERE, "deferred startup work failed: " + deferred.what(), failure);
        }
    }

    private record Deferred(String what, Runnable task) {}
}
