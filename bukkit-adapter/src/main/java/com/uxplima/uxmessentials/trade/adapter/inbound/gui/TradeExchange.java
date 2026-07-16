package com.uxplima.uxmessentials.trade.adapter.inbound.gui;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.trade.domain.TradeId;
import com.uxplima.uxmessentials.trade.domain.TradeSession;
import com.uxplima.uxmessentials.trade.domain.TradeSide;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The mutable coordinator shared by the two views of one trade: it owns the current {@link TradeSession}, the latest
 * offered-item snapshot each side placed, and the two {@link TradeHolder}s the views render into. Both participants'
 * region threads reach the same instance, so every read and write of the session and the snapshots runs under this
 * object's monitor; the offered stacks themselves are read and delivered outside the lock (no blocking I/O is held).
 *
 * <p>The {@code settled} flag is the single-winner gate: whichever path — a both-confirm commit, a window close, a
 * disconnect, a world change, or a plugin stop — first flips it owns the settlement and returns or swaps the items;
 * every later path sees the flag already set and does nothing, so no stack is delivered twice.
 */
@NullMarked
final class TradeExchange {

    /** The result of a confirm click, telling the view whether to re-render or run the swap. */
    enum Outcome {
        CHANGED,
        COMMITTED,
        IGNORED
    }

    private final TradeHolder initiatorHolder;
    private final TradeHolder partnerHolder;
    private final AtomicBoolean settled = new AtomicBoolean(false);

    private TradeSession session;
    private @Nullable ItemStack @Nullable [] initiatorOffer;
    private @Nullable ItemStack @Nullable [] partnerOffer;

    TradeExchange(TradeSession session, TradeHolder initiatorHolder, TradeHolder partnerHolder) {
        this.session = Objects.requireNonNull(session, "session");
        this.initiatorHolder = Objects.requireNonNull(initiatorHolder, "initiatorHolder");
        this.partnerHolder = Objects.requireNonNull(partnerHolder, "partnerHolder");
    }

    TradeId id() {
        return initiatorHolder.tradeId();
    }

    TradeHolder holder(TradeSide side) {
        return side == TradeSide.INITIATOR ? initiatorHolder : partnerHolder;
    }

    PlayerRef participant(TradeSide side) {
        return holder(side).viewer();
    }

    /** Store {@code side}'s freshly-read offer and re-stake it, which clears both confirmations. A no-op if settled. */
    synchronized void applyOffer(TradeSide side, @Nullable ItemStack[] offer) {
        if (session.state().isTerminal()) {
            return;
        }
        if (side == TradeSide.INITIATOR) {
            initiatorOffer = offer;
        } else {
            partnerOffer = offer;
        }
        session = session.withOffer(side, TradeItemCodec.toOffer(offer));
    }

    /** Mark {@code side} confirmed; when both sides agree, win the settle race and arm the swap. */
    synchronized Outcome confirm(TradeSide side) {
        if (session.state().isTerminal()) {
            return Outcome.IGNORED;
        }
        session = session.confirm(side);
        if (!session.bothConfirmed()) {
            return Outcome.CHANGED;
        }
        if (!settled.compareAndSet(false, true)) {
            return Outcome.IGNORED;
        }
        session = session.ready().commit();
        return Outcome.COMMITTED;
    }

    /** Claim the settlement for a cancel path; only the first caller wins, so items are returned exactly once. */
    boolean beginCancel() {
        return settled.compareAndSet(false, true);
    }

    /** Move the session to {@code CANCELLED} once a cancel path has won {@link #beginCancel()}. */
    synchronized void markCancelled() {
        if (!session.state().isTerminal()) {
            session = session.cancel();
        }
    }

    synchronized TradeSession session() {
        return session;
    }

    synchronized boolean confirmed(TradeSide side) {
        return session.confirmed(side);
    }

    synchronized @Nullable ItemStack @Nullable [] offer(TradeSide side) {
        return side == TradeSide.INITIATOR ? initiatorOffer : partnerOffer;
    }
}
