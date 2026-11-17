package com.uxplima.uxmessentials.economy.adapter.outbound;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.economy.application.DeliverSweptItems;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * One pass handing the players online here the item deliveries a sweep queued for them: after each chunk of a sweep
 * on this server, and when another server says a sweep moved on. The roster is read on the global region and the
 * deliveries made off the tick. Passes do not overlap: a pass asked for while one runs is folded into one more pass
 * after it, so a chunk queued meanwhile is not missed and a burst of chunks does not pile up passes.
 */
@NullMarked
public final class OnlineItemHandover implements Runnable {

    private final Scheduler scheduler;
    private final DeliverSweptItems deliver;
    private final Logger log;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean again = new AtomicBoolean();

    public OnlineItemHandover(Scheduler scheduler, DeliverSweptItems deliver, Logger log) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.deliver = Objects.requireNonNull(deliver, "deliver");
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
        scheduler.onGlobal(() -> {
            List<PlayerRef> online = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                online.add(BukkitRefs.toRef(player));
            }
            scheduler.async(() -> {
                try {
                    deliver.toOnline(List.copyOf(online));
                } catch (RuntimeException failure) {
                    log.error("event=swept_items_pass_failed", failure);
                } finally {
                    finished();
                }
            });
        });
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
}
