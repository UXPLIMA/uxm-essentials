package com.uxplima.uxmessentials.economy.application;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import com.uxplima.uxmessentials.economy.application.port.ItemDeliveryQueue;
import com.uxplima.uxmessentials.economy.application.port.ItemDeliveryQueue.ItemDelivery;
import com.uxplima.uxmessentials.economy.application.port.ItemPurse;
import com.uxplima.uxmessentials.economy.application.port.TransactionHistory;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.CurrencyRegistry;
import com.uxplima.uxmessentials.economy.domain.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.domain.EconomyReason;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Hands over the deliveries queued for balances that can only change while their player is online (items in an
 * inventory, experience), to a player online on this server: on their join, after each chunk of a sweep (here, or on
 * another server that said so), and for {@code /eco giveall --online}.
 *
 * <p>Each delivery is claimed first and finished only after the balance changed, so it is handed over once however
 * many servers try at once. A finish that could not be stored is kept here and tried again before every pass and on
 * a timer, refreshing the claim meanwhile, so the claim never goes stale and nobody else hands the same delivery
 * over a second time. One player's deliveries are handed over one at a time here, so a pass and a join never
 * interleave a reset with a give.
 *
 * <p>Per currency a reset is applied before any give: the reset removed everything that waited before it, so every
 * give beside it came later and must land on top of it, whatever the clocks of the servers that queued them say. A
 * give whose reset could not be applied yet waits with it. A give puts in what fits and keeps the rest, held back
 * until the player's next join, so nothing is dropped on the ground; a reset's shortfall is kept the same way. The
 * ledger gets one row per delivery, for what actually moved, with the reason of what queued it. Off the tick.
 */
@NullMarked
public final class DeliverSweptItems {

    /** Well inside the queue's stale window, so a claim kept for a finish is refreshed before it could go stale. */
    static final Duration RETRY_AFTER = Duration.ofMinutes(1);

    private static final int LOCK_STRIPES = 64;

    private final ItemDeliveryQueue queue;
    private final Function<Currency, Optional<ItemPurse>> purses;
    private final CurrencyRegistry currencies;
    private final TransactionHistory history;
    private final EconomyNotifier notifier;
    private final PlayerLookup players;
    private final Scheduler scheduler;
    private final Logger log;
    private final java.time.Clock clock;
    private final String claimant = UUID.randomUUID().toString();
    /** Hand-overs that happened but whose finish was not stored yet: delivery id to the rest it leaves. */
    private final Map<String, BigDecimal> unfinished = new ConcurrentHashMap<>();

    private final AtomicBoolean retryPlanned = new AtomicBoolean();
    private final Object[] playerLocks = new Object[LOCK_STRIPES];

