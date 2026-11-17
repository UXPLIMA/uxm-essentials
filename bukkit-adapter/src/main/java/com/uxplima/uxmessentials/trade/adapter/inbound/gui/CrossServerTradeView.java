package com.uxplima.uxmessentials.trade.adapter.inbound.gui;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.adapter.outbound.lookup.OnlineByName;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.trade.adapter.outbound.TradeItemBytes;
import com.uxplima.uxmessentials.trade.application.CrossServerInvites;
import com.uxplima.uxmessentials.trade.application.CrossServerTrade;
import com.uxplima.uxmessentials.trade.application.TradeSignal;
import com.uxplima.uxmessentials.trade.application.TradeSignalType;
import com.uxplima.uxmessentials.trade.application.port.TradeBus;
import com.uxplima.uxmessentials.trade.application.port.TradePresence;
import com.uxplima.uxmessentials.trade.domain.TradeMessageKey;
import com.uxplima.uxmlib.menu.binding.MenuBindings;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Drives the player-facing half of a cross-server trade: the invite step over the bus and each side's own solo offer
 * window. A {@code /trade} to a player on another backend sends an {@code INVITE}; the target's backend shows it as a
 * line to accept or deny and answers whether it arrived, and nothing opens until the target accepts. The accept claims
 * the target's side and answers {@code ACCEPT}; the sender's backend opens its own window and answers {@code OPENED},
 * and only then does the target's window open, so neither side can stake into a trade the other never joined. The invite bookkeeping on
 * both ends is {@link CrossServerInvites}; this class turns its outcomes into lines and windows.
 *
 * <p>Each participant stakes items in their own {@link CrossTradeWindow} (items-only in v1) and confirms; confirming
 * reads the live window and escrows the side through the {@link CrossServerTrade} coordinator, which runs the
 * two-phase commit and delivers the counterpart's goods. A close before confirming returns the items and aborts both
 * sides. Bus signals arrive off-tick, so lookups run on the global thread and every window and inventory touch hops to
 * the player's own entity thread through the injected {@link Scheduler}, which keeps the flow Folia-safe.
 */
@NullMarked
public final class CrossServerTradeView {

    /**
     * The sweep runs a little after each deadline: the scheduler's delay and the clock the invites read are separate
     * timers, and a sweep that lands a hair early would leave the invite waiting until the next one.
     */
    private static final Duration SWEEP_MARGIN = Duration.ofSeconds(1);

    private final Messages messages;
    private final MessageSink messageSink;
    private final Scheduler scheduler;
    private final CrossServerTrade coordinator;
    private final TradeBus bus;
    private final CrossTradeWindow window;
    private final TradeSessions localSessions;
    private final CrossServerInvites invites;
    private final PlayerLookup players;
    private final ConcurrentHashMap<UUID, CrossTradeHolder> sessions = new ConcurrentHashMap<>();

