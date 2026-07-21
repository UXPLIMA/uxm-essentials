package com.uxplima.uxmessentials.trade.adapter.inbound.gui;

import java.util.Objects;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.trade.domain.TradeId;
import com.uxplima.uxmessentials.trade.domain.TradeSide;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The {@link InventoryHolder} that tags one participant's view of a trade window, so {@link TradeListener} can
 * recognise a click, drag, or close as belonging to a trade (and never to a vanilla container the player happens to
 * have open) and route it to the shared exchange. The holder carries which trade it belongs to ({@link #tradeId}),
 * which {@link TradeSide} this view renders, and who is looking at it — enough for the listener and view to reach the
 * one shared {@link TradeExchange} and re-render or settle it.
 *
 * <p>The holder is created first and the menu is built against it; {@link #attach} then stores the built inventory so
 * {@link #getInventory()} can answer it, the way Bukkit's holder contract expects.
 */
@NullMarked
final class TradeHolder implements InventoryHolder {

    private final TradeId tradeId;
    private final TradeSide side;
    private final PlayerRef viewer;
    private @Nullable Inventory inventory;

    /**
     * Which allowed currency the single money button currently shows for this viewer. Our economy is multi-currency but
     * the AxTrade money slot is single, so a right-click advances this index and the money button re-renders the next
     * currency; it is per-viewer UI state, only ever touched on the viewer's own region thread.
     */
    private int selectedCurrency;

    TradeHolder(TradeId tradeId, TradeSide side, PlayerRef viewer) {
        this.tradeId = Objects.requireNonNull(tradeId, "tradeId");
        this.side = Objects.requireNonNull(side, "side");
        this.viewer = Objects.requireNonNull(viewer, "viewer");
    }

    /** The trade this view belongs to; the listener resolves the shared exchange from it. */
    TradeId tradeId() {
        return tradeId;
    }

    /** Which side of the trade this view renders and edits. */
    TradeSide side() {
        return side;
    }

    /** The player looking at this view; the render and every settlement are attributed to them. */
    PlayerRef viewer() {
        return viewer;
    }

    /** The index of the currency the money button currently shows for this viewer. */
    int selectedCurrency() {
        return selectedCurrency;
    }

    /** Advance the selected currency, wrapped into {@code count} allowed currencies (a no-op when {@code count <= 1}). */
    void cycleCurrency(int count) {
        if (count > 1) {
            selectedCurrency = Math.floorMod(selectedCurrency + 1, count);
        }
    }

    /** Store the built menu so the holder contract can answer {@link #getInventory()}. */
    void attach(Inventory built) {
        this.inventory = Objects.requireNonNull(built, "built");
    }

    /** The built menu, or {@code null} when the window was never opened (the player was offline at open). */
    @Nullable Inventory inventoryOrNull() {
        return inventory;
    }

    @Override
    public Inventory getInventory() {
        Inventory built = inventory;
        if (built == null) {
            throw new IllegalStateException("trade inventory not attached yet");
        }
        return built;
    }
}
