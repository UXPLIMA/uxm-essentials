package com.uxplima.uxmessentials.trade.adapter.inbound.gui;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.trade.application.TradeConfig;
import com.uxplima.uxmessentials.trade.application.TradeMessageKey;
import com.uxplima.uxmessentials.trade.domain.TradeId;
import com.uxplima.uxmessentials.trade.domain.TradeSession;
import com.uxplima.uxmessentials.trade.domain.TradeSide;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Builds and drives the two live inventory views of one same-server trade over the shared {@link TradeExchange}. Each
 * participant opens their own six-row window ({@link TradeLayout}): their editable offer on the left, the counterpart's
 * offer mirrored read-only on the right, and a confirm button. Placing or removing an item re-reads that side's offer
 * into the domain {@link TradeSession} (which clears both confirmations — the anti-scam invariant) and re-renders both
 * views live, so the other player sees the change and both confirms reset. When both sides confirm with no pending
 * change the swap runs: each player receives the other's stacks, with any overflow dropped at their feet rather than
 * deleted.
 *
 * <p>Item safety is absolute. An offered stack physically sits in the placing player's window, out of their inventory;
 * every terminal path — a commit, a window close, a disconnect, a world change, or a plugin stop — either swaps or
 * returns those stacks exactly once, gated by the exchange's single-winner settle flag. Every live-player touch hops to
 * that player's region thread through the injected {@link Scheduler}, so the flow is Folia-safe.
 */
@NullMarked
public final class TradeView {

    private final Messages messages;
    private final MessageSink messageSink;
    private final Scheduler scheduler;
    private final TradeLayout layout;
    private final TradeSessions sessions;

    public TradeView(
            Messages messages,
            MessageSink messageSink,
            Scheduler scheduler,
            TradeConfig config,
            TradeSessions sessions) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.messageSink = Objects.requireNonNull(messageSink, "messageSink");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(config, "config");
        this.layout = new TradeLayout(config.slotsPerSide());
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    /** The window listener bound to this view and its layout — the wiring registers it and this view drives it. */
    public TradeListener newListener() {
        return new TradeListener(this, layout);
    }

    /**
     * Schedule an offer re-read on {@code holder}'s owner's region thread. The listener calls this after an allowed
     * click or drag; the hop lands the read on the next tick, once Bukkit has applied the interaction to the window.
     */
    void scheduleSync(TradeHolder holder) {
        Objects.requireNonNull(holder, "holder");
        scheduler.onEntity(holder.viewer(), () -> syncOffer(holder));
    }

    /** Open a fresh trade window between two distinct, online players, unless either is already trading. */
    public void open(Player a, Player b) {
        Objects.requireNonNull(a, "a");
        Objects.requireNonNull(b, "b");
        if (a.getUniqueId().equals(b.getUniqueId())
                || sessions.isTrading(a.getUniqueId())
                || sessions.isTrading(b.getUniqueId())) {
            return;
        }
        PlayerRef aRef = ref(a);
        PlayerRef bRef = ref(b);
        TradeId id = TradeId.newId();
        TradeExchange exchange = new TradeExchange(
                TradeSession.open(id, aRef, bRef),
                new TradeHolder(id, TradeSide.INITIATOR, aRef),
                new TradeHolder(id, TradeSide.PARTNER, bRef));
        sessions.register(exchange);
        openOne(exchange, TradeSide.INITIATOR);
        openOne(exchange, TradeSide.PARTNER);
    }

    /** Re-read {@code holder}'s side after a placement or removal and re-render both views. */
    void syncOffer(TradeHolder holder) {
        TradeExchange exchange = sessions.find(holder.tradeId());
        if (exchange == null) {
            return;
        }
        Inventory inv = holder.inventoryOrNull();
        if (inv == null) {
            return;
        }
        exchange.applyOffer(holder.side(), layout.readOffer(inv));
        rerender(exchange);
    }

    /** Handle a confirm click: run the swap once both sides agree, otherwise re-render the reset controls. */
    void confirm(TradeHolder holder) {
        TradeExchange exchange = sessions.find(holder.tradeId());
        if (exchange == null) {
            return;
        }
        switch (exchange.confirm(holder.side())) {
            case COMMITTED -> commit(exchange);
            case CHANGED -> rerender(exchange);
            case IGNORED -> {}
        }
    }

    /** A window closed — cancel the trade (if still live) and return both sides' items to their owners. */
    void onClose(TradeHolder holder) {
        TradeExchange exchange = sessions.find(holder.tradeId());
        if (exchange != null) {
            cancel(exchange);
        }
    }

    /** A participant changed world — treat it like a close, returning both sides' items. */
    void onLeave(UUID player) {
        TradeExchange exchange = sessions.find(player);
        if (exchange != null) {
            cancel(exchange);
        }
    }

