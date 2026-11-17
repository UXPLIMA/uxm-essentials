package com.uxplima.uxmessentials.economy.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
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
import com.uxplima.uxmessentials.economy.domain.Transaction;
import com.uxplima.uxmessentials.economy.domain.event.WalletCredited;
import com.uxplima.uxmessentials.economy.domain.event.WalletDebited;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
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
 * give whose reset could not be applied yet waits with it, and so do the player's offline credits of that currency,
 * which are paid as soon as the reset lands. A give puts in what fits and keeps the rest, held back until the
 * player's next join, so nothing is dropped on the ground; a reset's shortfall is kept the same way. Each hand-over
 * writes one ledger row for what actually moved, with the reason of what queued it, and raises the wallet event of
 * that change, so other plugins see it like any other credit or debit. Off the tick.
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
    private final DomainEventPublisher events;
    private final Consumer<PlayerRef> resetApplied;
    private final PlayerLookup players;
    private final Scheduler scheduler;
    private final Logger log;
    private final Clock clock;
    private final String claimant = UUID.randomUUID().toString();
    /** Hand-overs that happened but whose finish was not stored yet: delivery id to the rest it leaves. */
    private final Map<String, BigDecimal> unfinished = new ConcurrentHashMap<>();
    /** The resets that could not be applied, as {@code owner:currency}, so each is reported once, not every pass. */
    private final Set<String> stuckResets = ConcurrentHashMap.newKeySet();

    private final AtomicBoolean retryPlanned = new AtomicBoolean();
    private final Object[] playerLocks = new Object[LOCK_STRIPES];

    /**
     * @param purses the balance a currency's deliveries change, or empty for a currency that is not delivered this way
     * @param resetApplied called with a player whose reset was just applied, to pay the credits that waited for it
     */
    public DeliverSweptItems(
            ItemDeliveryQueue queue,
            Function<Currency, Optional<ItemPurse>> purses,
            CurrencyRegistry currencies,
            TransactionHistory history,
            EconomyNotifier notifier,
            DomainEventPublisher events,
            Consumer<PlayerRef> resetApplied,
            PlayerLookup players,
            Scheduler scheduler,
            Logger log,
            Clock clock) {
        this.queue = Objects.requireNonNull(queue, "queue");
        this.purses = Objects.requireNonNull(purses, "purses");
        this.currencies = Objects.requireNonNull(currencies, "currencies");
        this.history = Objects.requireNonNull(history, "history");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.events = Objects.requireNonNull(events, "events");
        this.resetApplied = Objects.requireNonNull(resetApplied, "resetApplied");
        this.players = Objects.requireNonNull(players, "players");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
        this.clock = Objects.requireNonNull(clock, "clock");
        for (int i = 0; i < LOCK_STRIPES; i++) {
            playerLocks[i] = new Object();
        }
    }

    /** What {@code /eco giveall --online} did: the players a give was queued for, and how many of them kept a rest. */
    public record OnlineGive(int players, int heldBack) {}

    /** Hand {@code player} everything queued for them, the held-back rest too. Called when they join. */
    public void onJoin(PlayerRef player) {
        Objects.requireNonNull(player, "player");
        retryUnfinished();
        deliver(player, true);
    }

    /** Hand each of {@code online} what was queued for them since, leaving held-back rests for their next join. */
    public void toOnline(List<PlayerRef> online) {
        Objects.requireNonNull(online, "online");
        handOut(online);
    }

    /**
     * {@code /eco giveall --online} for a currency delivered this way: queue a give for each of {@code online} and
     * hand it over now, so what does not fit is kept instead of dropped.
     */
    public OnlineGive giveNow(List<PlayerRef> online, Money amount, EconomyReason reason) {
        Objects.requireNonNull(online, "online");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(reason, "reason");
        if (online.isEmpty()) {
            return new OnlineGive(0, 0);
        }
        List<UUID> owners = new ArrayList<>(online.size());
        for (PlayerRef player : online) {
            owners.add(player.uuid());
        }
        // A sweep id of its own, so each such give is a delivery of its own until it merges into one waiting.
        queue.queueGive(
                UUID.randomUUID().toString(), owners, amount.currency().id().value(), amount.amount(), reason);
        return new OnlineGive(owners.size(), handOut(online));
    }

    /**
     * The currencies whose credits {@code player} must not be paid yet, because a reset of them still waits: they came
     * after it, and paying them first would let the reset take them away again. Throws when the queue cannot be read,
     * so the caller leaves every credit where it is.
     */
    public Set<String> heldCurrencies(PlayerRef player) {
        return queue.resetsWaiting(player.uuid());
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

    /** Hand each of {@code online} what waits for them; returns how many still have something waiting after. */
    private int handOut(List<PlayerRef> online) {
        retryUnfinished();
        if (online.isEmpty()) {
            return 0;
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
            return online.size();
        }
        int left = 0;
        for (PlayerRef player : online) {
            if (waiting.contains(player.uuid()) && !deliver(player, false)) {
                left++;
            }
        }
        return left;
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

    /** Whether everything handed over landed in full, leaving nothing waiting. */
    private boolean deliver(PlayerRef player, boolean withHeldBack) {
        synchronized (playerLocks[Math.floorMod(player.uuid().hashCode(), LOCK_STRIPES)]) {
            return deliverHeld(player, withHeldBack);
        }
    }

    private boolean deliverHeld(PlayerRef player, boolean withHeldBack) {
        if (!players.isOnline(player.uuid())) {
            return false;
        }
        List<ItemDelivery> open;
        try {
            open = queue.claimable(player.uuid(), withHeldBack);
        } catch (RuntimeException failure) {
            log.error("event=swept_items_read_failed player=" + player.uuid(), failure);
            return false;
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
        boolean allLanded = true;
        for (Map.Entry<String, List<ItemDelivery>> currency : byCurrency.entrySet()) {
            allLanded &= deliverCurrency(player, currency.getKey(), currency.getValue());
        }
        return allLanded;
    }

    /** One currency's deliveries: the reset first, then the gives that came after it. */
    private boolean deliverCurrency(PlayerRef player, String currencyId, List<ItemDelivery> deliveries) {
        Optional<Currency> currency = currencies.find(CurrencyId.of(currencyId));
        Optional<ItemPurse> purse = currency.flatMap(purses);
        if (currency.isEmpty() || purse.isEmpty()) {
            // The currency left the configuration, or is no longer delivered this way: it waits for that to change.
            return false;
        }
        List<ItemDelivery> ordered = new ArrayList<>(deliveries.size());
        for (ItemDelivery delivery : deliveries) {
            if (delivery.kind() == ItemDeliveryQueue.Kind.RESET) {
                ordered.add(delivery);
            }
        }
        for (ItemDelivery delivery : deliveries) {
            if (delivery.kind() == ItemDeliveryQueue.Kind.GIVE) {
                ordered.add(delivery);
            }
        }
        boolean allLanded = true;
        boolean resetLanded = false;
        for (ItemDelivery seen : ordered) {
            Optional<ItemDelivery> claimed = queue.claim(seen.id(), claimant);
            if (claimed.isEmpty()) {
                if (seen.kind() == ItemDeliveryQueue.Kind.RESET) {
                    // Another server is applying the reset; the gives after it wait until it has.
                    return false;
                }
                continue;
            }
            ItemDelivery delivery = claimed.get();
            Optional<BigDecimal> rest = handOver(player, currency.get(), purse.get(), delivery);
            if (rest.isEmpty()) {
                // The player left, or their balance did not answer; nothing moved, the next pass tries again. A give
                // must not land before the reset it came after.
                queue.release(delivery.id(), claimant);
                if (delivery.kind() == ItemDeliveryQueue.Kind.RESET) {
                    stuck(player, currencyId);
                }
                return false;
            }
            allLanded &= rest.get().signum() == 0;
            if (delivery.kind() == ItemDeliveryQueue.Kind.RESET) {
                stuckResets.remove(player.uuid() + ":" + currencyId);
                resetLanded = true;
            }
        }
        if (resetLanded) {
            payWaitingCredits(player);
        }
        return allLanded;
    }

    /** A reset that cannot be applied holds that currency's gives and credits: said once, as an error. */
    private void stuck(PlayerRef player, String currencyId) {
        if (stuckResets.add(player.uuid() + ":" + currencyId)) {
            log.error(
                    "event=swept_items_reset_stuck player=" + player.uuid() + " currency=" + currencyId
                            + " (the reset could not be applied; that currency's gives and offline credits wait"
                            + " for it, and it is tried again on every pass and join)",
                    new IllegalStateException("reset of " + currencyId + " not applied"));
        }
    }

    private void payWaitingCredits(PlayerRef player) {
        try {
            resetApplied.accept(player);
        } catch (RuntimeException failure) {
            // They stay queued and are paid on the next join.
            log.error("event=swept_items_credits_after_reset_failed player=" + player.uuid(), failure);
        }
    }

    /**
     * Hand one claimed delivery over; the amount it leaves waiting, or empty when nothing could be done and the claim
     * should be given back.
     */
    private Optional<BigDecimal> handOver(PlayerRef player, Currency currency, ItemPurse purse, ItemDelivery delivery) {
        Money amount = Money.of(currency, delivery.amount());
        try {
            return switch (delivery.kind()) {
                case GIVE -> give(player, purse, amount, delivery);
                case RESET -> reset(player, purse, amount, delivery);
            };
        } catch (RuntimeException failure) {
            log.error("event=swept_items_failed player=" + player.uuid() + " id=" + delivery.id(), failure);
            return Optional.empty();
        }
    }

    private Optional<BigDecimal> give(PlayerRef player, ItemPurse purse, Money amount, ItemDelivery delivery) {
        Optional<ItemPurse.Change> change = purse.give(player, amount);
        if (change.isEmpty()) {
            return Optional.empty();
        }
        BigDecimal delivered =
                change.get().after().subtract(change.get().before()).max(BigDecimal.ZERO);
        BigDecimal rest = amount.amount().subtract(delivered).max(BigDecimal.ZERO);
        finish(delivery, rest);
        Currency currency = amount.currency();
        if (delivered.signum() > 0) {
            Money moved = Money.of(currency, delivered);
            recorded(player, moved, Money.of(currency, change.get().after()), true, delivery.reason());
            notifier.send(player, EconomyMessageKey.ECO_ITEMS_DELIVERED, Map.of("amount", notifier.amount(moved)));
        }
        if (rest.signum() > 0) {
            notifier.send(
                    player,
                    EconomyMessageKey.ECO_ITEMS_WAITING,
                    Map.of("amount", notifier.amount(Money.of(currency, rest))));
        }
        return Optional.of(rest);
    }

    private Optional<BigDecimal> reset(PlayerRef player, ItemPurse purse, Money target, ItemDelivery delivery) {
        Optional<ItemPurse.Change> change = purse.setTo(player, target);
        if (change.isEmpty()) {
            return Optional.empty();
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
            recorded(player, delta, Money.of(currency, after), sign > 0, delivery.reason());
        }
        notifier.send(player, EconomyMessageKey.ECO_ITEMS_RESET, Map.of("amount", notifier.amount(target)));
        if (shortfall.signum() > 0) {
            notifier.send(
                    player,
                    EconomyMessageKey.ECO_ITEMS_WAITING,
                    Map.of("amount", notifier.amount(Money.of(currency, shortfall))));
        }
        return Optional.of(shortfall);
    }

    /**
     * Write the ledger row of a change that happened and raise its wallet event. An admin change is neither earned
     * nor spent, so the event is not counted, the way every other giveall and resetall change is not.
     */
    private void recorded(PlayerRef player, Money moved, Money resulting, boolean credit, EconomyReason reason) {
        Instant now = clock.instant();
        String ownerId = player.uuid().toString();
        DomainEvent event;
        if (credit) {
            history.recordCredit(ownerId, moved, reason, now.toEpochMilli());
            event = new WalletCredited(
                    player, moved, resulting, Transaction.credit(player, moved, resulting, now), now, false);
        } else {
            history.recordDebit(ownerId, moved, reason, now.toEpochMilli());
            event = new WalletDebited(
                    player, moved, resulting, Transaction.debit(player, moved, resulting, now), now, false);
        }
        try {
            events.publish(event);
        } catch (RuntimeException failure) {
            // The change has landed; a listener that throws must not undo the hand-over's bookkeeping.
            log.error("event=swept_items_event_failed player=" + player.uuid(), failure);
        }
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
}
