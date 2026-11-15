package com.uxplima.uxmessentials.economy.application;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.TransferError;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /sellall}: sell every sellable item in the seller's inventory at its configured {@link WorthSource}
 * worth, the bulk counterpart to {@code /sell}. It reuses the same pricing table and the same DB-backed
 * {@link EconomyProvider} credit (never a PDC stamp, the economy hard invariant). Priced stacks are grouped by
 * the currency they price in, each currency is credited in one move, and a material is reported as sold only
 * after the credit for its currency actually applied, so the inventory the adapter removes and the balance the
 * seller received never diverge. Unpriced materials are silently left in place: no "cannot be sold" spam.
 *
 * <p>An inventory with nothing priced (or empty) is refused with {@link EconomyMessageKey#SELL_NOTHING_TO_SELL}
 * and leaves the inventory untouched; a currency whose credit the clamp rejects leaves that currency's stacks in
 * place and notifies the clamp once, while any other currency still settles. The seller is sent exactly one
 * summary covering every currency that paid out.
 *
 * <p>Once any credit has applied nothing throws: a currency whose credit fails is logged and left unsold, and the
 * notices are guarded, so the outcome always carries every stack that was paid for. The caller reads an exception as
 * a sale that paid nothing and hands every item back.
 */
public final class SellAll {

    private final EconomyProvider economy;
    private final WorthSource worth;
    private final EconomyNotifier notifier;
    private final Currency defaultCurrency;
    private final java.util.Collection<Currency> currencies;
    private final Logger log;

    public SellAll(
            EconomyProvider economy,
            WorthSource worth,
            EconomyNotifier notifier,
            Currency defaultCurrency,
            java.util.Collection<Currency> currencies,
            Logger log) {
        this.economy = Objects.requireNonNull(economy, "economy");
        this.worth = Objects.requireNonNull(worth, "worth");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.defaultCurrency = Objects.requireNonNull(defaultCurrency, "defaultCurrency");
        this.currencies = java.util.List.copyOf(currencies);
        this.log = Objects.requireNonNull(log, "log");
    }

    /** Sell every priced material in {@code materials} (id → count) for {@code seller}, crediting the total per currency. */
    public SellAllOutcome sellAll(PlayerRef seller, Map<String, Integer> materials) {
        return sellAll(seller, materials, paid -> {});
    }

    /**
     * Sell every priced material in {@code materials} for {@code seller}, handing {@code onPaid} each currency's stacks
     * the moment that currency's credit has applied.
     */
    public SellAllOutcome sellAll(
            PlayerRef seller, Map<String, Integer> materials, Consumer<Map<String, Integer>> onPaid) {
        Objects.requireNonNull(seller, "seller");
        Objects.requireNonNull(materials, "materials");
        Objects.requireNonNull(onPaid, "onPaid");

        // Priced once, here, before any money moves: a worth that changes mid-loop cannot throw after a credit.
        Map<Currency, Bucket> pricedByCurrency = groupByCurrency(materials);
        if (pricedByCurrency.isEmpty()) {
            notifier.send(seller, EconomyMessageKey.SELL_NOTHING_TO_SELL);
            return SellAllOutcome.refused();
        }

        Map<String, Integer> sold = new LinkedHashMap<>();
        Map<Currency, Money> proceeds = new LinkedHashMap<>();
        boolean failed = false;
        for (Map.Entry<Currency, Bucket> bucket : pricedByCurrency.entrySet()) {
            try {
                creditCurrency(seller, bucket.getKey(), bucket.getValue(), sold, proceeds, onPaid);
            } catch (RuntimeException failure) {
                // This currency's stacks stay unsold and go back; what an earlier currency paid for stays sold.
                log.error(
                        "event=sellall_credit_failed seller=" + seller.uuid() + " currency="
                                + bucket.getKey().id().value(),
                        failure);
                failed = true;
            }
        }
        if (failed) {
            notifySafely(seller, () -> notifier.send(seller, EconomyMessageKey.SELL_FAILED));
        }
        if (proceeds.isEmpty()) {
            return SellAllOutcome.refused();
        }
        notifySafely(
                seller,
                () -> notifier.send(
                        seller,
                        EconomyMessageKey.SELLALL_SUMMARY,
                        Map.of("count", Integer.toString(sold.size()), "amount", notifier.amounts(proceeds.values()))));
        return SellAllOutcome.sold(sold, proceeds);
    }

    /** One currency's priced stacks and their total worth. */
    private record Bucket(Map<String, Integer> stacks, BigDecimal total) {}

    /** Group every priced stack under the currency it prices in, with the total worth carried along. */
    private Map<Currency, Bucket> groupByCurrency(Map<String, Integer> materials) {
        Map<Currency, Map<String, Integer>> stacks = new LinkedHashMap<>();
        Map<Currency, BigDecimal> totals = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> stack : materials.entrySet()) {
            int count = stack.getValue();
            if (count <= 0) {
                continue;
            }
            Optional<Worth> unit = worth.unitPrice(stack.getKey());
            if (unit.isEmpty()) {
                continue;
            }
            Currency currency = currencyFor(unit.get());
            stacks.computeIfAbsent(currency, c -> new LinkedHashMap<>()).put(stack.getKey(), count);
            totals.merge(currency, unit.get().amount().multiply(BigDecimal.valueOf(count)), BigDecimal::add);
        }
        Map<Currency, Bucket> grouped = new LinkedHashMap<>();
        stacks.forEach((currency, priced) ->
                grouped.put(currency, new Bucket(priced, totals.getOrDefault(currency, BigDecimal.ZERO))));
        return grouped;
    }

    /** Credit one currency's total in a single move; only on success are its stacks recorded as sold. */
    private void creditCurrency(
            PlayerRef seller,
            Currency currency,
            Bucket bucket,
            Map<String, Integer> sold,
            Map<Currency, Money> proceeds,
            Consumer<Map<String, Integer>> onPaid) {
        Money credit = Money.of(currency, bucket.total());
        Result<Unit, TransferError> credited = economy.credit(seller, credit);
        if (credited.isErr()) {
            notifySafely(
                    seller, () -> notifier.send(seller, credited.errorOrThrow().messageKey()));
            return;
        }
        sold.putAll(bucket.stacks());
        proceeds.put(currency, credit);
        onPaid.accept(bucket.stacks());
    }

    /** Send a notice whose failure is logged, because by now money may have moved and the outcome must survive. */
    private void notifySafely(PlayerRef seller, Runnable send) {
        try {
            send.run();
        } catch (RuntimeException failure) {
            log.error("event=sellall_notice_failed seller=" + seller.uuid(), failure);
        }
    }

    private Currency currencyFor(Worth unit) {
        return currencies.stream()
                .filter(c -> c.id().value().equalsIgnoreCase(unit.currencyId()))
                .findFirst()
                .orElse(defaultCurrency);
    }
}
