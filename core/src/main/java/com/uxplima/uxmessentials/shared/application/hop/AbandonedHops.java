package com.uxplima.uxmessentials.shared.application.hop;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.hop.port.PendingHop;
import com.uxplima.uxmessentials.shared.application.hop.port.PendingHopStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;

/**
 * The age past which a pending hop is no longer honoured, and what happens to a row of a kind this backend has no
 * resolver for once it is that old.
 *
 * <p>The request time is stamped by the origin's clock and read against this backend's, so a skew between the two
 * would expire a fresh hop. Twice the TTL absorbs that; the live case, a player the proxy never moved, is already
 * settled at one TTL by the origin's watchdog, which compares only its own clock.
 *
 * <p>A row of an unknown kind is normally left for its origin, whose watchdog can refund it. Once it is past the
 * expiry that watchdog has had its chance and missed it (the origin stopped, or the kind was switched off there),
 * and the row would otherwise stay forever and refuse every new hop for the player. Nothing here can refund it, so
 * it is claimed and logged with the player and amount for an operator to settle by hand.
 */
final class AbandonedHops {

    private final PendingHopStore store;
    private final Logger log;
    private final Duration expiry;
    private final Clock clock;

    AbandonedHops(PendingHopStore store, Logger log, Duration requestTtl, Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.log = Objects.requireNonNull(log, "log");
        this.expiry = Objects.requireNonNull(requestTtl, "requestTtl").multipliedBy(2);
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** True once {@code hop} has aged past the expiry, so it is refunded or claimed rather than honoured. */
    boolean expired(PendingHop hop) {
        return hop.requestedAt().plus(expiry).isBefore(clock.instant());
    }

    /**
     * Claim {@code hop}, a row whose kind this backend has no resolver for, once it is past the expiry. Returns true
     * when this call removed it. A fresh row, or one another caller already took or replaced, is left alone.
     */
    boolean claimUnresolvable(PendingHop hop) {
        if (!expired(hop) || store.clear(hop) != 1) {
            return false;
        }
        log.warn(
                "event=cross_server_hop_abandoned player={} kind={} target={} target_server={} origin_server={}"
                        + " amount={} currency={} outcome=claimed_without_refund",
                hop.player(),
                hop.kind(),
                hop.target(),
                hop.targetServer(),
                hop.originServer(),
                hop.charge().map(HopCharge::amount).map(Object::toString).orElse("none"),
                hop.charge().map(HopCharge::currencyId).orElse("none"));
        return true;
    }
}
