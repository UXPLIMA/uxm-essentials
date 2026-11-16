package com.uxplima.uxmessentials.trade.application;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
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
 * <p>On join each kept batch is claimed with a fresh token before it is handed over, and removed only once the items
 * are in the inventory, under that same token. Only one delivery wins a claim, so a batch reaches the player once even
 * when they join two servers in quick succession, and the row is never gone while the items are still in flight. A
 * hand-over that does not land (the player left first, or the give failed) releases the claim, leaving the batch for
 * the next join. A claim that a crashed server never finished goes stale and is taken again; the store explains that
 * trade-off. What does not fit the inventory is put back in place of the batch rather than dropped, since a join
 * point is often a crowded spawn where a dropped stack can be picked up by someone else or despawn. A batch kept while
 * the player is already online waits for their next join.
 *
 * <p>Storage is touched only on the async lane. Once the plugin is no longer running the scheduler runs nothing
 * more, and storage is written on the calling thread instead; that only happens while the server stops.
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
        offTick(() -> keepNow(owner, items));
    }

    /**
     * Hand {@code owner} whatever was kept for them while they were away. Reads storage, so it runs off the tick. A
     * read that fails leaves the rows where they are, so the next join tries again.
     */
    public void deliverKept(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        List<TradeItemReturns.Kept> kept;
        try {
            kept = returns.claimable(owner.uuid());
        } catch (RuntimeException failure) {
            log.error("event=trade_return_read_failed player=" + owner.uuid(), failure);
            return;
        }
        for (TradeItemReturns.Kept batch : kept) {
            String claimant = UUID.randomUUID().toString();
            boolean claimed;
            try {
                claimed = returns.claim(batch.id(), claimant);
            } catch (RuntimeException failure) {
                // The row stays unclaimed, so the next join tries again.
                log.error("event=trade_return_claim_failed player=" + owner.uuid() + " id=" + batch.id(), failure);
                continue;
            }
            if (claimed) {
                scheduler.onEntity(
                        owner,
                        () -> give(owner, batch, claimant),
                        () -> offTick(() -> release(owner, batch, claimant)));
            }
        }
    }

    /** On the player's thread: put the batch in their inventory, then settle the row off the tick. */
    private void give(PlayerRef owner, TradeItemReturns.Kept batch, String claimant) {
        Optional<TradeItemReceiver.Receipt> receipt;
        try {
            receipt = receiver.give(owner, batch.items());
        } catch (RuntimeException failure) {
            // A receiver reports a partial give as leftover, so a throw means nothing went in and the batch stays.
            log.error("event=trade_return_give_failed player=" + owner.uuid() + " (kept for the next join)", failure);
            offTick(() -> release(owner, batch, claimant));
            return;
        }
        if (receipt.isEmpty()) {
            offTick(() -> release(owner, batch, claimant));
            return;
        }
        TradeItemReceiver.Receipt given = receipt.get();
        offTick(() -> finish(owner, batch, claimant, given.leftover()));
        if (given.given() > 0) {
            notifier.send(
                    owner, TradeMessageKey.TRADE_RETURNS_DELIVERED, Map.of("count", Integer.toString(given.given())));
        }
        if (!given.leftover().isEmpty()) {
            notifier.send(
                    owner,
                    TradeMessageKey.TRADE_RETURNS_KEPT,
                    Map.of("count", Integer.toString(given.leftoverCount())));
        }
    }

    private void finish(PlayerRef owner, TradeItemReturns.Kept batch, String claimant, VaultContents rest) {
        try {
            if (!returns.finish(batch.id(), claimant, rest)) {
                // The claim went stale and another delivery took it: the batch may reach the player twice.
                log.warn("event=trade_return_claim_lost player={} id={}", owner.uuid(), batch.id());
            }
        } catch (RuntimeException failure) {
            // The row stays claimed: once the claim goes stale the batch is handed over again rather than lost.
            log.error("event=trade_return_finish_failed player=" + owner.uuid() + " id=" + batch.id(), failure);
        }
    }

    private void release(PlayerRef owner, TradeItemReturns.Kept batch, String claimant) {
        try {
            returns.release(batch.id(), claimant);
        } catch (RuntimeException failure) {
            // The row stays claimed and is handed over once the claim goes stale.
            log.error("event=trade_return_release_failed player=" + owner.uuid() + " id=" + batch.id(), failure);
        }
    }

    private void offTick(Runnable storageWork) {
        if (!running.getAsBoolean()) {
            storageWork.run();
            return;
        }
        scheduler.async(storageWork);
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
