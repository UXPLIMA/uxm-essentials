package com.uxplima.uxmessentials.trade.adapter.inbound.gui;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.trade.domain.TradeId;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The {@link InventoryHolder} that tags one side's window of a cross-server trade, so {@link CrossServerTradeListener}
 * recognises a click, drag, or close as belonging to a cross-server trade (never to a local trade or a vanilla
 * container) and routes it to {@link CrossServerTradeView}. Unlike the two-player local {@code TradeHolder}, only one
 * of the two participants is on this backend, so the holder carries the local player, the remote counterparty, the
 * counterparty's backend id, and the trade id both backends agree on. The {@code escrowed} flag is the single-winner
 * gate that guarantees the local player's items are removed into escrow exactly once — a confirm and a
 * close-with-confirm can both fire, and only the first flips it.
 */
@NullMarked
final class CrossTradeHolder implements InventoryHolder {

    private final TradeId tradeId;
    private final PlayerRef local;
    private final PlayerRef remote;
    private final String remoteServer;
    private final AtomicBoolean escrowed = new AtomicBoolean(false);
    private @Nullable Inventory inventory;

    CrossTradeHolder(TradeId tradeId, PlayerRef local, PlayerRef remote, String remoteServer) {
        this.tradeId = Objects.requireNonNull(tradeId, "tradeId");
        this.local = Objects.requireNonNull(local, "local");
        this.remote = Objects.requireNonNull(remote, "remote");
        this.remoteServer = Objects.requireNonNull(remoteServer, "remoteServer");
    }

    TradeId tradeId() {
        return tradeId;
    }

    PlayerRef local() {
        return local;
    }

    PlayerRef remote() {
        return remote;
    }

    String remoteServer() {
        return remoteServer;
    }

    /** Claim the escrow for this side; only the first caller wins, so the items are staked exactly once. */
    boolean beginEscrow() {
        return escrowed.compareAndSet(false, true);
    }

    void attach(Inventory built) {
        this.inventory = Objects.requireNonNull(built, "built");
    }

    @Nullable Inventory inventoryOrNull() {
        return inventory;
    }

    @Override
    public Inventory getInventory() {
        Inventory built = inventory;
        if (built == null) {
            throw new IllegalStateException("cross-server trade inventory not attached yet");
        }
        return built;
    }
}
