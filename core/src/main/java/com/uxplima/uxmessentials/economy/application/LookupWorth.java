package com.uxplima.uxmessentials.economy.application;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /worth}: report the configured sell value of a material to a viewer, so a player can price loot
 * before committing to {@code /sell}. A pure pricing read against the {@link WorthTable} — it never touches a
 * balance. A single item renders the unit worth; a stack renders the unit worth and the stack total; a
 * material absent from the table renders the not-sellable notice. The amount is rendered through the
 * {@link EconomyNotifier} so the worth uses the same currency formatting as every other money figure.
 */
public final class LookupWorth {

    private final WorthTable worth;
    private final EconomyNotifier notifier;
    private final Currency currency;

    public LookupWorth(WorthTable worth, EconomyNotifier notifier, Currency currency) {
        this.worth = Objects.requireNonNull(worth, "worth");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.currency = Objects.requireNonNull(currency, "currency");
    }

    /** Report the worth of {@code amount} of {@code material} to {@code viewer}. */
    public void report(PlayerRef viewer, String material, int amount) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(material, "material");
        Optional<BigDecimal> unit = worth.unitPrice(material);
        if (unit.isEmpty()) {
            notifier.send(viewer, EconomyMessageKey.WORTH_NOT_SELLABLE, Map.of("item", material));
            return;
        }
        Money unitWorth = Money.of(currency, unit.get());
        if (amount <= 1) {
            notifier.send(
                    viewer,
                    EconomyMessageKey.WORTH_RESULT,
                    Map.of("item", material, "amount", notifier.amount(unitWorth)));
            return;
        }
        Money stackWorth = Money.of(currency, unit.get().multiply(BigDecimal.valueOf(amount)));
        notifier.send(
                viewer,
                EconomyMessageKey.WORTH_RESULT_STACK,
                Map.of(
                        "item", material,
                        "count", Integer.toString(amount),
                        "amount", notifier.amount(unitWorth),
                        "total", notifier.amount(stackWorth)));
    }
}
