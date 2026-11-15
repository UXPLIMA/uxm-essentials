package com.uxplima.uxmessentials.economy.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.CLOCK;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.testing.RecordingLogger;
import org.junit.jupiter.api.Test;

class SellAllTest {

    private static final Currency DOLLARS =
            Currency.builder(CurrencyId.of("dollars")).build();
    private static final Currency GEMS = Currency.builder(CurrencyId.of("gems")).build();

    private final InMemoryBank bank = new InMemoryBank(DOLLARS, GEMS);
    private final List<String> lines = new ArrayList<>();
    private final RecordingLogger log = new RecordingLogger();
    private boolean summaryFails;

    private SellAll sellAll(EconomyProvider economy) {
        WorthSource worth = material -> switch (material) {
            case "coal" -> Optional.of(Worth.of(BigDecimal.ONE, "dollars"));
            case "diamond" -> Optional.of(Worth.of(BigDecimal.TEN, "gems"));
            default -> Optional.empty();
        };
        EconomyNotifier notifier = new EconomyNotifier(
                (PlayerRef viewer, MessageKey key, Map<String, String> placeholders) -> {
                    if (summaryFails && key == EconomyMessageKey.SELLALL_SUMMARY) {
                        throw new IllegalStateException("chat is down");
                    }
                    lines.add(key.key());
                    return key.key();
                },
                (viewer, text) -> {});
        return new SellAll(economy, worth, notifier, DOLLARS, List.of(DOLLARS, GEMS), log);
    }

    private static Map<String, Integer> coalAndDiamonds() {
        Map<String, Integer> inventory = new LinkedHashMap<>();
        inventory.put("coal", 10);
        inventory.put("diamond", 2);
        return inventory;
    }

    @Test
    void aCurrencyWhoseCreditThrowsLeavesItsStacksUnsoldAndKeepsTheOthersSold() {
        bank.breakCredits(GEMS);

        SellAllOutcome outcome = sellAll(bank).sellAll(ALEX, coalAndDiamonds());

        assertThat(outcome.sold()).isEqualTo(Map.of("coal", 10));
        assertThat(bank.balance(ALEX, DOLLARS).amount()).isEqualByComparingTo("10");
        assertThat(lines).containsExactly("wallet.sell-failed", "wallet.sellall-summary");
        assertThat(log.errors()).hasSize(1);
    }

    @Test
    void creditsThatAppliedStaySoldWhenEverythingAfterThemFails() {
        summaryFails = true;
        PublishingEconomyProvider economy = new PublishingEconomyProvider(
                bank,
                event -> {
                    throw new IllegalStateException("event bus down");
                },
                CLOCK,
                log);
        List<Map<String, Integer>> paid = new ArrayList<>();

        SellAllOutcome outcome = sellAll(economy).sellAll(ALEX, coalAndDiamonds(), paid::add);

        assertThat(outcome.sold()).isEqualTo(Map.of("coal", 10, "diamond", 2));
        assertThat(paid).containsExactly(Map.of("coal", 10), Map.of("diamond", 2));
        assertThat(bank.balance(ALEX, GEMS).amount()).isEqualByComparingTo("20");
    }
}
