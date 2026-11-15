package com.uxplima.uxmessentials.playerwarps.application;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpEconomy;
import com.uxplima.uxmessentials.playerwarps.domain.ChargeError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.warps.domain.WarpCost;

/**
 * Where the money of a cross-server use ends up. The origin only debits the visitor, so a use that never moves
 * them gets that exact charge back here, and a use the target completed pays the owner's share here.
 *
 * <p>A caller reaches this only after it claimed the request (its store delete removed the row), which is what
 * keeps every credit and accrual single. Nothing here throws: by then the claim is spent, so a fault is logged
 * with the player and amount for an operator to settle by hand rather than lost with the exception. Money moves
 * before any line is sent, so a failing message never costs the player their refund.
 */
public final class CrossServerCharges {

    private final Optional<PlayerWarpEconomy> economy;
    private final Notifier notifier;
    private final Logger log;

    public CrossServerCharges(Optional<PlayerWarpEconomy> economy, Notifier notifier, Logger log) {
        this.economy = Objects.requireNonNull(economy, "economy");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.log = Objects.requireNonNull(log, "log");
    }

    /** Tell {@code who} why the hop did not happen and give back what they paid for it. */
    public void fail(
            PlayerRef who,
            Optional<WarpCost> paid,
            PlayerwarpsMessageKey reason,
            Map<String, String> placeholders,
            String warpLabel) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(paid, "paid");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(placeholders, "placeholders");
        Objects.requireNonNull(warpLabel, "warpLabel");
        boolean credited = credit(who, paid);
        Map<String, String> values = new HashMap<>(placeholders);
        values.putIfAbsent("warp", warpLabel);
        tell(who, reason, values);
        if (credited) {
            tell(who, PlayerwarpsMessageKey.PWARP_CROSS_SERVER_REFUNDED, Map.of("warp", warpLabel));
        }
    }

    /** Give back what {@code who} paid for a request that is being replaced, with only the refund line. */
    public void refund(PlayerRef who, Optional<WarpCost> paid, String warpLabel) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(paid, "paid");
        Objects.requireNonNull(warpLabel, "warpLabel");
        if (credit(who, paid)) {
            tell(who, PlayerwarpsMessageKey.PWARP_CROSS_SERVER_REFUNDED, Map.of("warp", warpLabel));
        }
    }

    /**
     * Pay the owner of {@code warp} their share of what {@code who} paid, now that the target moved them. An accrual
     * that banks nothing (the warp was deleted meanwhile, or the write failed) gives the visitor their charge back,
     * the same outcome a local use gets when its accrual fails.
     */
    public void settle(PlayerRef who, PlayerWarpId warp, Optional<WarpCost> paid, String warpLabel) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(warp, "warp");
        Objects.requireNonNull(paid, "paid");
        Objects.requireNonNull(warpLabel, "warpLabel");
        if (economy.isEmpty() || paid.isEmpty()) {
            return;
        }
        WarpCost cost = paid.get();
        if (accrue(warp, cost)) {
            return;
        }
        log.warn(
                "event=playerwarp_cross_server_accrue_failed warp={} visitor={} amount={} currency={}",
                warp.value(),
                who.uuid(),
                cost.amount(),
                cost.currencyId());
        refund(who, paid, warpLabel);
    }

    private boolean accrue(PlayerWarpId warp, WarpCost cost) {
        try {
            return economy.orElseThrow()
                    .accrue(warp, cost.amount(), cost.currencyId())
                    .isOk();
        } catch (RuntimeException accrualFailure) {
            log.error(
                    "event=playerwarp_cross_server_accrue_threw warp=" + warp.value() + " amount=" + cost.amount()
                            + " currency=" + cost.currencyId(),
                    accrualFailure);
            return false;
        }
    }

    /** Credit the exact charge back; true only when a charge existed and the credit landed. */
    private boolean credit(PlayerRef who, Optional<WarpCost> paid) {
        if (economy.isEmpty() || paid.isEmpty()) {
            return false;
        }
        WarpCost cost = paid.get();
        try {
            Result<Unit, ChargeError> credited = economy.get().refund(who, cost.amount(), cost.currencyId());
            if (credited.isOk()) {
                return true;
            }
            log.error(
                    "event=playerwarp_cross_server_refund_failed player=" + who.uuid() + " name=" + who.name()
                            + " amount=" + cost.amount() + " currency=" + cost.currencyId(),
                    new IllegalStateException("refund rejected: " + credited.errorOrThrow()));
        } catch (RuntimeException refundFailure) {
            log.error(
                    "event=playerwarp_cross_server_refund_failed player=" + who.uuid() + " name=" + who.name()
                            + " amount=" + cost.amount() + " currency=" + cost.currencyId(),
                    refundFailure);
        }
        return false;
    }

    private void tell(PlayerRef who, PlayerwarpsMessageKey key, Map<String, String> placeholders) {
        try {
            notifier.send(who, key, placeholders);
        } catch (RuntimeException sendFailure) {
            log.warn(
                    "event=playerwarp_cross_server_notice_failed player={} key={} reason={}",
                    who.uuid(),
                    key.key(),
                    sendFailure.toString());
        }
    }
}
