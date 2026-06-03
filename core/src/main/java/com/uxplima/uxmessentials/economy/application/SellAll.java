package com.uxplima.uxmessentials.economy.application;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /sellall}: sell every sellable item in the seller's inventory at its configured {@link WorthSource}
 * worth in one credit, the bulk counterpart to {@code /sell}. It reuses the same pricing table and the same
 * DB-backed {@link EconomyProvider} credit (never a PDC stamp — the economy hard invariant), but folds the
 * whole inventory snapshot into a single proceeds figure so the seller is credited once and notified once
 * instead of per material. Unpriced materials are silently left in place — no "cannot be sold" spam — so only
 * the priced stacks are reported back for removal.
 *
 * <p>An inventory with nothing priced (or empty) is refused with {@link EconomyMessageKey#SELL_NOTHING_TO_SELL}
 * and leaves the inventory untouched; a credit the currency clamp rejects is refused with the clamp notice.
 */
public final class SellAll {

    private final EconomyProvider economy;
    private final WorthSource worth;
    private final EconomyNotifier notifier;
    private final Currency currency;

    public SellAll(EconomyProvider economy, WorthSource worth, EconomyNotifier notifier, Currency currency) {
        this.economy = Objects.requireNonNull(economy, "economy");
        this.worth = Objects.requireNonNull(worth, "worth");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.currency = Objects.requireNonNull(currency, "currency");
    }

    /** Sell every priced material in {@code materials} (id → count) for {@code seller}, crediting the total. */
    public SellAllOutcome sellAll(PlayerRef seller, Map<String, Integer> materials) {
        Objects.requireNonNull(seller, "seller");
        Objects.requireNonNull(materials, "materials");
        Map<String, Integer> sold = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<String, Integer> stack : materials.entrySet()) {
            total = accumulate(sold, total, stack.getKey(), stack.getValue());
        }
        if (sold.isEmpty()) {
            notifier.send(seller, EconomyMessageKey.SELL_NOTHING_TO_SELL);
            return SellAllOutcome.refused();
        }
        return credit(seller, sold, Money.of(currency, total));
    }

    private BigDecimal accumulate(Map<String, Integer> sold, BigDecimal total, String material, int count) {
        if (count <= 0) {
            return total;
        }
        Optional<BigDecimal> unit = worth.unitPrice(material);
        if (unit.isEmpty()) {
            return total;
        }
        sold.put(material, count);
        return total.add(unit.get().multiply(BigDecimal.valueOf(count)));
    }

    private SellAllOutcome credit(PlayerRef seller, Map<String, Integer> sold, Money proceeds) {
        Result<Unit, TransferError> credited = economy.credit(seller, proceeds);
        if (credited.isErr()) {
            notifier.send(seller, credited.errorOrThrow().messageKey());
            return SellAllOutcome.refused();
        }
        notifier.send(
                seller,
                EconomyMessageKey.SELLALL_SUMMARY,
                Map.of("count", Integer.toString(sold.size()), "amount", notifier.amount(proceeds)));
        return SellAllOutcome.sold(sold, proceeds);
    }
}
