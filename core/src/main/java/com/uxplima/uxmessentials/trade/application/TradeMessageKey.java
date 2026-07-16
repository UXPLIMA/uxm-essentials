package com.uxplima.uxmessentials.trade.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The trade context's user-visible message keys. Each constant maps 1:1 to a kebab-case catalog key in
 * {@code messages_<lang>.conf} ({@code TRADE_REQUEST_SENT} ↔ {@code trade.request-sent}); the constant is the
 * compile-time handle, the catalog holds the text. There are no inline player-facing literals in the context — every
 * message resolves through one of these.
 *
 * <p>Per the i18n contract a disabled module still ships its keys so the catalog stays whole and the locale-parity
 * guard sees the full {@code en} key set. This is the Phase-1 seed covering the request/accept flow, the window
 * lifecycle, and the refusals the first behaviour phases will surface; the later phases (money, blacklist,
 * cross-server) add their own keys here as their verbs land.
 */
public enum TradeMessageKey implements MessageKey {

    // Request flow — /trade <player> and /trade accept|deny.
    TRADE_REQUEST_SENT("trade.request-sent"),
    TRADE_REQUEST_RECEIVED("trade.request-received"),
    TRADE_REQUEST_EXPIRED("trade.request-expired"),
    TRADE_NO_REQUEST("trade.no-request"),
    TRADE_ACCEPTED("trade.accepted"),
    TRADE_DENIED("trade.denied"),

    // Window lifecycle — the session opened, was aborted, or completed the swap.
    TRADE_CANCELLED("trade.cancelled"),
    TRADE_COMPLETED("trade.completed"),

    // Refusals — the request or the open could not proceed.
    TRADE_ALREADY_TRADING("trade.already-trading"),
    TRADE_TARGET_BUSY("trade.target-busy"),
    TRADE_CANNOT_TRADE_SELF("trade.cannot-trade-self"),
    TRADE_TOO_FAR("trade.too-far"),
    TRADE_ON_COOLDOWN("trade.on-cooldown"),
    TRADE_ITEM_BLACKLISTED("trade.item-blacklisted"),
    TRADE_CROSS_SERVER_DISABLED("trade.cross-server-disabled"),

    // The bare /trade root's usage line — shown to a sender who ran it without a target or subcommand.
    TRADE_USAGE("trade.usage");

    private final String key;

    TradeMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}
