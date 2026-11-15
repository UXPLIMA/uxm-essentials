package com.uxplima.uxmessentials.vaults.adapter.outbound;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vaults.application.VaultsMessageKey;
import com.uxplima.uxmessentials.vaults.application.port.VaultItemHandBack;
import com.uxplima.uxmessentials.vaults.application.port.VaultItemReturns;
import com.uxplima.uxmessentials.vaults.domain.VaultContents;
import org.jspecify.annotations.NullMarked;

/**
 * Gives the items of a deleted vault's window to the owner: into their inventory on their own thread (overflow dropped
 * at their feet) when they are online, otherwise into {@link VaultItemReturns} until their next join.
 *
 * <p>The live player is looked up inside the task that runs on the owner's thread, never captured beforehand: after a
 * relog a captured {@code Player} is a dead object, and items added to it would be lost. When the owner is gone by
 * then, or leaves before the task runs, the items are kept. Keeping writes to storage, so it runs on the async lane;
 * once the module is stopping the scheduler runs nothing more, so they are kept on the calling thread instead.
 */
@NullMarked
public final class BukkitVaultItemHandBack implements VaultItemHandBack {

    private final Scheduler scheduler;
    private final VaultItemReturns returns;
    private final Messages messages;
    private final MessageSink sink;
    private final Logger log;
    private volatile boolean stopping;

    public BukkitVaultItemHandBack(
            Scheduler scheduler, VaultItemReturns returns, Messages messages, MessageSink sink, Logger log) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.returns = Objects.requireNonNull(returns, "returns");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public void handBack(PlayerRef owner, VaultContents items) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(items, "items");
        if (items.isEmpty()) {
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
        List<VaultContents> kept;
        try {
            kept = returns.takeAll(owner.uuid());
        } catch (RuntimeException failure) {
            log.error("event=vault_return_read_failed player=" + owner.uuid(), failure);
            return;
        }
        kept.forEach(items -> handBack(owner, items));
    }

    /** From now on keep items on the calling thread, because the scheduler runs nothing more. */
    public void stop() {
        stopping = true;
    }

    private void give(PlayerRef owner, VaultContents items) {
        Player live = Bukkit.getPlayer(owner.uuid());
        if (live == null || !live.isOnline()) {
            keepLater(owner, items);
            return;
        }
        int given = 0;
        for (ItemStack stack : VaultItemCodec.decodeAll(items)) {
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            Map<Integer, ItemStack> overflow = live.getInventory().addItem(stack);
            overflow.values().forEach(extra -> live.getWorld().dropItemNaturally(live.getLocation(), extra));
            given += stack.getAmount();
        }
        if (given > 0) {
            sink.deliver(
                    owner,
                    messages.resolve(
                            owner, VaultsMessageKey.VAULT_DELETED_RETURNED, Map.of("count", Integer.toString(given))));
        }
    }

    private void keepLater(PlayerRef owner, VaultContents items) {
        if (stopping) {
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
                                    .map(java.util.Base64.getEncoder()::encodeToString)
                                    .orElse(""),
                    failure);
        }
    }
}
