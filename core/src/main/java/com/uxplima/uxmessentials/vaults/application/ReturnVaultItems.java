package com.uxplima.uxmessentials.vaults.application;

import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vaults.application.port.VaultItemHandBack;
import com.uxplima.uxmessentials.vaults.application.port.VaultItemReceiver;
import com.uxplima.uxmessentials.vaults.application.port.VaultItemReturns;
import com.uxplima.uxmessentials.vaults.domain.VaultContents;

/**
 * Gives vault items whose vault is gone back to the owner: into their inventory on their own thread when they are
 * online, and otherwise into {@link VaultItemReturns} for their next join. Nothing is dropped on the ground: what does
 * not fit the inventory is kept for the next join too, since a join point is often a crowded spawn.
 *
 * <p>On join every kept batch is claimed by deleting its row first, and only the server whose delete removed the row
 * hands it over, so a batch reaches the player exactly once across servers. A batch claimed but not given (the player
 * left first, the inventory was full, or the give failed) is kept again straight away.
 *
 * <p>Keeping writes to storage, so it runs on the async lane. Once the plugin is no longer running the scheduler runs
 * nothing more, and the items are kept on the calling thread instead; that only happens while the server stops.
 */
public final class ReturnVaultItems implements VaultItemHandBack {

    private final Scheduler scheduler;
    private final VaultItemReturns returns;
    private final VaultItemReceiver receiver;
    private final VaultNotifier notifier;
    private final Logger log;
    private final BooleanSupplier running;

    public ReturnVaultItems(
            Scheduler scheduler,
            VaultItemReturns returns,
            VaultItemReceiver receiver,
            VaultNotifier notifier,
            Logger log,
            BooleanSupplier running) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.returns = Objects.requireNonNull(returns, "returns");
        this.receiver = Objects.requireNonNull(receiver, "receiver");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.log = Objects.requireNonNull(log, "log");
        this.running = Objects.requireNonNull(running, "running");
    }

    @Override
    public void handBack(PlayerRef owner, VaultContents items) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(items, "items");
        if (items.isEmpty()) {
            return;
        }
        if (!running.getAsBoolean()) {
            keepNow(owner, items);
            return;
        }
        scheduler.onEntity(owner, () -> give(owner, items), () -> keepLater(owner, items));
    }

    /**
     * Hand {@code owner} whatever was kept for them while they were away. Reads storage, so it runs off the tick. A
     * read that fails leaves the rows where they are, so the next join tries again.
     */
    public void deliverKept(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        List<VaultItemReturns.Kept> kept;
        try {
            kept = returns.kept(owner.uuid());
        } catch (RuntimeException failure) {
            log.error("event=vault_return_read_failed player=" + owner.uuid(), failure);
            return;
        }
        for (VaultItemReturns.Kept batch : kept) {
            boolean claimed;
            try {
                claimed = returns.claim(batch.id());
            } catch (RuntimeException failure) {
                // The row stays, so the next join tries again.
                log.error("event=vault_return_claim_failed player=" + owner.uuid() + " id=" + batch.id(), failure);
                continue;
            }
            if (claimed) {
                handBack(owner, batch.items());
            }
        }
    }

    private void give(PlayerRef owner, VaultContents items) {
        Optional<VaultItemReceiver.Receipt> receipt;
        try {
            receipt = receiver.give(owner, items);
        } catch (RuntimeException failure) {
            // A receiver reports a partial give as leftover, so a throw means nothing went in and the batch is kept.
            log.error("event=vault_return_give_failed player=" + owner.uuid() + " (kept for the next join)", failure);
            keepLater(owner, items);
            return;
        }
        if (receipt.isEmpty()) {
            keepLater(owner, items);
            return;
        }
        VaultItemReceiver.Receipt given = receipt.get();
        if (given.given() > 0) {
            notifier.itemsReturned(owner, given.given());
        }
        if (!given.leftover().isEmpty()) {
            keepLater(owner, given.leftover());
            notifier.returnsKept(owner, given.leftoverCount());
        }
    }

    private void keepLater(PlayerRef owner, VaultContents items) {
        if (!running.getAsBoolean()) {
            keepNow(owner, items);
            return;
        }
        scheduler.async(() -> keepNow(owner, items));
    }

    private void keepNow(PlayerRef owner, VaultContents items) {
        try {
            returns.keep(owner.uuid(), items);
        } catch (RuntimeException failure) {
            // Storage is gone: the items are written to the log so an operator can give them back by hand.
            log.error(
                    "event=vault_return_keep_failed player=" + owner.uuid() + " items="
                            + items.payload()
                                    .map(Base64.getEncoder()::encodeToString)
                                    .orElse(""),
                    failure);
        }
    }
}
