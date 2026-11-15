package com.uxplima.uxmessentials.economy.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.testing.RecordingLogger;
import org.junit.jupiter.api.Test;

/** A sale pays the worth of what is sold and reports items as sold only once that credit has applied. */
class SellItemTest {

    private static final Currency DOLLARS = Currency.builder(CurrencyId.of("dollars"))
            .max(new BigDecimal("1000"))
            .build();
    private static final Currency GEMS = Currency.builder(CurrencyId.of("gems")).build();

    private final InMemoryBank bank = new InMemoryBank(DOLLARS, GEMS);
    private final List<String> lines = new ArrayList<>();
    private final RecordingLogger log = new RecordingLogger();
    private boolean noticesFail;

    private final WorthSource worth = material -> switch (material) {
        case "coal" -> Optional.of(Worth.of(new BigDecimal("0.25"), "dollars"));
        case "diamond" -> Optional.of(Worth.of(new BigDecimal("3"), "gems"));
        case "relic" -> Optional.of(Worth.of(new BigDecimal("7"), "retired-currency"));
        default -> Optional.empty();
    };
    private final EconomyNotifier notifier = new EconomyNotifier(
            (PlayerRef viewer, MessageKey key, Map<String, String> placeholders) -> {
                if (noticesFail) {
                    throw new IllegalStateException("chat is down");
                }
                lines.add(key.key());
                return key.key();
            },
            (viewer, text) -> {});

    private final SellItem sellItem = new SellItem(bank, worth, notifier, DOLLARS, List.of(DOLLARS, GEMS), log);
    private final SellAll sellAll = new SellAll(bank, worth, notifier, DOLLARS, List.of(DOLLARS, GEMS), log);

    private BigDecimal balance(Currency currency) {
        return bank.balance(ALEX, currency).amount();
    }

    @Test
    void aSalePaysCountTimesTheWorthInTheItemsCurrency() {
        int[] paid = {0};

        SellOutcome outcome = sellItem.sell(ALEX, "diamond", 4, () -> paid[0]++);

        assertThat(outcome.sold()).isTrue();
        assertThat(outcome.earned()).contains(Money.of(GEMS, 12));
        assertThat(balance(GEMS)).isEqualByComparingTo("12");
        assertThat(paid[0]).isEqualTo(1);
    }

    @Test
    void aWorthInACurrencyNoLongerConfiguredPaysInTheDefaultCurrency() {
        sellItem.sell(ALEX, "relic", 2);

        assertThat(balance(DOLLARS)).isEqualByComparingTo("14");
    }

    @Test
    void anUnpricedItemOrNothingInHandIsRefusedAndPaysNothing() {
        int[] paid = {0};

        assertThat(sellItem.sell(ALEX, "dirt", 64, () -> paid[0]++).sold()).isFalse();
        assertThat(sellItem.sell(ALEX, "coal", 0, () -> paid[0]++).sold()).isFalse();

        assertThat(paid[0]).isZero();
        assertThat(balance(DOLLARS)).isZero();
        assertThat(lines).containsExactly("wallet.sell-not-sellable", "wallet.sell-nothing");
    }

    @Test
    void aCreditOverTheMaximumBalanceKeepsTheItemsAndPaysNothing() {
        bank.set(ALEX, Money.of(DOLLARS, 999));
        int[] paid = {0};

        SellOutcome outcome = sellItem.sell(ALEX, "coal", 8, () -> paid[0]++);

        assertThat(outcome.sold()).isFalse();
        assertThat(paid[0]).isZero();
        assertThat(balance(DOLLARS)).isEqualByComparingTo("999");
    }

    @Test
    void aNoticeThatFailsAfterTheCreditStillReportsTheSale() {
        noticesFail = true;

        SellOutcome outcome = sellItem.sell(ALEX, "coal", 4);

        assertThat(outcome.sold()).isTrue();
        assertThat(balance(DOLLARS)).isEqualByComparingTo("1");
        assertThat(log.errors()).hasSize(1);
    }

    @Test
    void sellAllPaysEachCurrencyOnceAndLeavesUnpricedAndClampedStacksUnsold() {
        bank.set(ALEX, Money.of(DOLLARS, 999));
        Map<String, Integer> inventory = new LinkedHashMap<>();
        inventory.put("coal", 64);
        inventory.put("diamond", 2);
        inventory.put("dirt", 32);
        List<Map<String, Integer>> paid = new ArrayList<>();

        SellAllOutcome outcome = sellAll.sellAll(ALEX, inventory, paid::add);

        assertThat(outcome.sold()).isEqualTo(Map.of("diamond", 2));
        assertThat(paid).containsExactly(Map.of("diamond", 2));
        assertThat(balance(GEMS)).isEqualByComparingTo("6");
        assertThat(balance(DOLLARS)).isEqualByComparingTo("999");
    }

    @Test
    void sellAllWithNothingPricedSellsNothing() {
        SellAllOutcome outcome = sellAll.sellAll(ALEX, Map.of("dirt", 64, "coal", 0));

        assertThat(outcome.sold()).isEmpty();
        assertThat(balance(DOLLARS)).isZero();
        assertThat(lines).containsExactly("wallet.sell-nothing");
    }
}
