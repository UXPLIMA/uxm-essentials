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
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

class SellAllTest {

    private static final Currency DOLLARS =
            Currency.builder(CurrencyId.of("dollars")).build();
    private static final Currency GEMS = Currency.builder(CurrencyId.of("gems")).build();

    private final InMemoryBank bank = new InMemoryBank(DOLLARS, GEMS);
    private final List<String> lines = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();

    private SellAll sellAll() {
        WorthSource worth = material -> switch (material) {
            case "coal" -> Optional.of(Worth.of(BigDecimal.ONE, "dollars"));
            case "diamond" -> Optional.of(Worth.of(BigDecimal.TEN, "gems"));
            default -> Optional.empty();
        };
        EconomyNotifier notifier = new EconomyNotifier(
                (PlayerRef viewer, MessageKey key, Map<String, String> placeholders) -> {
                    lines.add(key.key());
                    return key.key();
                },
                (viewer, text) -> {});
        Logger log = new Logger() {
            @Override
            public void info(String message, Object... args) {}

            @Override
            public void warn(String message, Object... args) {}

            @Override
            public void error(String message, Throwable cause) {
                errors.add(message);
            }

            @Override
            public void debug(String message, Object... args) {}
        };
        return new SellAll(bank, worth, notifier, DOLLARS, List.of(DOLLARS, GEMS), log);
    }

    @Test
    void aCurrencyWhoseCreditThrowsLeavesItsStacksUnsoldAndKeepsTheOthersSold() {
        bank.breakCredits(GEMS);
        Map<String, Integer> inventory = new LinkedHashMap<>();
        inventory.put("coal", 10);
        inventory.put("diamond", 2);

        SellAllOutcome outcome = sellAll().sellAll(ALEX, inventory);

        assertThat(outcome.sold()).isEqualTo(Map.of("coal", 10));
        assertThat(bank.balance(ALEX, DOLLARS).amount()).isEqualByComparingTo("10");
        assertThat(lines).containsExactly("wallet.sell-failed", "wallet.sellall-summary");
        assertThat(errors).hasSize(1);
    }
}
