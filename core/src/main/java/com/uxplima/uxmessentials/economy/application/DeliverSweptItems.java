package com.uxplima.uxmessentials.economy.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

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
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Hands over the item deliveries a sweep over an item currency queued, in the inventory of a player online on this
 * server: on their join, and after each chunk of a sweep (here, or on another server that said so) for the players
 * online at that moment.
 *
 * <p>Each delivery is claimed first and finished only after the items are in the inventory, so it is handed over
 * once however many servers try at once. A give puts in what fits and keeps the rest queued, held back until the
 * player's next join, so nothing is dropped on the ground and a full inventory is not asked again every few seconds.
 * A reset brings the held amount to the currency's starting figure; when that needs more than fits, the shortfall is
 * kept the same way. The ledger gets one row per delivery, for what actually moved. Off the tick.
 */
@NullMarked
public final class DeliverSweptItems {

    private final ItemDeliveryQueue queue;
    private final ItemPurse purse;
    private final CurrencyRegistry currencies;
    private final TransactionHistory history;
    private final EconomyNotifier notifier;
    private final PlayerLookup players;
    private final Logger log;
    private final String claimant;
    private final java.time.Clock clock;

    public DeliverSweptItems(
            ItemDeliveryQueue queue,
            ItemPurse purse,
            CurrencyRegistry currencies,
            TransactionHistory history,
            EconomyNotifier notifier,
            PlayerLookup players,
            Logger log,
            java.time.Clock clock) {
        this.queue = Objects.requireNonNull(queue, "queue");
        this.purse = Objects.requireNonNull(purse, "purse");
        this.currencies = Objects.requireNonNull(currencies, "currencies");
        this.history = Objects.requireNonNull(history, "history");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.players = Objects.requireNonNull(players, "players");
        this.log = Objects.requireNonNull(log, "log");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.claimant = UUID.randomUUID().toString();
    }

    /** Hand {@code player} everything queued for them, the held-back rest too. Called when they join. */
    public void onJoin(PlayerRef player) {
        Objects.requireNonNull(player, "player");
        deliver(player, true);
    }

    /** Hand each of {@code online} what a sweep queued for them since, leaving held-back rests for their next join. */
    public void toOnline(List<PlayerRef> online) {
        Objects.requireNonNull(online, "online");
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

    private void deliver(PlayerRef player, boolean withHeldBack) {
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
        for (ItemDelivery delivery : open) {
            Optional<Currency> currency = currencies.find(CurrencyId.of(delivery.currency()));
            if (currency.isEmpty() || !currency.get().isPhysical()) {
                // The currency left the configuration, or stopped being items: the delivery waits for it to return.
                continue;
            }
            if (!queue.claim(delivery.id(), claimant)) {
                continue;
            }
            if (!handOver(player, currency.get(), delivery)) {
                // The player left, or their thread did not take it; nothing moved, the next join tries again.
                queue.release(delivery.id(), claimant);
                return;
            }
        }
    }

    /** Hand one claimed delivery over; false when nothing could be done and the claim should be given back. */
    private boolean handOver(PlayerRef player, Currency currency, ItemDelivery delivery) {
        Money amount = Money.of(currency, delivery.amount());
        try {
            return switch (delivery.kind()) {
                case GIVE -> give(player, amount, delivery);
                case RESET -> reset(player, amount, delivery);
            };
        } catch (RuntimeException failure) {
            log.error("event=swept_items_failed player=" + player.uuid() + " id=" + delivery.id(), failure);
            return false;
        }
    }

    private boolean give(PlayerRef player, Money amount, ItemDelivery delivery) {
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
            history.recordCredit(ownerId(player), moved, EconomyReason.ADMIN_GIVEALL, clock.millis());
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

    private boolean reset(PlayerRef player, Money target, ItemDelivery delivery) {
        Optional<ItemPurse.Change> change = purse.setTo(player, target);
        if (change.isEmpty()) {
            return false;
        }
        BigDecimal before = change.get().before();
        BigDecimal after = change.get().after();
        BigDecimal shortfall = target.amount().subtract(after).max(BigDecimal.ZERO);
        finish(delivery, shortfall);
        Currency currency = target.currency();
        int sign = after.compareTo(before);
        if (sign != 0) {
            Money delta = Money.of(currency, after.subtract(before).abs());
            if (sign > 0) {
                history.recordCredit(ownerId(player), delta, EconomyReason.ADMIN_RESETALL, clock.millis());
            } else {
                history.recordDebit(ownerId(player), delta, EconomyReason.ADMIN_RESETALL, clock.millis());
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
     * The items are in the inventory now, so a finish that fails must not hand the claim back: the claim goes stale
     * and the delivery could be handed over again, which the log says so an operator can check.
     */
    private void finish(ItemDelivery delivery, BigDecimal rest) {
        boolean finished;
        try {
            finished = queue.finish(delivery.id(), claimant, rest);
        } catch (RuntimeException failure) {
            log.error(
                    "event=swept_items_finish_failed id=" + delivery.id() + " owner=" + delivery.owner()
                            + " (handed over; the delivery may be handed over again once its claim goes stale)",
                    failure);
            return;
        }
        if (!finished) {
            log.warn("event=swept_items_claim_lost id={} owner={}", delivery.id(), delivery.owner());
        }
    }

    private static String ownerId(PlayerRef owner) {
        return owner.uuid().toString();
    }
}
