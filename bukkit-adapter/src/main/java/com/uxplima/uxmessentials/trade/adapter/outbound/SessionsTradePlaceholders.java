package com.uxplima.uxmessentials.trade.adapter.outbound;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.adapter.outbound.papi.TradePlaceholders;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.trade.adapter.inbound.gui.TradeSessions;
import org.jspecify.annotations.NullMarked;

/**
 * {@link TradePlaceholders} over the live {@link TradeSessions} registry, which is the whole store a same-server
 * trade has. Both reads are map lookups keyed by player id, cheap enough for a per-viewer nametag or chat line.
 */
@NullMarked
public final class SessionsTradePlaceholders implements TradePlaceholders {

    private final TradeSessions sessions;

    public SessionsTradePlaceholders(TradeSessions sessions) {
        this.sessions = Objects.requireNonNull(sessions, "sessions");
    }

    @Override
    public boolean isTrading(PlayerRef who) {
        return sessions.isTrading(Objects.requireNonNull(who, "who").uuid());
    }

    @Override
    public boolean isTradingWith(PlayerRef one, PlayerRef other) {
        Objects.requireNonNull(one, "one");
        Objects.requireNonNull(other, "other");
        Optional<TradeSessions.TradeSnapshot> open = sessions.snapshot(one.uuid());
        return open.filter(trade -> trade.initiator().uuid().equals(other.uuid())
                        || trade.partner().uuid().equals(other.uuid()))
                .isPresent();
    }
}
