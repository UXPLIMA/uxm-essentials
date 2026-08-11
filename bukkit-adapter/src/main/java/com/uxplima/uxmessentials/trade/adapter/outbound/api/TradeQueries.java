package com.uxplima.uxmessentials.trade.adapter.outbound.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.api.query.UxmTradeQuery;
import com.uxplima.uxmessentials.api.view.UxmTrade;
import com.uxplima.uxmessentials.trade.adapter.inbound.gui.TradeSessions;
import org.jspecify.annotations.NullMarked;

/**
 * The published trade query, over the same in-memory registry the {@code /trade} flow and the windows themselves
 * read.
 *
 * <p>Nothing here waits. A same-server trade is held in memory for exactly as long as its window is open, so every
 * answer is a map lookup rather than a database read, and the calling thread gets it straight back.
 *
 * <p>Only trades on this server are visible. A cross-server trade's other half lives on the backend the partner is
 * connected to, and reporting half a trade as if it were the whole one would be worse than reporting none of it.
 */
@NullMarked
public final class TradeQueries implements UxmTradeQuery {

    private final TradeSessions sessions;

    public TradeQueries(TradeSessions sessions) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    @Override
    public boolean isTrading(UUID playerId) {
        return sessions.isTrading(Objects.requireNonNull(playerId, "playerId"));
    }

    @Override
    public Optional<UxmTrade> of(UUID playerId) {
        return sessions.snapshot(Objects.requireNonNull(playerId, "playerId")).map(TradeQueries::view);
    }

    @Override
    public List<UxmTrade> open() {
        return sessions.snapshots().stream().map(TradeQueries::view).toList();
    }

    private static UxmTrade view(TradeSessions.TradeSnapshot snapshot) {
        return new UxmTrade(
                snapshot.id().value(),
                snapshot.initiator().uuid(),
                snapshot.initiator().name(),
                snapshot.partner().uuid(),
                snapshot.partner().name(),
                snapshot.initiatorConfirmed(),
                snapshot.partnerConfirmed());
    }
}