    /** A participant disconnected — return the quitter's items inline (their thread is theirs now) and the other's async. */
    void onQuit(Player quitter) {
        TradeExchange exchange = sessions.find(quitter.getUniqueId());
        if (exchange == null || !exchange.beginCancel()) {
            return;
        }
        exchange.markCancelled();
        sessions.remove(exchange);
        TradeSide side = exchange.participant(TradeSide.INITIATOR).uuid().equals(quitter.getUniqueId())
                ? TradeSide.INITIATOR
                : TradeSide.PARTNER;
        deliver(quitter, TradeItemCodec.stacks(exchange.offer(side)));
        deliverAndClose(exchange, side.other(), exchange.offer(side.other()));
        notifyBoth(exchange, TradeMessageKey.TRADE_CANCELLED);
    }

    /** Drain every live trade on module stop or reload, returning all offered items. */
    public void closeAll() {
        for (TradeExchange exchange : sessions.all()) {
            cancel(exchange);
        }
    }

    private void openOne(TradeExchange exchange, TradeSide side) {
        PlayerRef viewer = exchange.participant(side);
        PlayerRef other = exchange.participant(side.other());
        scheduler.onEntity(viewer, () -> {
            Player live = Bukkit.getPlayer(viewer.uuid());
            if (live == null || !live.isOnline()) {
                return;
            }
            TradeHolder holder = exchange.holder(side);
            Inventory inv = Bukkit.createInventory(holder, TradeLayout.SIZE, title(viewer, other));
            holder.attach(inv);
            layout.seedFrame(inv);
            renderView(inv, viewer, exchange.offer(side.other()), false, false);
            live.openInventory(inv);
        });
    }

    private void rerender(TradeExchange exchange) {
        for (TradeSide side : TradeSide.values()) {
            renderSide(exchange, side);
        }
    }

    private void renderSide(TradeExchange exchange, TradeSide side) {
        TradeHolder holder = exchange.holder(side);
        PlayerRef viewer = holder.viewer();
        @Nullable ItemStack @Nullable [] otherOffer = exchange.offer(side.other());
        boolean self = exchange.confirmed(side);
        boolean partner = exchange.confirmed(side.other());
        scheduler.onEntity(viewer, () -> {
            Inventory inv = holder.inventoryOrNull();
            if (inv != null) {
                renderView(inv, viewer, otherOffer, self, partner);
            }
        });
    }

    private void renderView(
            Inventory inv,
            PlayerRef viewer,
            @Nullable ItemStack @Nullable [] otherOffer,
            boolean self,
            boolean partner) {
        layout.renderMirror(inv, otherOffer);
        layout.renderControls(inv, messages, viewer, self, partner);
    }

    private void commit(TradeExchange exchange) {
        sessions.remove(exchange);
        deliverAndClose(exchange, TradeSide.INITIATOR, exchange.offer(TradeSide.PARTNER));
        deliverAndClose(exchange, TradeSide.PARTNER, exchange.offer(TradeSide.INITIATOR));
        notifyBoth(exchange, TradeMessageKey.TRADE_COMPLETED);
    }

    private void cancel(TradeExchange exchange) {
        if (!exchange.beginCancel()) {
            return;
        }
        exchange.markCancelled();
        sessions.remove(exchange);
        deliverAndClose(exchange, TradeSide.INITIATOR, exchange.offer(TradeSide.INITIATOR));
        deliverAndClose(exchange, TradeSide.PARTNER, exchange.offer(TradeSide.PARTNER));
        notifyBoth(exchange, TradeMessageKey.TRADE_CANCELLED);
    }

    private void deliverAndClose(TradeExchange exchange, TradeSide side, @Nullable ItemStack @Nullable [] incoming) {
        TradeHolder holder = exchange.holder(side);
        PlayerRef viewer = holder.viewer();
        List<ItemStack> gifts = TradeItemCodec.stacks(incoming);
        scheduler.onEntity(viewer, () -> {
            Inventory inv = holder.inventoryOrNull();
            if (inv != null) {
                layout.clearOffer(inv);
            }
            Player live = Bukkit.getPlayer(viewer.uuid());
            if (live != null && live.isOnline()) {
                deliver(live, gifts);
                live.closeInventory();
            }
        });
    }

    private void deliver(Player recipient, List<ItemStack> gifts) {
        if (gifts.isEmpty()) {
            return;
        }
        Map<Integer, ItemStack> overflow = recipient.getInventory().addItem(gifts.toArray(ItemStack[]::new));
        for (ItemStack extra : overflow.values()) {
            recipient.getWorld().dropItemNaturally(recipient.getLocation(), extra);
        }
    }

    private void notifyBoth(TradeExchange exchange, TradeMessageKey key) {
        for (TradeSide side : TradeSide.values()) {
            PlayerRef viewer = exchange.participant(side);
            messageSink.deliver(viewer, messages.resolve(viewer, key, Map.of()));
        }
    }

    private Component title(PlayerRef viewer, PlayerRef other) {
        return StyledText.render(
                messages.resolve(viewer, TradeMessageKey.TRADE_WINDOW_TITLE, Map.of("player", other.name())));
    }

    private static PlayerRef ref(Player player) {
        return new PlayerRef(player.getUniqueId(), player.getName());
    }
}
