package com.uxplima.uxmessentials.shared.application.hop;

import com.uxplima.uxmessentials.shared.application.hop.port.PendingHop;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Everything the shared hop needs to know about one {@link HopKind}: how to find the destination on the target,
 * where the money goes, and how the notices read. Each context that sends players across the proxy registers one
 * in {@link HopResolvers} on every backend, since the origin's watchdog refunds through it as well as the target.
 *
 * <p>Any method may throw. The hop calls {@link #label} and {@link #resolve} before it claims the row, so a failed
 * read leaves the row for a later attempt, and catches a failing {@link #refund} or {@link #settle} after the
 * claim to log it with the player and amount.
 */
public interface HopResolver {

    /** The kind this resolver serves. */
    HopKind kind();

    /** The catalog keys and placeholder this kind words its notices with. */
    HopNotices notices();

    /** The name the player knows {@code hop}'s destination by, for a refund line. */
    String label(PendingHop hop);

    /** Off-tick: find where {@code hop} lands on this backend, or that it can no longer land here. */
    HopResolution resolve(PendingHop hop);

    /** Give {@code charge} back to {@code who}; true only once the credit has landed. */
    boolean refund(PlayerRef who, HopCharge charge);

    /** Pay {@code charge} on to {@code settlement}'s payee now that {@code who} landed; true once it is banked. */
    boolean settle(PlayerRef who, HopSettlement settlement, HopCharge charge);

    /**
     * A hop of this kind to {@code label} ended without moving {@code who}, for {@code reason}; they have been told.
     * Called once for each hop that fails with a notice, after its claim. A hop refunded without one (its player left
     * during the settling delay, its backend stopped, it was withdrawn or replaced) does not call this. A kind somebody
     * other than the player must hear about (staff, for a hop the player did not choose) answers here; the others
     * need nothing.
     */
    default void failed(PlayerRef who, String label, HopNotice reason) {}
}
