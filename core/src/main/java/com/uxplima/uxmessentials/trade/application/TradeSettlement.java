package com.uxplima.uxmessentials.trade.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.trade.application.port.TradeEconomy;
import com.uxplima.uxmessentials.trade.domain.MoneyTransfer;
import com.uxplima.uxmessentials.trade.domain.TradeOffer;
import com.uxplima.uxmessentials.trade.domain.TradeSession;
import com.uxplima.uxmessentials.trade.domain.TradeSide;
import org.jspecify.annotations.NullMarked;

/**
 * Moves the money a {@link TradeSession}'s two offers stake, all-or-nothing, through the {@link TradeEconomy} port. The
 * decision — which legs move, from whom, to whom — is the pure {@link #transfers(TradeSession)} enumeration (one leg per
 * non-zero money entry a side staked), so it is unit-testable without a provider. {@link #settle(TradeSession)} then
 * applies those legs atomically: it pre-checks affordability, moves each leg through the guarded
 * {@link TradeEconomy#transfer}, and if any leg fails it reverses every leg already moved and reports failure — so a
 * commit either moves all the money or none of it. The trade adapter runs the item swap only when {@link #settle}
 * returned {@code true}, so items and money move together or not at all.
 */
@NullMarked
public final class TradeSettlement {

    private final TradeEconomy economy;

    public TradeSettlement(TradeEconomy economy) {
        this.economy = Objects.requireNonNull(economy, "economy");
    }

    /** The money legs a session's offers imply — one per non-zero money entry each side staked. Pure, provider-free. */
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

    /**
     * Move every staked money leg atomically. Returns {@code true} when all legs moved (or none were staked) and
     * {@code false} when any leg could not be covered, in which case every already-moved leg is reversed so no money
     * moved. The caller swaps items only on a {@code true} return.
     */
    public boolean settle(TradeSession session) {
        Objects.requireNonNull(session, "session");
        List<MoneyTransfer> legs = transfers(session);
        if (!affordable(session, legs)) {
            return false;
        }
        List<MoneyTransfer> moved = new ArrayList<>(legs.size());
        for (MoneyTransfer leg : legs) {
            if (economy.transfer(payer(session, leg), recipient(session, leg), leg.amount(), leg.currencyId())) {
                moved.add(leg);
            } else {
                reverse(session, moved);
                return false;
            }
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

    private static PlayerRef payer(TradeSession session, MoneyTransfer leg) {
        return session.participant(leg.payer());
    }

    private static PlayerRef recipient(TradeSession session, MoneyTransfer leg) {
        return session.participant(leg.recipient());
    }
}
