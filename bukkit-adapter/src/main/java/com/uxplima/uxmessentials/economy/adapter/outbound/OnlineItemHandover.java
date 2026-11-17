package com.uxplima.uxmessentials.economy.adapter.outbound;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.economy.application.DeliverSweptItems;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * One pass handing the players online here the deliveries queued for them: after each chunk of a sweep on this
 * server, and when another server says a sweep moved on. The roster is read on the global region and the deliveries
 * made off the tick. Passes do not overlap: a pass asked for while one runs is folded into one more pass after it, so
 * a chunk queued meanwhile is not missed and a burst of chunks does not pile up passes.
 *
 * <p>Every way a pass can end (the roster read throws, the hand-over throws, a scheduler refuses a task) ends it, so
 * one failure never leaves the passes switched off for the rest of the server's life.
 */
@NullMarked
public final class OnlineItemHandover implements Runnable {

    private final Scheduler scheduler;
    private final Supplier<List<PlayerRef>> roster;
    private final Consumer<List<PlayerRef>> handOver;
    private final Logger log;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean again = new AtomicBoolean();

    public OnlineItemHandover(Scheduler scheduler, DeliverSweptItems deliver, Logger log) {
        this(scheduler, OnlineItemHandover::onlinePlayers, deliver::toOnline, log);
    }

    /**
     * @param roster the players online here, read on the global region
     * @param handOver one pass over them, off the tick
     */
    public OnlineItemHandover(
            Scheduler scheduler, Supplier<List<PlayerRef>> roster, Consumer<List<PlayerRef>> handOver, Logger log) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.roster = Objects.requireNonNull(roster, "roster");
        this.handOver = Objects.requireNonNull(handOver, "handOver");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public void run() {
        again.set(true);
        if (!running.compareAndSet(false, true)) {
            return;
        }
        pass();
    }

    private void pass() {
        again.set(false);
        try {
            scheduler.onGlobal(this::readRoster);
        } catch (RuntimeException refused) {
            log.error("event=swept_items_pass_failed stage=global", refused);
            finished();
        }
    }

    private void readRoster() {
        List<PlayerRef> online;
        try {
            online = List.copyOf(roster.get());
        } catch (RuntimeException failure) {
            log.error("event=swept_items_pass_failed stage=roster", failure);
            finished();
            return;
        }
        try {
            scheduler.async(() -> deliver(online));
        } catch (RuntimeException refused) {
            log.error("event=swept_items_pass_failed stage=async", refused);
            finished();
        }
    }

    private void deliver(List<PlayerRef> online) {
        try {
            handOver.accept(online);
        } catch (RuntimeException failure) {
            log.error("event=swept_items_pass_failed stage=deliver", failure);
        } finally {
            finished();
        }
    }

    private void finished() {
        if (again.get()) {
            pass();
            return;
        }
        running.set(false);
        // A pass asked for between the check above and the release would otherwise wait for the next one.
        if (again.get() && running.compareAndSet(false, true)) {
            pass();
        }
    }

    private static List<PlayerRef> onlinePlayers() {
        List<PlayerRef> online = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            online.add(BukkitRefs.toRef(player));
        }
        return online;
    }
}
