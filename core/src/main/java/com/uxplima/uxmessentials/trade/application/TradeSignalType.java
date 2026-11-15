package com.uxplima.uxmessentials.trade.application;

/**
 * The kind of a cross-server {@link TradeSignal}. A signal is a lightweight poke over the bus that tells the peer
 * "the shared escrow rows for this trade changed. Re-evaluate"; the authoritative state lives in the escrow table,
 * never in the frame (the bus "notification, not source of truth" contract).
 *
 * <ul>
 *   <li>{@link #INVITE}. A {@code /trade} to a player on another backend, addressed by name to every peer.
 *   <li>{@link #DELIVERED} / {@link #NOT_FOUND} / {@link #BUSY}. A peer's answer to an {@code INVITE}: the target saw
 *       it and may accept, the target is not online on that peer, or the target is already in a trade.
 *   <li>{@link #ACCEPT} / {@link #DECLINE}. The target accepted or denied a delivered invite.
 *   <li>{@link #READY}. The sender escrowed its side (its row is {@code HELD}); the receiver re-checks whether both
 *       sides are now escrowed and, if so, crosses the atomic commit point.
 *   <li>{@link #COMMIT}. The sender crossed the commit point; the receiver delivers the counterpart's goods to its
 *       own player.
 *   <li>{@link #ABORT}: the sender refunded/cancelled its side or withdrew its invite; the receiver refunds its own
 *       side if still refundable and drops the invite.
 * </ul>
 *
 * <p>The frame carries the constant's name, so a constant is never renamed or given a second meaning, and new kinds
 * are only appended: a peer on an older build drops a kind it does not know instead of misreading it.
 */
public enum TradeSignalType {
    INVITE,
    ACCEPT,
    DECLINE,
    READY,
    COMMIT,
    ABORT,
    DELIVERED,
    NOT_FOUND,
    BUSY
}
