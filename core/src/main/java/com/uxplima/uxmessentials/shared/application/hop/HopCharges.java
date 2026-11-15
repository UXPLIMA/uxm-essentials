package com.uxplima.uxmessentials.shared.application.hop;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.application.hop.port.PendingHop;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Where the money of a cross-server hop ends up. The origin only debits the player, so a hop that never moves them
 * gets that exact charge back here, and a hop the target completed settles the charge here, each through the
 * kind's {@link HopResolver}.
 *
 * <p>A caller reaches this only after it claimed the hop (its store delete removed the row), which is what keeps
 * every credit and settlement single. Nothing here throws: by then the claim is spent, so a fault is logged with
 * the player and amount for an operator to settle by hand rather than lost with the exception. Money moves before
 * any line is sent, so a failing message never costs the player their refund.
 */
public final class HopCharges {

    private final Notifier notifier;
    private final Logger log;

    public HopCharges(Notifier notifier, Logger log) {
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.log = Objects.requireNonNull(log, "log");
    }

    /** Tell {@code who} why the hop did not happen and give back what they paid for it. */
    public void fail(
            HopResolver resolver,
            PlayerRef who,
            Optional<HopCharge> charge,
            HopNotice reason,
            Map<String, String> placeholders,
            String label) {
        Objects.requireNonNull(resolver, "resolver");
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(charge, "charge");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(placeholders, "placeholders");
        Objects.requireNonNull(label, "label");
        boolean credited = credit(resolver, who, charge);
        tell(resolver, who, reason, placeholders, label);
        if (credited) {
            tell(resolver, who, HopNotice.REFUNDED, Map.of(), label);
        }
    }

    /** Give back what {@code who} paid for a hop that is being replaced or abandoned, with only the refund line. */
    public void refund(HopResolver resolver, PlayerRef who, Optional<HopCharge> charge, String label) {
        Objects.requireNonNull(resolver, "resolver");
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(charge, "charge");
        Objects.requireNonNull(label, "label");
        if (credit(resolver, who, charge)) {
            tell(resolver, who, HopNotice.REFUNDED, Map.of(), label);
        }
    }

    /**
     * Pay {@code hop}'s charge on to its settlement now that the target moved {@code who}. A settlement that banks
     * nothing gives the player their charge back, the same outcome a local use gets when its payout fails. A hop
     * with no charge or no settlement has nothing to pay.
     */
    public void settle(HopResolver resolver, PlayerRef who, PendingHop hop, String label) {
        Objects.requireNonNull(resolver, "resolver");
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(hop, "hop");
        Objects.requireNonNull(label, "label");
        if (hop.charge().isEmpty() || hop.settlement().isEmpty()) {
            return;
        }
        HopCharge charge = hop.charge().get();
        HopSettlement settlement = hop.settlement().get();
        if (paidOn(resolver, who, settlement, charge)) {
            return;
        }
        // The player has landed, so this refund hands back a hop that did happen; the line lets an operator see it.
        log.warn(
                "event=cross_server_hop_settle_failed kind={} payee={} label={} player={} name={} amount={}"
                        + " currency={} outcome=player_refunded",
                hop.kind(),
                settlement.payee(),
                label,
                who.uuid(),
                who.name(),
                charge.amount(),
                charge.currencyId());
        refund(resolver, who, hop.charge(), label);
    }

    /** Send one hop notice in the kind's wording; a failing send is logged, never thrown. */
    public void tell(
            HopResolver resolver, PlayerRef who, HopNotice notice, Map<String, String> placeholders, String label) {
        Objects.requireNonNull(resolver, "resolver");
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(notice, "notice");
        Objects.requireNonNull(placeholders, "placeholders");
        Objects.requireNonNull(label, "label");
        HopNotices notices = resolver.notices();
        Map<String, String> values = new HashMap<>(placeholders);
        values.putIfAbsent(notices.labelTag(), label);
        try {
            notifier.send(who, notices.key(notice), values);
        } catch (RuntimeException sendFailure) {
            log.warn(
                    "event=cross_server_hop_notice_failed player={} notice={} reason={}",
                    who.uuid(),
                    notice,
                    sendFailure.toString());
        }
    }

    private boolean paidOn(HopResolver resolver, PlayerRef who, HopSettlement settlement, HopCharge charge) {
        try {
            return resolver.settle(who, settlement, charge);
        } catch (RuntimeException settleFailure) {
            log.error(
                    "event=cross_server_hop_settle_threw kind=" + resolver.kind() + " payee=" + settlement.payee()
                            + " amount=" + charge.amount() + " currency=" + charge.currencyId(),
                    settleFailure);
            return false;
        }
    }

    /** Credit the exact charge back; true only when a charge existed and the credit landed. */
    private boolean credit(HopResolver resolver, PlayerRef who, Optional<HopCharge> charge) {
        if (charge.isEmpty()) {
            return false;
        }
        HopCharge cost = charge.get();
        String line = "event=cross_server_hop_refund_failed kind=" + resolver.kind() + " player=" + who.uuid()
                + " name=" + who.name() + " amount=" + cost.amount() + " currency=" + cost.currencyId();
        try {
            if (resolver.refund(who, cost)) {
                return true;
            }
            log.error(line, new IllegalStateException("refund rejected"));
        } catch (RuntimeException refundFailure) {
            log.error(line, refundFailure);
        }
        return false;
    }
}
