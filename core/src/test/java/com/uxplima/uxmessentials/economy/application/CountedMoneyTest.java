package com.uxplima.uxmessentials.economy.application;

import static com.uxplima.uxmessentials.testing.Fixtures.ALEX;
import static com.uxplima.uxmessentials.testing.Fixtures.CLOCK;
import static com.uxplima.uxmessentials.testing.Fixtures.SAM;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.economy.domain.event.WalletCredited;
import com.uxplima.uxmessentials.economy.domain.event.WalletDebited;
import com.uxplima.uxmessentials.testing.RecordingEvents;
import org.junit.jupiter.api.Test;

class CountedMoneyTest {

    private static final Currency COINS =
            Currency.builder(CurrencyId.of("coins")).build();
    private static final Money TEN = Money.of(COINS, BigDecimal.TEN);

    private final RecordingEvents events = new RecordingEvents();
    private final PublishingEconomyProvider economy =
            new PublishingEconomyProvider(new InMemoryBank(COINS), events, CLOCK);

    private List<Boolean> counted() {
        return events.published().stream()
                .map(event -> switch (event) {
                    case WalletCredited credited -> credited.counted();
                    case WalletDebited debited -> debited.counted();
                    default -> null;
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Test
    void moneyEarnedOrSpentIsCounted() {
        economy.credit(ALEX, TEN);
        economy.debit(ALEX, TEN);

        assertThat(counted()).containsExactly(true, true);
    }

    @Test
    void moneyMovedInsideAnUncountedBlockIsNot() {
        new UncountedEconomyProvider(economy).credit(ALEX, TEN);
        UncountedMoney.call(() -> economy.debit(ALEX, TEN));
        economy.credit(ALEX, TEN);

        assertThat(counted()).containsExactly(false, false, true);
        assertThat(UncountedMoney.counted()).isTrue();
    }

    @Test
    void aTransferBetweenPlayersIsNeitherEarnedNorSpent() {
        economy.credit(ALEX, TEN);

        economy.transfer(ALEX, SAM, TEN);

        assertThat(counted()).containsExactly(true, false, false);
    }
}