    /**
     * @param purses the balance a currency's deliveries change, or empty for a currency that is not delivered this way
     */
    public DeliverSweptItems(
            ItemDeliveryQueue queue,
            Function<Currency, Optional<ItemPurse>> purses,
            CurrencyRegistry currencies,
            TransactionHistory history,
            EconomyNotifier notifier,
            PlayerLookup players,
            Scheduler scheduler,
            Logger log,
            java.time.Clock clock) {
        this.queue = Objects.requireNonNull(queue, "queue");
        this.purses = Objects.requireNonNull(purses, "purses");
        this.currencies = Objects.requireNonNull(currencies, "currencies");
        this.history = Objects.requireNonNull(history, "history");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.players = Objects.requireNonNull(players, "players");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        for (int i = 0; i < LOCK_STRIPES; i++) {
            playerLocks[i] = new Object();
        }
        this.log = Objects.requireNonNull(log, "log");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Hand {@code player} everything queued for them, the held-back rest too. Called when they join. */
    public void onJoin(PlayerRef player) {
        Objects.requireNonNull(player, "player");
        retryUnfinished();
        deliver(player, true);
    }

    /** Hand each of {@code online} what was queued for them since, leaving held-back rests for their next join. */
    public void toOnline(List<PlayerRef> online) {
        Objects.requireNonNull(online, "online");
        retryUnfinished();
        if (online.isEmpty()) {
            return;
        }
        List<UUID> ids = new ArrayList<>(online.size());
        for (PlayerRef player : online) {
            ids.add(player.uuid());
        }
        List<UUID> waiting;
        try {
            waiting = queue.waiting(ids);
        } catch (RuntimeException failure) {
            log.error("event=swept_items_read_failed players=" + ids.size(), failure);
            return;
        }
        for (PlayerRef player : online) {
            if (waiting.contains(player.uuid())) {
                deliver(player, false);
            }
        }
    }

    /**
     * {@code /eco giveall --online} for a currency delivered this way: queue a give for each of {@code online} and
     * hand it over now, so what does not fit is kept instead of dropped. Returns how many were queued.
     */
    public int giveNow(List<PlayerRef> online, Money amount, EconomyReason reason) {
        Objects.requireNonNull(online, "online");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(reason, "reason");
        if (online.isEmpty()) {
            return 0;
        }
        List<UUID> owners = new ArrayList<>(online.size());
        for (PlayerRef player : online) {
            owners.add(player.uuid());
        }
        // A sweep id of its own, so each such give is a delivery of its own until it merges into one waiting.
        queue.queueGive(
                UUID.randomUUID().toString(), owners, amount.currency().id().value(), amount.amount(), reason);
        toOnline(online);
        return owners.size();
    }

    /** Whether {@code player}'s credits in {@code currency} must wait for a reset still queued for them. */
    public boolean holdsCredits(PlayerRef player, String currency) {
        try {
            return queue.resetWaiting(player.uuid(), currency);
        } catch (RuntimeException failure) {
            // Unknown: holding back is the safe side, the credit is paid on the next join.
            log.error("event=swept_items_read_failed player=" + player.uuid(), failure);
            return true;
        }
    }

    /**
     * Store the finishes that failed before, refreshing their claims while they still wait. Called before every pass
     * and on a timer.
     */
    public void retryUnfinished() {
        for (Map.Entry<String, BigDecimal> entry : List.copyOf(unfinished.entrySet())) {
            String id = entry.getKey();
            try {
                queue.touch(id, claimant);
                if (!queue.finish(id, claimant, entry.getValue())) {
                    // Gone or no longer ours: a reset removed it, or an earlier try did store it after all.
                    log.warn("event=swept_items_finish_moot id={}", id);
                }
                unfinished.remove(id);
            } catch (RuntimeException failure) {
                log.warn("event=swept_items_finish_retry_failed id={} reason={}", id, failure.toString());
            }
        }
        if (!unfinished.isEmpty()) {
            planRetry();
        }
    }

    private void planRetry() {
        if (!retryPlanned.compareAndSet(false, true)) {
            return;
        }
        try {
            scheduler.asyncAfter(RETRY_AFTER, () -> {
                retryPlanned.set(false);
                retryUnfinished();
            });
        } catch (RuntimeException refused) {
            // The next pass or join tries again instead.
            retryPlanned.set(false);
            log.warn("event=swept_items_retry_unplanned reason={}", refused.toString());
        }
    }

    private void deliver(PlayerRef player, boolean withHeldBack) {
        synchronized (playerLocks[Math.floorMod(player.uuid().hashCode(), LOCK_STRIPES)]) {
            deliverHeld(player, withHeldBack);
        }
    }

    private void deliverHeld(PlayerRef player, boolean withHeldBack) {
        if (!players.isOnline(player.uuid())) {
            return;
        }
        List<ItemDelivery> open;
        try {
            open = queue.claimable(player.uuid(), withHeldBack);
        } catch (RuntimeException failure) {
            log.error("event=swept_items_read_failed player=" + player.uuid(), failure);
            return;
        }
        Map<String, List<ItemDelivery>> byCurrency = new LinkedHashMap<>();
        for (ItemDelivery delivery : open) {
            if (unfinished.containsKey(delivery.id())) {
                continue;
            }
            byCurrency
                    .computeIfAbsent(delivery.currency(), id -> new ArrayList<>())
                    .add(delivery);
        }
        for (Map.Entry<String, List<ItemDelivery>> currency : byCurrency.entrySet()) {
            deliverCurrency(player, currency.getKey(), currency.getValue());
        }
    }

    /** One currency's deliveries: the reset first, then the gives that came after it. */
    private void deliverCurrency(PlayerRef player, String currencyId, List<ItemDelivery> deliveries) {
        Optional<Currency> currency = currencies.find(CurrencyId.of(currencyId));
        Optional<ItemPurse> purse = currency.flatMap(purses);
        if (currency.isEmpty() || purse.isEmpty()) {
            // The currency left the configuration, or is no longer delivered this way: it waits for that to change.
            return;
        }
        List<ItemDelivery> ordered = new ArrayList<>(deliveries.size());
        for (ItemDelivery delivery : deliveries) {
            if (delivery.kind() == ItemDeliveryQueue.Kind.RESET) {
                ordered.add(delivery);
            }
        }
        boolean resetPending = !ordered.isEmpty();
        for (ItemDelivery delivery : deliveries) {
            if (delivery.kind() == ItemDeliveryQueue.Kind.GIVE) {
                ordered.add(delivery);
            }
        }
        for (ItemDelivery seen : ordered) {
            Optional<ItemDelivery> claimed = queue.claim(seen.id(), claimant);
            if (claimed.isEmpty()) {
                if (seen.kind() == ItemDeliveryQueue.Kind.RESET) {
                    // Another server is applying the reset; the gives after it wait until it has.
                    return;
                }
                continue;
            }
            ItemDelivery delivery = claimed.get();
            if (!handOver(player, currency.get(), purse.get(), delivery)) {
                // The player left, or their balance did not answer; nothing moved, the next pass tries again. A give
                // must not land before the reset it came after.
                queue.release(delivery.id(), claimant);
                return;
            }
            if (delivery.kind() == ItemDeliveryQueue.Kind.RESET) {
                resetPending = false;
            }
        }
        if (resetPending) {
            log.warn("event=swept_items_reset_waiting player={} currency={}", player.uuid(), currencyId);
        }
    }

    /** Hand one claimed delivery over; false when nothing could be done and the claim should be given back. */
    private boolean handOver(PlayerRef player, Currency currency, ItemPurse purse, ItemDelivery delivery) {
        Money amount = Money.of(currency, delivery.amount());
        try {
            return switch (delivery.kind()) {
                case GIVE -> give(player, purse, amount, delivery);
                case RESET -> reset(player, purse, amount, delivery);
            };
        } catch (RuntimeException failure) {
            log.error("event=swept_items_failed player=" + player.uuid() + " id=" + delivery.id(), failure);
            return false;
        }
    }

    private boolean give(PlayerRef player, ItemPurse purse, Money amount, ItemDelivery delivery) {
        Optional<BigDecimal> fit = purse.give(player, amount);
        if (fit.isEmpty()) {
            return false;
        }
        BigDecimal delivered = fit.get();
        BigDecimal rest = amount.amount().subtract(delivered).max(BigDecimal.ZERO);
        finish(delivery, rest);
        Currency currency = amount.currency();
        if (delivered.signum() > 0) {
            Money moved = Money.of(currency, delivered);
            history.recordCredit(ownerId(player), moved, delivery.reason(), clock.millis());
            notifier.send(player, EconomyMessageKey.ECO_ITEMS_DELIVERED, Map.of("amount", notifier.amount(moved)));
        }
        if (rest.signum() > 0) {
            notifier.send(
                    player,
                    EconomyMessageKey.ECO_ITEMS_WAITING,
                    Map.of("amount", notifier.amount(Money.of(currency, rest))));
        }
        return true;
    }

    private boolean reset(PlayerRef player, ItemPurse purse, Money target, ItemDelivery delivery) {
        Optional<ItemPurse.Change> change = purse.setTo(player, target);
        if (change.isEmpty()) {
            return false;
        }
        BigDecimal before = change.get().before();
        BigDecimal after = change.get().after();
        BigDecimal shortfall = target.amount().subtract(after).max(BigDecimal.ZERO);
        // The shortfall stays a give under the reset's own reason.
        finish(delivery, shortfall);
        Currency currency = target.currency();
        int sign = after.compareTo(before);
        if (sign != 0) {
            Money delta = Money.of(currency, after.subtract(before).abs());
            if (sign > 0) {
                history.recordCredit(ownerId(player), delta, delivery.reason(), clock.millis());
            } else {
                history.recordDebit(ownerId(player), delta, delivery.reason(), clock.millis());
            }
        }
        notifier.send(player, EconomyMessageKey.ECO_ITEMS_RESET, Map.of("amount", notifier.amount(target)));
        if (shortfall.signum() > 0) {
            notifier.send(
                    player,
                    EconomyMessageKey.ECO_ITEMS_WAITING,
                    Map.of("amount", notifier.amount(Money.of(currency, shortfall))));
        }
        return true;
    }

    /**
     * The balance changed, so a finish that fails must not give the claim back: it is kept and stored on the next
     * try, its claim refreshed until then, so the delivery is never handed over again.
     */
    private void finish(ItemDelivery delivery, BigDecimal rest) {
        boolean finished;
        try {
            finished = queue.finish(delivery.id(), claimant, rest);
        } catch (RuntimeException failure) {
            unfinished.put(delivery.id(), rest);
            planRetry();
            log.error(
                    "event=swept_items_finish_failed id=" + delivery.id() + " owner=" + delivery.owner()
                            + " (handed over; the finish is tried again)",
                    failure);
            return;
        }
        if (!finished) {
            // A reset removed it while it was handed over; the reset still applies after it.
            log.warn("event=swept_items_claim_lost id={} owner={}", delivery.id(), delivery.owner());
        }
    }

    private static String ownerId(PlayerRef owner) {
        return owner.uuid().toString();
    }
}
