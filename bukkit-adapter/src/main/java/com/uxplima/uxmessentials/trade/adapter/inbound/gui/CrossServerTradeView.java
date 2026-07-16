package com.uxplima.uxmessentials.trade.adapter.inbound.gui;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
import com.uxplima.uxmessentials.trade.adapter.outbound.TradeItemBytes;
import com.uxplima.uxmessentials.trade.application.CrossServerTrade;
import com.uxplima.uxmessentials.trade.application.TradeConfig;
import com.uxplima.uxmessentials.trade.application.TradeMessageKey;
import com.uxplima.uxmessentials.trade.application.TradeSignal;
import com.uxplima.uxmessentials.trade.application.TradeSignalType;
import com.uxplima.uxmessentials.trade.application.port.TradeBus;
import com.uxplima.uxmessentials.trade.domain.TradeId;
import org.jspecify.annotations.NullMarked;

/**
 * Drives the player-facing half of a cross-server trade: the rendezvous over the bus and each side's own solo offer
 * window. A {@code /trade} to a player on another backend sends an {@code INVITE}; the target's backend opens a window
 * and answers {@code ACCEPT}; the sender's backend then opens its own. Each participant stakes items in their own
 * six-row window ({@link TradeLayout}, items-only in v1) and confirms; confirming reads the live window and escrows the
 * side through the {@link CrossServerTrade} coordinator, which runs the two-phase commit and delivers the counterpart's
 * goods. A close before confirming returns the items and aborts both sides; every player/inventory touch hops to the
 * player's region through the injected {@link Scheduler}, so the flow is Folia-safe.
 */
@NullMarked
public final class CrossServerTradeView {

    /** The placeholder id an {@code INVITE} carries for the not-yet-resolved target — matched by name, not uuid. */
    private static final UUID UNRESOLVED = new UUID(0L, 0L);

    private final Messages messages;
    private final MessageSink messageSink;
    private final Scheduler scheduler;
    private final CrossServerTrade coordinator;
    private final TradeBus bus;
    private final TradeLayout layout;
    private final ConcurrentHashMap<UUID, CrossTradeHolder> sessions = new ConcurrentHashMap<>();

