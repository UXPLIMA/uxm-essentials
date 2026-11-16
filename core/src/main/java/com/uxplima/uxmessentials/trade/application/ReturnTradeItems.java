package com.uxplima.uxmessentials.trade.application;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.trade.application.port.TradeItemReceiver;
import com.uxplima.uxmessentials.trade.application.port.TradeItemReturns;
import com.uxplima.uxmessentials.trade.domain.TradeMessageKey;
import com.uxplima.uxmessentials.vaults.domain.VaultContents;

/**
 * Keeps the items a trade could not hand to a player who was offline or unreachable, and hands them over on that
 * player's next join.
 *
 * <p>On join every kept batch is claimed by deleting its row first, and only the server whose delete removed the row
 * hands it over, so a batch reaches the player exactly once, even when they join two servers in quick succession. A
 * batch claimed but not given (the player left first, or the give failed) is kept again straight away. What does not
 * fit the inventory is kept for the next join too rather than dropped, since a join point is often a crowded spawn
 * where a dropped stack can be picked up by someone else or despawn. A batch kept while the player is already online
 * waits for their next join; it is never lost.
 *
 * <p>Keeping writes to storage, so it runs on the async lane. Once the plugin is no longer running the scheduler runs
 * nothing more, and the items are kept on the calling thread instead; that only happens while the server stops.
 */
public final class ReturnTradeItems {

    private final Scheduler scheduler;
    private final TradeItemReturns returns;
    private final TradeItemReceiver receiver;
    private final Notifier notifier;
    private final Logger log;
    private final BooleanSupplier running;

    public ReturnTradeItems(
            Scheduler scheduler,
            TradeItemReturns returns,
            TradeItemReceiver receiver,
            Notifier notifier,
            Logger log,
            BooleanSupplier running) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.returns = Objects.requireNonNull(returns, "returns");
        this.receiver = Objects.requireNonNull(receiver, "receiver");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.log = Objects.requireNonNull(log, "log");
        this.running = Objects.requireNonNull(running, "running");
    }

    /** Whether the scheduler still runs work; when it does not, a hand-over must keep its items on the calling thread. */
    public boolean running() {
        return running.getAsBoolean();
    }

    /** Keep {@code items} for {@code owner}'s next join: off the tick, or on the calling thread once stopped. */
    public void keep(PlayerRef owner, VaultContents items) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(items, "items");
        if (items.isEmpty()) {
            return;
        }
        if (!running.getAsBoolean()) {
            keepNow(owner, items);
            return;
        }
        scheduler.async(() -> keepNow(owner, items));
    }

    /**
     * Hand {@code owner} whatever was kept for them while they were away. Reads storage, so it runs off the tick. A
     * read that fails leaves the rows where they are, so the next join tries again.
     */
    public void deliverKept(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        List<TradeItemReturns.Kept> kept;
        try {
            kept = returns.kept(owner.uuid());
        } catch (RuntimeException failure) {
            log.error("event=trade_return_read_failed player=" + owner.uuid(), failure);
            return;
        }
        for (TradeItemReturns.Kept batch : kept) {
            boolean claimed;
            try {
                claimed = returns.claim(batch.id());
            } catch (RuntimeException failure) {
                // The row stays, so the next join tries again.
                log.error("event=trade_return_claim_failed player=" + owner.uuid() + " id=" + batch.id(), failure);
                continue;
            }
            if (claimed) {
                VaultContents items = batch.items();
                scheduler.onEntity(owner, () -> give(owner, items), () -> keep(owner, items));
            }
        }
    }

    private void give(PlayerRef owner, VaultContents items) {
        Optional<TradeItemReceiver.Receipt> receipt;
        try {
            receipt = receiver.give(owner, items);
        } catch (RuntimeException failure) {
            // A receiver reports a partial give as leftover, so a throw means nothing went in and the batch is kept.
            log.error("event=trade_return_give_failed player=" + owner.uuid() + " (kept for the next join)", failure);
            keep(owner, items);
            return;
        }
        if (receipt.isEmpty()) {
            keep(owner, items);
            return;
        }
        TradeItemReceiver.Receipt given = receipt.get();
        if (given.given() > 0) {
            notifier.send(
                    owner, TradeMessageKey.TRADE_RETURNS_DELIVERED, Map.of("count", Integer.toString(given.given())));
        }
        if (!given.leftover().isEmpty()) {
            keep(owner, given.leftover());
            notifier.send(
                    owner,
                    TradeMessageKey.TRADE_RETURNS_KEPT,
                    Map.of("count", Integer.toString(given.leftoverCount())));
        }
    }

    private void keepNow(PlayerRef owner, VaultContents items) {
        try {
            returns.keep(owner.uuid(), items);
        } catch (RuntimeException failure) {
            // Storage is gone: the items are written to the log so an operator can give them back by hand.
            log.error(
                    "event=trade_return_keep_failed player=" + owner.uuid() + " items="
                            + items.payload()
                                    .map(Base64.getEncoder()::encodeToString)
                                    .orElse(""),
                    failure);
        }
    }
}
