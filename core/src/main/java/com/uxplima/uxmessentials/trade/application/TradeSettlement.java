package com.uxplima.uxmessentials.trade.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.trade.application.port.TradeEconomy;
import com.uxplima.uxmessentials.trade.application.port.TradeExperience;
import com.uxplima.uxmessentials.trade.domain.ExperienceTransfer;
import com.uxplima.uxmessentials.trade.domain.MoneyTransfer;
import com.uxplima.uxmessentials.trade.domain.TradeOffer;
import com.uxplima.uxmessentials.trade.domain.TradeSession;
import com.uxplima.uxmessentials.trade.domain.TradeSide;
import org.jspecify.annotations.NullMarked;

/**
 * Moves the money and experience a {@link TradeSession}'s two offers stake, all-or-nothing, through the
 * {@link TradeEconomy} and {@link TradeExperience} ports. The decision, which legs move, from whom, to whom, is the pure
 * {@link #transfers(TradeSession)} and {@link #experienceTransfers(TradeSession)} enumerations (one money leg per
 * non-zero money entry a side staked, one experience leg per side that staked any experience), so it is unit-testable
 * without a live player or provider. {@link #settle(TradeSession)} then applies those legs atomically: it withdraws each
 * side's staked experience into hand (guarded, reversible), moves each money leg through the guarded
 * {@link TradeEconomy#transfer}, and only once both kinds committed does it deposit the withdrawn experience to the
 * other side. If any step fails it refunds every experience already withdrawn and reverses every money leg already
 * moved, then reports failure, so a commit either moves all of it or none of it. The trade adapter runs the item swap
 * only when {@link #settle} returned {@code true}, so items, money, and experience move together or not at all.
 */
@NullMarked
public final class TradeSettlement {

    private final TradeEconomy economy;
    private final TradeExperience experience;

    public TradeSettlement(TradeEconomy economy, TradeExperience experience) {
        this.economy = Objects.requireNonNull(economy, "economy");
        this.experience = Objects.requireNonNull(experience, "experience");
    }

    /** The money legs a session's offers imply, one per non-zero money entry each side staked. Pure, provider-free. */
    public static List<MoneyTransfer> transfers(TradeSession session) {
        Objects.requireNonNull(session, "session");
        List<MoneyTransfer> legs = new ArrayList<>();
        for (TradeSide side : TradeSide.values()) {
            TradeOffer offer = session.offer(side);
            for (Map.Entry<String, BigDecimal> entry : offer.money().entrySet()) {
                if (entry.getValue().signum() > 0) {
                    legs.add(new MoneyTransfer(side, entry.getKey(), entry.getValue()));
                }
            }
        }
        return legs;
    }

    /** The experience legs a session's offers imply, one per side that staked any experience. Pure, provider-free. */
    public static List<ExperienceTransfer> experienceTransfers(TradeSession session) {
        Objects.requireNonNull(session, "session");
        List<ExperienceTransfer> legs = new ArrayList<>();
        for (TradeSide side : TradeSide.values()) {
            long points = session.offer(side).experience();
            if (points > 0) {
                legs.add(new ExperienceTransfer(side, points));
            }
        }
        return legs;
    }

    /**
     * Move every staked money and experience leg atomically. Returns {@code true} when all legs moved (or none were
     * staked) and {@code false} when any leg could not be covered, in which case every already-moved leg is undone so
     * nothing moved. The caller swaps items only on a {@code true} return.
     */
    public boolean settle(TradeSession session) {
        Objects.requireNonNull(session, "session");
        List<MoneyTransfer> moneyLegs = transfers(session);
        List<ExperienceTransfer> experienceLegs = experienceTransfers(session);
        // Withdraw the staked experience into hand first: it is guarded (a shortfall removes nothing) and reversible,
        // so a failure here has touched no money yet.
        List<ExperienceTransfer> heldExperience = new ArrayList<>(experienceLegs.size());
        for (ExperienceTransfer leg : experienceLegs) {
            if (experience.withdraw(payer(session, leg), leg.points())) {
                heldExperience.add(leg);
            } else {
                refundExperience(session, heldExperience);
                return false;
            }
        }
        if (!affordable(session, moneyLegs)) {
            refundExperience(session, heldExperience);
            return false;
        }
        List<MoneyTransfer> movedMoney = new ArrayList<>(moneyLegs.size());
        for (MoneyTransfer leg : moneyLegs) {
            if (economy.transfer(payer(session, leg), recipient(session, leg), leg.amount(), leg.currencyId())) {
                movedMoney.add(leg);
            } else {
                reverse(session, movedMoney);
                refundExperience(session, heldExperience);
                return false;
            }
        }
        for (ExperienceTransfer leg : heldExperience) {
            experience.deposit(recipient(session, leg), leg.points());
        }
        return true;
    }

    /** Best-effort pre-check that every payer can currently cover their leg; the guarded transfer is the real gate. */
    private boolean affordable(TradeSession session, List<MoneyTransfer> legs) {
        for (MoneyTransfer leg : legs) {
            if (!economy.canAfford(payer(session, leg), leg.amount(), leg.currencyId())) {
                return false;
            }
        }
        return true;
    }

    /** Undo the legs already moved by transferring each back to its payer, so a failed settlement moves no money. */
    private void reverse(TradeSession session, List<MoneyTransfer> moved) {
        for (MoneyTransfer leg : moved) {
            economy.transfer(recipient(session, leg), payer(session, leg), leg.amount(), leg.currencyId());
        }
    }

    /** Return each already-withdrawn experience stake to its own payer, so a failed settlement keeps no experience. */
    private void refundExperience(TradeSession session, List<ExperienceTransfer> held) {
        for (ExperienceTransfer leg : held) {
            experience.deposit(payer(session, leg), leg.points());
        }
    }

    private static PlayerRef payer(TradeSession session, MoneyTransfer leg) {
        return session.participant(leg.payer());
    }

    private static PlayerRef recipient(TradeSession session, MoneyTransfer leg) {
        return session.participant(leg.recipient());
    }

    private static PlayerRef payer(TradeSession session, ExperienceTransfer leg) {
        return session.participant(leg.payer());
    }

    private static PlayerRef recipient(TradeSession session, ExperienceTransfer leg) {
        return session.participant(leg.recipient());
    }
}
