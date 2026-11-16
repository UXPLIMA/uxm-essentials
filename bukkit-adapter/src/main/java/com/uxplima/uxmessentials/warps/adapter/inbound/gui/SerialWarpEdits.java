package com.uxplima.uxmessentials.warps.adapter.inbound.gui;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * Runs the editor's saves on the async lane, one warp's edits one after another (the category menus queue their
 * writes through it too, one category at a time).
 *
 * <p>A save writes the database, so it cannot run on the click's thread. Once it leaves that thread, two quick clicks
 * on the same warp (a lock toggled twice) could otherwise both load the warp before either has saved, and the second
 * would write over the first. Edits of different warps do not wait for each other. A failing edit still lets the next
 * one run, and its failure reaches the async lane's log.
 */
@NullMarked
final class SerialWarpEdits {

    private final Scheduler scheduler;
    private final Map<String, CompletableFuture<Void>> tails = new ConcurrentHashMap<>();

    SerialWarpEdits(Scheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /** Run {@code edit} on the async lane once every earlier edit of the warp {@code key} has finished. */
    void run(String key, Runnable edit) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(edit, "edit");
        CompletableFuture<Void> done = new CompletableFuture<>();
        CompletableFuture<Void> previous = tails.put(key, done);
        Runnable step = () -> {
            try {
                edit.run();
            } finally {
                settle(key, done);
            }
        };
        if (previous == null) {
            offTick(key, done, step);
        } else {
            var unused = previous.whenComplete((ignored, failure) -> offTick(key, done, step));
        }
    }

    private void offTick(String key, CompletableFuture<Void> done, Runnable step) {
        try {
            scheduler.async(step);
        } catch (RuntimeException refused) {
            // The lane refuses work while the plugin stops; this edit never runs, and the next must not wait on it.
            settle(key, done);
        }
    }

    private void settle(String key, CompletableFuture<Void> done) {
        tails.remove(key, done);
        done.complete(null);
    }
}