    /**
     * @param localSessions the same-server trades, so a player in one counts as busy for an invite
     * @param invites builds the invite bookkeeping over this backend's view of its own players
     */
    public CrossServerTradeView(
            Messages messages,
            MessageSink messageSink,
            Scheduler scheduler,
            CrossServerTrade coordinator,
            TradeBus bus,
            CrossTradeWindow window,
            TradeSessions localSessions,
            PlayerLookup players,
            Function<TradePresence, CrossServerInvites> invites) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.messageSink = Objects.requireNonNull(messageSink, "messageSink");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.coordinator = Objects.requireNonNull(coordinator, "coordinator");
        this.bus = Objects.requireNonNull(bus, "bus");
        this.window = Objects.requireNonNull(window, "window");
        this.localSessions = Objects.requireNonNull(localSessions, "localSessions");
        this.players = Objects.requireNonNull(players, "players");
        this.invites = Objects.requireNonNull(invites, "invites").apply(new LivePresence());
    }

    /** Register the window's spec and its bindings; the wiring calls this once, before the first invite. */
    public void register(MenuBindings bindings) {
        window.register(bindings, this);
    }

    /** The lifecycle listener bound to this view: the wiring registers it. */
    public CrossServerTradeListener newListener() {
        return new CrossServerTradeListener(this);
    }

    /** Whether {@code player} is already in a cross-server trade: the {@code /trade} busy check reads it. */
    public boolean isTrading(UUID player) {
        return sessions.containsKey(player);
    }

    /** The open window {@code player} is staking into, or {@code null} when they are not in a cross-server trade. */
    @Nullable CrossTradeHolder session(UUID player) {
        return sessions.get(player);
    }

    /**
     * Send a cross-server trade invite to a player named {@code targetName} on another backend. The invite starts the
     * sender's cooldown, which is given back if it turns out to reach nobody.
     */
    public void invite(Player sender, String targetName) {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(targetName, "targetName");
        PlayerRef from = ref(sender);
        invites.invite(from, targetName);
        notify(from, TradeMessageKey.TRADE_CROSS_SERVER_REQUEST_SENT, Map.of("player", targetName));
        for (Duration deadline : invites.deadlines()) {
            scheduler.asyncAfter(deadline.plus(SWEEP_MARGIN), this::sweep);
        }
    }

    /**
     * {@code target} accepts a waiting cross-server invite, from {@code inviterName} or the newest. Their side is
     * claimed before the accept goes out, and their window opens once the inviter's backend reports its own side open.
     * Returns false when no such invite is waiting, so the caller can report that no request exists at all.
     */
    public boolean accept(Player target, @Nullable String inviterName) {
        Objects.requireNonNull(target, "target");
        CrossServerInvites.Answer answer = invites.accept(target.getUniqueId(), inviterName, this::claim);
        PlayerRef local = ref(target);
        switch (answer.status()) {
            case NONE -> {
                return false;
            }
            case TRADING -> notify(local, TradeMessageKey.TRADE_ALREADY_TRADING, Map.of());
            case EXPIRED -> notifyExpired(local, answered(answer));
            case MATCHED -> {
                notify(
                        local,
                        TradeMessageKey.TRADE_ACCEPTED,
                        Map.of("player", answered(answer).inviter().name()));
                scheduler.asyncAfter(CrossServerInvites.ANSWER_GRACE.plus(SWEEP_MARGIN), this::sweep);
            }
        }
        return true;
    }

    /** {@code target} denies a waiting cross-server invite; false when no such invite is waiting. */
    public boolean deny(Player target, @Nullable String inviterName) {
        Objects.requireNonNull(target, "target");
        CrossServerInvites.Answer answer = invites.deny(target.getUniqueId(), inviterName);
        PlayerRef local = ref(target);
        switch (answer.status()) {
            case NONE -> {
                return false;
            }
            case TRADING -> notify(local, TradeMessageKey.TRADE_ALREADY_TRADING, Map.of());
            case EXPIRED -> notifyExpired(local, answered(answer));
            case MATCHED ->
                notify(
                        local,
                        TradeMessageKey.TRADE_DENIED,
                        Map.of("player", answered(answer).inviter().name()));
        }
        return true;
    }

    /** The names of everyone with a cross-server invite waiting for {@code target}, for accept and deny completion. */
    public List<String> pendingInviterNames(UUID target) {
        return invites.pendingInviterNames(target);
    }

    /**
     * Route an inbound rendezvous signal; escrow signals are handled by the coordinator, not here. Every rendezvous
     * signal, the abort included, runs on the global thread: lookups by name belong there, and one queue keeps an
     * abort from being handled on another thread at the same moment as the accept it cancels. Frames can still
     * arrive out of order, which the invite book's tombstones and the claim gates below absorb.
     */
    public void onSignal(TradeSignal signal) {
        Objects.requireNonNull(signal, "signal");
        switch (signal.type()) {
            case INVITE -> scheduler.onGlobal(() -> invites.onInvite(signal).ifPresent(this::showInvite));
            case DELIVERED, NOT_FOUND, BUSY, DECLINE, ACCEPT ->
                scheduler.onGlobal(() -> invites.onReply(signal).ifPresent(this::report));
            case OPENED -> scheduler.onGlobal(() -> invites.onOpened(signal).ifPresent(this::openAccepted));
            case ABORT ->
                scheduler.onGlobal(() -> {
                    invites.onAbort(signal).ifPresent(this::report);
                    onAbort(signal);
                });
            case COMMIT -> notify(signal.to(), TradeMessageKey.TRADE_CROSS_SERVER_COMPLETED, Map.of());
            case READY -> {
                // READY is the coordinator's.
            }
        }
    }

    /** Settle the invites whose wait is over and tell each player, and give up accepts the inviter never opened. */
    private void sweep() {
        invites.expire().forEach(this::report);
        invites.expireAccepts().forEach(this::releaseAbandoned);
    }

    private void showInvite(CrossServerInvites.Incoming invite) {
        notify(
                invite.target(),
                TradeMessageKey.TRADE_CROSS_SERVER_INCOMING,
                Map.of("player", invite.inviter().name()));
    }

    private void report(CrossServerInvites.Reply reply) {
        switch (reply) {
            case CrossServerInvites.Accepted accepted -> openInviter(accepted);
            case CrossServerInvites.AcceptedWhileBusy busy -> notifyAcceptedBusy(busy.inviter(), busy.target());
            case CrossServerInvites.Denied denied ->
                notify(
                        denied.inviter(),
                        TradeMessageKey.TRADE_CROSS_SERVER_DENIED,
                        Map.of("player", denied.target().name()));
            case CrossServerInvites.Busy busy ->
                notify(
                        busy.inviter(),
                        TradeMessageKey.TRADE_TARGET_BUSY,
                        Map.of("player", busy.target().name()));
            case CrossServerInvites.NotFound notFound ->
                notify(
                        notFound.inviter(),
                        TradeMessageKey.TRADE_CROSS_SERVER_NOT_FOUND,
                        Map.of("player", notFound.targetName()));
            case CrossServerInvites.Expired expired ->
                notify(
                        expired.inviter(),
                        TradeMessageKey.TRADE_REQUEST_EXPIRED,
                        Map.of("player", expired.targetName()));
            case CrossServerInvites.CalledOff calledOff ->
                notify(
                        calledOff.inviter(),
                        TradeMessageKey.TRADE_CROSS_SERVER_CALLED_OFF,
                        Map.of("player", calledOff.target().name()));
            case CrossServerInvites.TimedOut timedOut ->
                notify(
                        timedOut.inviter(),
                        TradeMessageKey.TRADE_CROSS_SERVER_TIMEOUT,
                        Map.of("player", timedOut.targetName()));
        }
    }

    private void onAbort(TradeSignal signal) {
        CrossTradeHolder holder = sessions.get(signal.to().uuid());
        if (holder != null && holder.tradeId().equals(signal.tradeId())) {
            // The peer already gave up, so this side is only returned and closed; telling them again would be noise.
            abort(holder, false);
        }
    }

    /** Reserve the target's side of an invite they accept; false when they already hold another trade's side. */
    private boolean claim(CrossServerInvites.Incoming invite) {
        CrossTradeHolder holder =
                new CrossTradeHolder(invite.tradeId(), invite.target(), invite.inviter(), invite.inviterServer());
        return sessions.putIfAbsent(invite.target().uuid(), holder) == null;
    }

    /**
     * The target accepted and their side is free: claim the inviter's side, open their window on their own entity
     * thread, and only then tell the target's backend to open its window. A player who meanwhile took another trade's
     * side keeps it; this one is withdrawn and they are told who accepted.
     */
    private void openInviter(CrossServerInvites.Accepted accepted) {
        CrossTradeHolder holder = new CrossTradeHolder(
                accepted.tradeId(), accepted.inviter(), accepted.target(), accepted.targetServer());
        if (sessions.putIfAbsent(accepted.inviter().uuid(), holder) != null) {
            sendAbort(holder);
            notifyAcceptedBusy(accepted.inviter(), accepted.target());
            return;
        }
        scheduler.onEntity(
                accepted.inviter(),
                () -> {
                    if (!holder.escrowed()) {
                        window.open(holder);
                        invites.opened(accepted);
                    }
                },
                () -> abandon(holder));
    }

    /** The inviter's side is open: open the target's window for the side claimed when they accepted. */
    private void openAccepted(CrossServerInvites.Incoming invite) {
        CrossTradeHolder holder = sessions.get(invite.target().uuid());
        if (holder == null || !holder.tradeId().equals(invite.tradeId())) {
            return;
        }
        scheduler.onEntity(
                invite.target(),
                () -> {
                    if (!holder.escrowed()) {
                        window.open(holder);
                    }
                },
                () -> abandon(holder));
    }

    /** The inviter's side never opened in time; the invite book already withdrew it, so only release the target. */
    private void releaseAbandoned(CrossServerInvites.Incoming invite) {
        CrossTradeHolder holder = sessions.get(invite.target().uuid());
        if (holder != null && holder.tradeId().equals(invite.tradeId()) && holder.beginEscrow()) {
            sessions.remove(invite.target().uuid(), holder);
            notify(invite.target(), TradeMessageKey.TRADE_CANCELLED, Map.of());
        }
    }

    /** The player left before their window could open: drop their side and tell the peer. */
    private void abandon(CrossTradeHolder holder) {
        if (holder.beginEscrow()) {
            sessions.remove(holder.local().uuid(), holder);
            sendAbort(holder);
        }
    }

    private void sendAbort(CrossTradeHolder holder) {
        bus.send(new TradeSignal(
                holder.tradeId(), TradeSignalType.ABORT, holder.local(), holder.remote(), bus.localServer()));
    }

    private void notifyAcceptedBusy(PlayerRef inviter, PlayerRef target) {
        notify(inviter, TradeMessageKey.TRADE_CROSS_SERVER_ACCEPTED_BUSY, Map.of("player", target.name()));
    }

    private static CrossServerInvites.Incoming answered(CrossServerInvites.Answer answer) {
        return Objects.requireNonNull(answer.invite(), "a matched or expired answer carries its invite");
    }

    /** Whether the local player may still move items in their window: only until the side is escrowed or returned. */
    boolean acceptsItems(CrossTradeHolder holder) {
        return !holder.escrowed();
    }

    /** A confirm click: read the live window, escrow the items, and close; the coordinator settles from here. */
    void confirm(CrossTradeHolder holder) {
        if (!holder.beginEscrow()) {
            return;
        }
        sessions.remove(holder.local().uuid(), holder);
        List<ItemStack> staked = window.live(holder.local())
                .map(inv -> {
                    List<ItemStack> read = TradeItemCodec.stacks(window.readOffer(inv));
                    window.clearOffer(inv);
                    return read;
                })
                .orElse(List.of());
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

    /**
     * A window closed before confirming: return what was staked in it and abort the other side. The escrow gate wins
     * here only when no confirm ran first: a plain close is the real abort and takes this path exactly once, while
     * the close a confirm causes loses the gate (the confirm already claimed it) and does nothing, so it never fires
     * a spurious {@code ABORT} that would tear the counterpart down. The items come from the window itself rather
     * than from any snapshot, so a stack placed in the closing tick is still returned.
     */
    void onWindowClosed(CrossTradeHolder holder, List<@Nullable ItemStack> contents) {
        if (!holder.beginEscrow()) {
            return;
        }
        sessions.remove(holder.local().uuid(), holder);
        deliver(holder.local(), TradeItemCodec.stacks(contents.toArray(new ItemStack[0])));
        notify(holder.local(), TradeMessageKey.TRADE_CANCELLED, Map.of());
        bus.send(new TradeSignal(
                holder.tradeId(), TradeSignalType.ABORT, holder.local(), holder.remote(), bus.localServer()));
    }

    /**
     * End this side from outside its window (the peer aborted, the player quit, or the module is stopping) by
     * reading whatever is still staked back out of the live window and returning it. {@code tellPeer} is false only
     * when the peer is the one who aborted, since they need no telling.
     */
    private void abort(CrossTradeHolder holder, boolean tellPeer) {
        if (!holder.beginEscrow()) {
            return;
        }
        sessions.remove(holder.local().uuid(), holder);
        returnAndClose(holder);
        notify(holder.local(), TradeMessageKey.TRADE_CANCELLED, Map.of());
        if (tellPeer) {
            sendAbort(holder);
        }
    }

    /**
     * Drain every open cross-server trade window on module stop or reload: return each side's still-staked items to the
     * player and signal the peer to abort, exactly as a plain close does. Mirrors the same-server {@code TradeView.closeAll}
     * so a disable or {@code /uxmess reload trade} never drops the transient window's items. Snapshots the open holders
     * first because each abort removes from {@link #sessions} as it drains.
     */
    public void flushAll() {
        for (CrossTradeHolder holder : List.copyOf(sessions.values())) {
            abort(holder, true);
        }
    }

    /** The window a player quit belonged to a cross-server trade, treat the disconnect as a close. */
    public void onQuit(Player quitter) {
        invites.forget(quitter.getUniqueId());
        CrossTradeHolder holder = sessions.get(quitter.getUniqueId());
        if (holder != null) {
            abort(holder, true);
        }
    }

    /**
     * Empty the live window into the player's hands and shut it, on their own region thread. A side that never got
     * its window (still waiting for the inviter) leaves whatever the player has open alone.
     */
    private void returnAndClose(CrossTradeHolder holder) {
        PlayerRef viewer = holder.local();
        scheduler.onEntity(viewer, () -> {
            boolean windowOpen = window.live(viewer).isPresent();
            List<ItemStack> staked = window.live(viewer)
                    .map(inv -> {
                        List<ItemStack> read = TradeItemCodec.stacks(window.readOffer(inv));
                        window.clearOffer(inv);
                        return read;
                    })
                    .orElse(List.of());
            deliverNow(viewer, staked);
            Player live = Bukkit.getPlayer(viewer.uuid());
            if (windowOpen && live != null && live.isOnline()) {
                live.closeInventory();
            }
        });
    }

    /** Hand {@code stacks} to {@code viewer} on their own region thread, dropping any overflow at their feet. */
    private void deliver(PlayerRef viewer, List<ItemStack> stacks) {
        scheduler.onEntity(viewer, () -> deliverNow(viewer, stacks));
    }

    private void deliverNow(PlayerRef viewer, List<ItemStack> stacks) {
        Player live = Bukkit.getPlayer(viewer.uuid());
        if (live == null || !live.isOnline() || stacks.isEmpty()) {
            return;
        }
        for (ItemStack stack : stacks) {
            live.getInventory()
                    .addItem(stack)
                    .values()
                    .forEach(extra -> live.getWorld().dropItemNaturally(live.getLocation(), extra));
        }
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

    private void notifyExpired(PlayerRef who, CrossServerInvites.Incoming invite) {
        notify(
                who,
                TradeMessageKey.TRADE_REQUEST_EXPIRED,
                Map.of("player", invite.inviter().name()));
    }

    private void notify(PlayerRef who, TradeMessageKey key, Map<String, String> placeholders) {
        messageSink.deliver(who, messages.resolve(who, key, placeholders));
    }

    private static PlayerRef ref(Player player) {
        return new PlayerRef(player.getUniqueId(), player.getName());
    }

    /** This backend's players as the invite step sees them: online by name, and busy in either kind of trade. */
    private final class LivePresence implements TradePresence {

        @Override
        public Optional<PlayerRef> onlineByName(String name) {
            Player player = OnlineByName.player(players, name);
            return player != null && player.isOnline() ? Optional.of(ref(player)) : Optional.empty();
        }

        @Override
        public boolean isOnline(UUID player) {
            Player live = Bukkit.getPlayer(player);
            return live != null && live.isOnline();
        }

        @Override
        public boolean isTrading(UUID player) {
            return localSessions.isTrading(player) || sessions.containsKey(player);
        }
    }
}
