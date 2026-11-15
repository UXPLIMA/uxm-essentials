package com.uxplima.uxmessentials.economy.application;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

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
 */
public final class ReturnUnsoldItems {

    /** One sale over the taken items. */
    @FunctionalInterface
    public interface Sale {

        /** Sell {@code taken} (item id to count) and answer how many of each were paid for; an id left out was not. */
        Map<String, Integer> sell(Map<String, Integer> taken);
    }

    private final Scheduler scheduler;
    private final SellerInventory inventory;
    private final ItemReturnStore store;
    private final EconomyNotifier notifier;
    private final Logger log;

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
     * give back what it did not pay for. A sale that throws pays for nothing, so everything goes back.
     */
    public void sell(PlayerRef seller, Map<String, Integer> taken, Sale sale) {
        Objects.requireNonNull(seller, "seller");
        Objects.requireNonNull(sale, "sale");
        Map<String, Integer> items = Collections.unmodifiableMap(new LinkedHashMap<>(taken));
        scheduler.async(() -> {
            Map<String, Integer> unsold;
            try {
                unsold = unsold(items, sale.sell(items));
            } catch (RuntimeException failure) {
                log.error("event=sell_failed seller=" + seller.uuid() + " items=" + items + " (given back)", failure);
                notifier.send(seller, EconomyMessageKey.SELL_FAILED);
                unsold = items;
            }
            giveBack(seller, unsold, false);
        });
    }

    /** Hand {@code player} whatever was kept for them while they were away. Reads storage, so it runs off the tick. */
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
     * Give {@code items} back on the seller's own thread, or keep them if the seller is gone by then. The retired
     * branch can run on a region thread, so keeping always hops to the async lane before it touches storage.
     */
    private void giveBack(PlayerRef seller, Map<String, Integer> items, boolean announce) {
        if (items.isEmpty()) {
            return;
        }
        Runnable keep = () -> scheduler.async(() -> keep(seller, items));
        scheduler.onEntity(
                seller,
                () -> {
                    if (!inventory.giveBack(seller, items)) {
                        keep.run();
                        return;
                    }
                    if (announce) {
                        notifier.send(
                                seller,
                                EconomyMessageKey.SELL_RETURNED,
                                Map.of("count", Integer.toString(total(items))));
                    }
                },
                keep);
    }

    private void keep(PlayerRef seller, Map<String, Integer> items) {
        try {
            store.keep(seller.uuid(), items);
        } catch (RuntimeException failure) {
            // Nothing else holds these items now, so the line carries all an operator needs to give them back by hand.
            log.error("event=sell_return_lost owner=" + seller.uuid() + " items=" + items, failure);
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
