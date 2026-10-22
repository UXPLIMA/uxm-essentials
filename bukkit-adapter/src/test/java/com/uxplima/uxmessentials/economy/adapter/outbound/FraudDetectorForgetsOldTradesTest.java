package com.uxplima.uxmessentials.economy.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import com.uxplima.uxmessentials.economy.application.port.EconomyAudit;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.EconomyReason;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/**
 * The velocity check keeps each player's recent trades for one period, and the alert quiet time for one minute. It
 * kept both for every player who ever traded, for as long as the server ran: a player's old trades were dropped only
 * when that same player traded again.
 */
final class FraudDetectorForgetsOldTradesTest {

    private static final Currency COINS =
            Currency.builder(CurrencyId.of("coins")).build();

    private final AtomicLong now = new AtomicLong(1_000_000L);
    private final FraudDetector detector = new FraudDetector(
            mock(EconomyAudit.class),
            mock(Logger.class),
            true,
            "",
            new BigDecimal("1000000"),
            60,
            new BigDecimal("10"),
            now::get);

    @Test
    void tradesOlderThanThePeriodAreNotKept() {
        for (int i = 0; i < 200; i++) {
            credit(new PlayerRef(UUID.randomUUID(), "trader" + i), 20);
        }
        now.addAndGet(120_000L);

        credit(new PlayerRef(UUID.randomUUID(), "late"), 1);

        assertThat(detector.remembered()).isEqualTo(1);
    }

    @Test
    void aTradeInsideThePeriodStillCounts() {
        PlayerRef steady = new PlayerRef(UUID.randomUUID(), "steady");
        credit(steady, 4);
        for (int i = 0; i < 100; i++) {
            credit(new PlayerRef(UUID.randomUUID(), "other" + i), 1);
        }
        now.addAndGet(30_000L);

        credit(steady, 4);

        assertThat(detector.historyOf(steady.uuid())).isEqualTo(2);
    }

    private void credit(PlayerRef who, long amount) {
        detector.credited(who, Money.of(COINS, amount), EconomyReason.PAY);
    }
}