    public CrossServerTradeView(
            Messages messages,
            MessageSink messageSink,
            Scheduler scheduler,
            CrossServerTrade coordinator,
            TradeBus bus,
            TradeConfig config) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.messageSink = Objects.requireNonNull(messageSink, "messageSink");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
        this.bus = Objects.requireNonNull(bus, "bus");
        this.layout = new TradeLayout(Objects.requireNonNull(config, "config").slotsPerSide(), List.of());
    }

    /** The window listener bound to this view — the wiring registers it. */
    public CrossServerTradeListener newListener() {
        return new CrossServerTradeListener(this, layout);
    }

    /** Whether {@code player} is already in a cross-server trade — the {@code /trade} busy check reads it. */
    public boolean isTrading(UUID player) {
        return sessions.containsKey(player);
    }

    /** Send a cross-server trade request to a player named {@code targetName} on another backend. */
    public void invite(Player sender, String targetName) {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(targetName, "targetName");
        PlayerRef from = ref(sender);
        bus.send(new TradeSignal(
                TradeId.newId(),
                TradeSignalType.INVITE,
                from,
                new PlayerRef(UNRESOLVED, targetName),
                bus.localServer()));
        notify(from, TradeMessageKey.TRADE_CROSS_SERVER_REQUEST_SENT, Map.of("player", targetName));
    }

    /** Route an inbound rendezvous signal; escrow signals are handled by the coordinator, not here. */
    public void onSignal(TradeSignal signal) {
        Objects.requireNonNull(signal, "signal");
        switch (signal.type()) {
            case INVITE -> scheduler.onGlobal(() -> onInvite(signal));
            case ACCEPT -> scheduler.onGlobal(() -> onAccept(signal));
            case ABORT -> onAbort(signal);
            case COMMIT -> notify(signal.to(), TradeMessageKey.TRADE_CROSS_SERVER_COMPLETED, Map.of());
            case READY, DECLINE -> {
                // READY is the coordinator's; DECLINE is folded into ABORT.
            }
        }
    }

    private void onInvite(TradeSignal signal) {
        Player target = Bukkit.getPlayerExact(signal.to().name());
        if (target == null || isTrading(target.getUniqueId())) {
            return;
        }
        PlayerRef local = ref(target);
        open(signal.tradeId(), local, signal.from(), signal.originServer());
        notify(
                local,
                TradeMessageKey.TRADE_CROSS_SERVER_INCOMING,
                Map.of("player", signal.from().name()));
        bus.send(new TradeSignal(signal.tradeId(), TradeSignalType.ACCEPT, local, signal.from(), bus.localServer()));
    }

    private void onAccept(TradeSignal signal) {
        Player sender = Bukkit.getPlayer(signal.to().uuid());
        if (sender == null || isTrading(sender.getUniqueId())) {
            return;
        }
        open(signal.tradeId(), ref(sender), signal.from(), signal.originServer());
    }

    private void onAbort(TradeSignal signal) {
        CrossTradeHolder holder = sessions.remove(signal.to().uuid());
        if (holder != null && holder.beginEscrow()) {
            returnAndClose(holder);
            notify(holder.local(), TradeMessageKey.TRADE_CANCELLED, Map.of());
        }
    }

    private void open(TradeId tradeId, PlayerRef local, PlayerRef remote, String remoteServer) {
        CrossTradeHolder holder = new CrossTradeHolder(tradeId, local, remote, remoteServer);
        sessions.put(local.uuid(), holder);
        scheduler.onEntity(local, () -> {
            Player live = Bukkit.getPlayer(local.uuid());
            if (live == null || !live.isOnline()) {
                sessions.remove(local.uuid());
                return;
            }
            Inventory inv = Bukkit.createInventory(holder, TradeLayout.SIZE, title(local, remote));
            holder.attach(inv);
            layout.seedFrame(inv);
            layout.renderMirror(inv, null);
            layout.renderControls(inv, messages, local, false, false);
            live.openInventory(inv);
        });
    }

    /** A confirm click — read the live window, escrow the items, and close; the coordinator settles from here. */
    void confirm(CrossTradeHolder holder) {
        if (!holder.beginEscrow()) {
            return;
        }
        sessions.remove(holder.local().uuid());
        Inventory inv = holder.inventoryOrNull();
        List<ItemStack> staked = inv == null ? List.of() : TradeItemCodec.stacks(layout.readOffer(inv));
        if (inv != null) {
            layout.clearOffer(inv);
        }
        closeWindow(holder);
        notify(
                holder.local(),
                TradeMessageKey.TRADE_CROSS_SERVER_ESCROWED,
                Map.of("player", holder.remote().name()));
        String itemData = TradeItemBytes.encode(staked);
        int count = TradeItemBytes.totalCount(staked);
        scheduler.async(() -> coordinator.escrow(
                holder.tradeId(), holder.local(), holder.remoteServer(), holder.remote(), itemData, count, Map.of()));
    }

    /** A window closed before confirming — return the staked items and abort the other side. */
    void onClose(CrossTradeHolder holder) {
        if (!holder.beginEscrow()) {
            sessions.remove(holder.local().uuid());
            returnItems(holder);
            notify(holder.local(), TradeMessageKey.TRADE_CANCELLED, Map.of());
            bus.send(new TradeSignal(
                    holder.tradeId(), TradeSignalType.ABORT, holder.local(), holder.remote(), bus.localServer()));
        }
    }

    /** The window a player quit belonged to a cross-server trade — treat the disconnect as a close. */
    public void onQuit(Player quitter) {
        CrossTradeHolder holder = sessions.get(quitter.getUniqueId());
        if (holder != null) {
            onClose(holder);
        }
    }

    private void returnAndClose(CrossTradeHolder holder) {
        returnItems(holder);
        closeWindow(holder);
    }

    private void returnItems(CrossTradeHolder holder) {
        PlayerRef viewer = holder.local();
        scheduler.onEntity(viewer, () -> {
            Inventory inv = holder.inventoryOrNull();
            Player live = Bukkit.getPlayer(viewer.uuid());
            if (inv == null || live == null || !live.isOnline()) {
                return;
            }
            for (ItemStack stack : TradeItemCodec.stacks(layout.readOffer(inv))) {
                live.getInventory()
                        .addItem(stack)
                        .values()
                        .forEach(extra -> live.getWorld().dropItemNaturally(live.getLocation(), extra));
            }
            layout.clearOffer(inv);
        });
    }

    private void closeWindow(CrossTradeHolder holder) {
        PlayerRef viewer = holder.local();
        scheduler.onEntity(viewer, () -> {
            Player live = Bukkit.getPlayer(viewer.uuid());
            if (live != null && live.isOnline()) {
                live.closeInventory();
            }
        });
    }

    private Component title(PlayerRef viewer, PlayerRef other) {
        return StyledText.render(
                messages.resolve(viewer, TradeMessageKey.TRADE_WINDOW_TITLE, Map.of("player", other.name())));
    }

    private void notify(PlayerRef who, TradeMessageKey key, Map<String, String> placeholders) {
        messageSink.deliver(who, messages.resolve(who, key, placeholders));
    }

    private static PlayerRef ref(Player player) {
        return new PlayerRef(player.getUniqueId(), player.getName());
    }
}
