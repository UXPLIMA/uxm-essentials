package com.uxplima.uxmessentials.economy.application;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import com.uxplima.uxmessentials.economy.application.port.ItemReturnStore;
import com.uxplima.uxmessentials.economy.application.port.SellerInventory;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Sells items that were already taken from a seller and makes sure every item the sale does not pay for goes back.
 * The items leave the inventory before any money moves, so a sale that is refused, or that throws on its way through
 * a provider or the database, must not keep them. A seller who left before the items could go back finds them on
 * their next join, kept in the {@link ItemReturnStore} until then.
 *
 * <p>Every batch of items that is neither in an inventory nor in the store is tracked from the moment it is taken
 * until one of the two holds it. Whoever removes a batch from the tracking owns it, so a give-back and the
 * {@link #stop()} sweep never both act on the same items. The scheduler runs nothing once the plugin is disabling,
 * which is why the sweep keeps what is still tracked on the stopping thread.
 */
public final class ReturnUnsoldItems {

    /** One sale over the taken items. */
    @FunctionalInterface
    public interface Sale {

        /**
         * Sell {@code taken} (item id to count) and answer how many of each were paid for; an id left out was not.
         * Each credit that applies is reported to {@code paid} at once, so a sale that throws afterwards still keeps
         * what it was paid for.
         */
        Map<String, Integer> sell(Map<String, Integer> taken, Paid paid);
    }

    /** Told the moment part of a sale has been paid for. */
    @FunctionalInterface
    public interface Paid {

        /** {@code items} (item id to count) have been paid for. */
        void paid(Map<String, Integer> items);
    }

    private record Batch(PlayerRef owner, Map<String, Integer> items) {}

    private final Scheduler scheduler;
    private final SellerInventory inventory;
    private final ItemReturnStore store;
    private final EconomyNotifier notifier;
    private final Logger log;
    private final ConcurrentMap<Long, Batch> inFlight = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();
    private volatile boolean stopped;

    public ReturnUnsoldItems(
            Scheduler scheduler,
            SellerInventory inventory,
            ItemReturnStore store,
            EconomyNotifier notifier,
            Logger log) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.inventory = Objects.requireNonNull(inventory, "inventory");
        this.store = Objects.requireNonNull(store, "store");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.log = Objects.requireNonNull(log, "log");
    }

    /**
     * Run {@code sale} off the tick for {@code taken} (item id to count, already out of the seller's inventory) and
     * give back what it did not pay for. A sale that throws keeps only what it reported as paid before it threw.
     */
    public void sell(PlayerRef seller, Map<String, Integer> taken, Sale sale) {
        Objects.requireNonNull(seller, "seller");
        Objects.requireNonNull(sale, "sale");
        Map<String, Integer> items = Collections.unmodifiableMap(new LinkedHashMap<>(taken));
        long id = track(seller, items);
        if (stopped) {
            keepNow(id);
            return;
        }
        scheduler.async(() -> {
            if (inFlight.remove(id) == null) {
                return; // the stop sweep kept them
            }
            Map<String, Integer> paid = new ConcurrentHashMap<>();
            Map<String, Integer> unsold;
            boolean failed = false;
            try {
                Map<String, Integer> sold =
                        sale.sell(items, part -> part.forEach((item, count) -> paid.merge(item, count, Integer::sum)));
                sold.forEach((item, count) -> paid.merge(item, count, Math::max));
                unsold = unsold(items, paid);
            } catch (RuntimeException failure) {
                unsold = unsold(items, paid);
                failed = true;
                log.error(
                        "event=sell_failed seller=" + seller.uuid() + " items=" + items + " paid=" + paid
                                + " (the rest is given back)",
                        failure);
            }
            giveBack(seller, unsold, false);
            if (failed) {
                notifySafely(seller, () -> notifier.send(seller, EconomyMessageKey.SELL_FAILED));
            }
        });
    }

    /**
     * Hand {@code player} whatever was kept for them while they were away. Reads storage, so it runs off the tick.
     * The rows leave the store here and are tracked until they are in the inventory, so a player who leaves again, or
     * a stop before the hand-over, puts them back.
     */
    public void deliverKept(PlayerRef player) {
        Objects.requireNonNull(player, "player");
        Map<String, Integer> kept;
        try {
            kept = store.takeAll(player.uuid());
        } catch (RuntimeException failure) {
            // The rows stay where they are, so the next join tries again.
            log.error("event=sell_return_read_failed player=" + player.uuid(), failure);
            return;
        }
        giveBack(player, kept, true);
    }

    /**
     * Keep every batch still in flight, on the calling thread, before storage closes. A sale already running when this
     * is called is not in the tracking while it runs; it keeps its own rest once it ends. That is the window left: if
     * storage has closed by then, the items are written to the log for an operator to give back by hand.
     */
    public void stop() {
        stopped = true;
        for (Long id : inFlight.keySet()) {
            keepNow(id);
        }
    }

    /**
     * Give {@code items} back on the seller's own thread, or keep them if the seller is gone by then. The retired
     * branch can run on a region thread, so keeping always hops to the async lane before it touches storage.
     */
    private void giveBack(PlayerRef owner, Map<String, Integer> items, boolean announce) {
        if (items.isEmpty()) {
            return;
        }
        long id = track(owner, items);
        if (stopped) {
            keepNow(id);
            return;
        }
        scheduler.onEntity(
                owner,
                () -> {
                    Batch batch = inFlight.remove(id);
                    if (batch == null) {
                        return; // the stop sweep kept them
                    }
                    if (!inventory.giveBack(owner, items)) {
                        inFlight.put(id, batch);
                        keepLater(id);
                        return;
                    }
                    if (announce) {
                        notifySafely(
                                owner,
                                () -> notifier.send(
                                        owner,
                                        EconomyMessageKey.SELL_RETURNED,
                                        Map.of("count", Integer.toString(total(items)))));
                    }
                },
                () -> keepLater(id));
    }

    private long track(PlayerRef owner, Map<String, Integer> items) {
        long id = ids.incrementAndGet();
        inFlight.put(id, new Batch(owner, items));
        return id;
    }

    /**
     * Keep a tracked batch from the async lane. Once stopping, the scheduler runs nothing more, so the batch is kept on
     * the calling thread instead; that can be a region thread, but only while the plugin is shutting down. Checked
     * after the batch is tracked, so either this or the stop sweep sees it.
     */
    private void keepLater(long id) {
        if (stopped) {
            keepNow(id);
            return;
        }
        scheduler.async(() -> keepNow(id));
    }

    private void keepNow(long id) {
        Batch batch = inFlight.remove(id);
        if (batch == null || batch.items().isEmpty()) {
            return;
        }
        try {
            store.keep(batch.owner().uuid(), batch.items());
        } catch (RuntimeException failure) {
            // Nothing else holds these items now, so the line carries all an operator needs to give them back by hand.
            log.error("event=sell_return_lost owner=" + batch.owner().uuid() + " items=" + batch.items(), failure);
        }
    }

    private void notifySafely(PlayerRef player, Runnable send) {
        try {
            send.run();
        } catch (RuntimeException failure) {
            log.error("event=sell_notice_failed player=" + player.uuid(), failure);
        }
    }

    /** What was taken and not paid for, per item id; a count paid beyond what was taken is ignored. */
    private static Map<String, Integer> unsold(Map<String, Integer> taken, Map<String, Integer> sold) {
        Map<String, Integer> rest = new LinkedHashMap<>();
        taken.forEach((id, count) -> {
            int left = count - Math.min(count, Math.max(0, sold.getOrDefault(id, 0)));
            if (left > 0) {
                rest.put(id, left);
            }
        });
        return rest;
    }

    private static int total(Map<String, Integer> items) {
        return items.values().stream().mapToInt(Integer::intValue).sum();
    }
}
